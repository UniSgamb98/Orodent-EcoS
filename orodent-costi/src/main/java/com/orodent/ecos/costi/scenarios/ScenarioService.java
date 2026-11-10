package com.orodent.ecos.costi.scenarios;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.costi.products.repository.ProductCostRepository;
import com.orodent.ecos.costi.scenarios.Scenarios.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class ScenarioService {
    private final ConnectionProvider db;
    private final Executor workers;

    public ScenarioService(ConnectionProvider db, Executor workers) {
        this.db = db;
        this.workers = workers;
    }

    private <T> CompletableFuture<T> read(ConnectionProvider.ConnectionWork<T> work) {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withTransaction(
                                c -> {
                                    c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                                    c.setReadOnly(true);
                                    return work.execute(c);
                                }),
                workers);
    }

    public CompletableFuture<List<Entry>> list(boolean archived) {
        return read(c -> new ScenarioRepository(c).list(archived));
    }

    public CompletableFuture<List<Entry>> history(UUID id) {
        return read(c -> new ScenarioRepository(c).history(id));
    }

    public CompletableFuture<Draft> load(Entry entry) {
        return read(c -> new ScenarioRepository(c).load(entry.id(), entry.version()));
    }

    public CompletableFuture<Draft> save(Draft draft) {
        return CompletableFuture.supplyAsync(
                () -> db.withTransaction(c -> new ScenarioRepository(c).save(draft)), workers);
    }

    public CompletableFuture<Analysis> analyse(Draft draft) {
        return CompletableFuture.supplyAsync(() -> new ScenarioEngine().analyse(draft), workers);
    }

    public CompletableFuture<Base> capture(LocalDate from, LocalDate through) {
        LocalDate today = LocalDate.now();
        if (from == null
                || through == null
                || through.isBefore(from)
                || !through.isBefore(today)
                || java.time.temporal.ChronoUnit.DAYS.between(from, through) > 1095)
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Periodo storico concluso di massimo 3 anni, fino a ieri."));
        return read(
                c -> {
                    var catalogue = new ProductCostRepository(c).loadForScenarios();
                    var sales = new HashMap<Long, BigDecimal>();
                    var production = new HashMap<Long, BigDecimal>();
                    var warnings = new ArrayList<String>();
                    try (var q =
                            c.prepareStatement(
                                    "SELECT a.articolo_id,SUM(v.quantita) FROM"
                                        + " VENDITE.VENDITE_DETTAGLIO v LEFT JOIN TON.ARTICOLI a ON"
                                        + " a.codice=v.codice_prodotto WHERE v.data_vendita BETWEEN"
                                        + " ? AND ? AND v.tipo_operazione='VENDITA' AND"
                                        + " v.quantita>0 GROUP BY a.articolo_id")) {
                        dates(q, from, through);
                        try (var r = q.executeQuery()) {
                            while (r.next()) {
                                long id = r.getLong(1);
                                if (r.wasNull())
                                    warnings.add(
                                            "Vendite del periodo con codici non collegati ad"
                                                    + " articoli TON: i relativi volumi non sono"
                                                    + " ponderabili.");
                                else sales.put(id, r.getBigDecimal(2));
                            }
                        }
                    }
                    try (var q =
                            c.prepareStatement(
                                    "SELECT cfg.articolo_id,SUM(r.quantita_prodotta) FROM"
                                        + " TON.PRODUZIONI p JOIN TON.PRODUZIONE_RIGHE r ON"
                                        + " r.produzione_id=p.produzione_id JOIN"
                                        + " TON.CONFIGURAZIONI_ARTICOLO cfg ON"
                                        + " cfg.configurazione_id=r.configurazione_id WHERE"
                                        + " p.stato='CONFERMATA' AND p.data_produzione BETWEEN ?"
                                        + " AND ? AND r.quantita_prodotta>0 GROUP BY"
                                        + " cfg.articolo_id")) {
                        dates(q, from, through);
                        try (var r = q.executeQuery()) {
                            while (r.next()) production.put(r.getLong(1), r.getBigDecimal(2));
                        }
                    }
                    var recipeProducts = new HashMap<Long, Long>();
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT composizione_id,prodotto_id FROM"
                                                    + " TON.COMPOSIZIONI WHERE stato<>'BOZZA'")) {
                        while (r.next()) recipeProducts.put(r.getLong(1), r.getLong(2));
                    }
                    var powders = new ArrayList<Powder>();
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT"
                                                + " p.polvere_id,p.codice,p.nome,p.colore,r.numero_versione"
                                                + " FROM TON.POLVERI p JOIN TON.POLVERI_REVISIONI r"
                                                + " ON r.polvere_id=p.polvere_id AND"
                                                + " r.stato='APPROVATA' AND"
                                                + " r.numero_versione=(SELECT"
                                                + " MAX(v.numero_versione) FROM"
                                                + " TON.POLVERI_REVISIONI v WHERE"
                                                + " v.polvere_id=p.polvere_id AND"
                                                + " v.stato='APPROVATA') ORDER BY"
                                                + " p.colore,p.codice")) {
                        while (r.next())
                            powders.add(
                                    new Powder(
                                            r.getLong(1),
                                            r.getString(2),
                                            r.getString(3),
                                            r.getString(4),
                                            r.getInt(5)));
                    }
                    var lots = new ArrayList<Lot>();
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT"
                                                + " p.polvere_id,p.numero_versione,l.lotto,l.quantita_kg,l.prezzo_kg"
                                                + " FROM COSTI.POLVERI_PREZZI p JOIN"
                                                + " COSTI.POLVERI_LOTTI l ON"
                                                + " l.prezzo_id=p.prezzo_id WHERE"
                                                + " p.numero_versione=(SELECT"
                                                + " MAX(v.numero_versione) FROM"
                                                + " COSTI.POLVERI_PREZZI v WHERE"
                                                + " v.polvere_id=p.polvere_id) ORDER BY"
                                                + " p.polvere_id,l.posizione")) {
                        while (r.next())
                            lots.add(
                                    new Lot(
                                            r.getLong(1),
                                            r.getInt(2),
                                            r.getString(3),
                                            r.getBigDecimal(4),
                                            r.getBigDecimal(5)));
                    }
                    return new Base(
                            UUID.randomUUID(),
                            catalogue,
                            from,
                            through,
                            sales,
                            production,
                            recipeProducts,
                            powders,
                            lots,
                            warnings);
                });
    }

    private static void dates(PreparedStatement q, LocalDate from, LocalDate through)
            throws SQLException {
        q.setDate(1, java.sql.Date.valueOf(from));
        q.setDate(2, java.sql.Date.valueOf(through));
    }
}

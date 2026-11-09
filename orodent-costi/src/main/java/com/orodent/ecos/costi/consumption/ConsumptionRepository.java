package com.orodent.ecos.costi.consumption;

import com.orodent.ecos.costi.consumption.Consumption.*;
import com.orodent.ecos.costi.model.PowderPrices;
import com.orodent.ecos.costi.products.repository.ProductCostRepository;
import com.orodent.ecos.costi.repository.PowderPriceRepository;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class ConsumptionRepository {
    private final Connection connection;

    public ConsumptionRepository(Connection connection) {
        this.connection = connection;
    }

    public Snapshot load(LocalDate from, LocalDate through) throws SQLException {
        var catalogue = new ProductCostRepository(connection).load(true);
        var settings = new HashMap<Long, Settings>();
        var powders = new ArrayList<Powder>();
        var repository = new PowderPriceRepository(connection);
        for (int offset = 0; ; offset += PowderPrices.PAGE_SIZE) {
            var page =
                    repository.page(
                            "", PowderPrices.Filter.TUTTE, true, offset, PowderPrices.PAGE_SIZE);
            for (var row : page.rows())
                powders.add(
                        new Powder(
                                row.powder().id(),
                                row.powder().code(),
                                row.powder().name(),
                                row.powder().color(),
                                row.quantity(),
                                row.referenceDate(),
                                settings.get(row.powder().id())));
            if (!page.hasNext()) break;
        }
        try (var q = connection.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT s.polvere_id,s.numero_versione,s.unita,s.valore FROM"
                                        + " COSTI.SCORTA_MINIMA s WHERE s.numero_versione=(SELECT"
                                        + " MAX(v.numero_versione) FROM COSTI.SCORTA_MINIMA v WHERE"
                                        + " v.polvere_id=s.polvere_id)")) {
            while (r.next())
                settings.put(
                        r.getLong(1),
                        new Settings(
                                r.getInt(2), Unit.valueOf(r.getString(3)), r.getBigDecimal(4)));
        }
        powders.replaceAll(
                p ->
                        new Powder(
                                p.id(),
                                p.code(),
                                p.name(),
                                p.color(),
                                p.stock(),
                                p.stockDate(),
                                settings.get(p.id())));
        var movements = new ArrayList<Movement>();
        var warnings = new ArrayList<String>();
        try (var q =
                connection.prepareStatement(
                        "SELECT"
                            + " p.data_produzione,c.articolo_id,r.configurazione_id,SUM(r.quantita_prodotta)"
                            + " FROM TON.PRODUZIONI p JOIN TON.PRODUZIONE_RIGHE r ON"
                            + " r.produzione_id=p.produzione_id JOIN TON.CONFIGURAZIONI_ARTICOLO c"
                            + " ON c.configurazione_id=r.configurazione_id WHERE"
                            + " p.stato='CONFERMATA' AND p.data_produzione BETWEEN ? AND ? GROUP BY"
                            + " p.data_produzione,c.articolo_id,r.configurazione_id")) {
            dates(q, from, through);
            try (var r = q.executeQuery()) {
                while (r.next())
                    movements.add(
                            new Movement(
                                    r.getDate(1).toLocalDate(),
                                    Basis.PRODUZIONI,
                                    r.getLong(2),
                                    r.getLong(3),
                                    r.getBigDecimal(4)));
            }
        }
        try (var q =
                connection.prepareStatement(
                        "SELECT v.data_vendita,a.articolo_id,v.codice_prodotto,SUM(v.quantita) FROM"
                            + " VENDITE.VENDITE_DETTAGLIO v LEFT JOIN TON.ARTICOLI a ON"
                            + " a.codice=v.codice_prodotto WHERE v.tipo_operazione='VENDITA' AND"
                            + " v.data_vendita BETWEEN ? AND ? GROUP BY"
                            + " v.data_vendita,a.articolo_id,v.codice_prodotto")) {
            dates(q, from, through);
            try (var r = q.executeQuery()) {
                while (r.next()) {
                    Long article = (Long) r.getObject(2);
                    BigDecimal qty = r.getBigDecimal(4);
                    if (article == null)
                        warnings.add("Codice vendita senza articolo TON: " + r.getString(3));
                    movements.add(
                            new Movement(
                                    r.getDate(1).toLocalDate(),
                                    Basis.VENDITE,
                                    article == null ? -1 : article,
                                    null,
                                    qty));
                }
            }
        }
        try (var q =
                connection.prepareStatement(
                        "SELECT COUNT(*) FROM VENDITE.VENDITE_DETTAGLIO WHERE"
                                + " tipo_operazione<>'VENDITA' AND data_vendita BETWEEN ? AND ?")) {
            dates(q, from, through);
            try (var r = q.executeQuery()) {
                r.next();
                if (r.getLong(1) > 0)
                    warnings.add(
                            "Resi e note di credito esclusi: non restituiscono automaticamente"
                                    + " polvere utilizzabile. La stima usa le vendite lorde.");
            }
        }
        warnings.add(
                "Produzioni: consumo teorico dalle configurazioni confermate, non pesature"
                        + " effettivamente registrate. Vendite: ricostruzione con configurazione"
                        + " predefinita attuale.");
        var approved = new HashSet<Long>();
        try (var q = connection.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT configurazione_id FROM TON.CONFIGURAZIONI_ARTICOLO WHERE"
                                        + " stato='APPROVATA'")) {
            while (r.next()) approved.add(r.getLong(1));
        }
        return new Snapshot(catalogue, powders, movements, warnings, approved);
    }

    public Settings save(long powder, Settings expected) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "UPDATE TON.POLVERI SET versione_modifica=versione_modifica WHERE"
                                + " polvere_id=?")) {
            q.setLong(1, powder);
            if (q.executeUpdate() != 1)
                throw new IllegalArgumentException("Polvere non disponibile.");
        }
        int version;
        try (var q =
                connection.prepareStatement(
                        "SELECT COALESCE(MAX(numero_versione),0) FROM COSTI.SCORTA_MINIMA WHERE"
                                + " polvere_id=?")) {
            q.setLong(1, powder);
            try (var r = q.executeQuery()) {
                r.next();
                version = r.getInt(1);
            }
        }
        if (version != expected.version())
            throw new IllegalStateException(
                    "Scorta minima modificata da un altro operatore. Ricarica prima di salvare.");
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO COSTI.SCORTA_MINIMA(polvere_id,numero_versione,unita,valore)"
                                + " VALUES (?,?,?,?)")) {
            q.setLong(1, powder);
            q.setInt(2, version + 1);
            q.setString(3, expected.unit().name());
            q.setBigDecimal(4, expected.value());
            q.executeUpdate();
        }
        return new Settings(version + 1, expected.unit(), expected.value());
    }

    private static void dates(PreparedStatement q, LocalDate from, LocalDate through)
            throws SQLException {
        q.setDate(1, java.sql.Date.valueOf(from));
        q.setDate(2, java.sql.Date.valueOf(through));
    }
}

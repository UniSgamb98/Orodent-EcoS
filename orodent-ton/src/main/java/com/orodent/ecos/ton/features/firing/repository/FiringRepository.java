package com.orodent.ecos.ton.features.firing.repository;

import static com.orodent.ecos.ton.features.firing.model.Firing.*;

import java.sql.*;
import java.util.*;

/** Reads physical output only. Assignments and their furnace snapshots are written atomically. */
public final class FiringRepository {
    private final Connection c;
    private static final String DISC_SELECT =
            "SELECT d.*,e.data_produzione FROM TON.DISCHI_PRODOTTI d JOIN TON.ESECUZIONI_PIANO e ON"
                + " e.esecuzione_id=d.esecuzione_id";

    public FiringRepository(Connection c) {
        this.c = c;
    }

    public Workspace workspace() throws SQLException {
        var furnaces = new ArrayList<Furnace>();
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT forno_id,versione_modifica,codice,nome FROM TON.FORNI WHERE"
                                    + " attivo=TRUE ORDER BY codice")) {
            while (r.next())
                furnaces.add(
                        new Furnace(r.getLong(1), r.getInt(2), r.getString(3), r.getString(4)));
        }
        var loads = new ArrayList<Load>();
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT c.*,l.codice AS lotto,(SELECT COUNT(*) FROM"
                                    + " TON.CARICO_DISCHI a WHERE a.ciclo_id=c.ciclo_id) AS"
                                    + " numero_dischi FROM TON.CICLI_COTTURA c JOIN"
                                    + " TON.LOTTI_COTTURA l ON l.ciclo_id=c.ciclo_id WHERE"
                                    + " c.caricato_il IS NOT NULL ORDER BY c.data_prevista"
                                    + " DESC,c.ciclo_id DESC")) {
            while (r.next()) loads.add(load(r));
        }
        try (var q =
                c.prepareStatement(
                        DISC_SELECT
                                + " WHERE NOT EXISTS (SELECT 1 FROM TON.CARICO_DISCHI a WHERE"
                                + " a.disco_id=d.disco_id) ORDER BY"
                                + " e.data_produzione,d.piano_id,d.numero_riga,d.progressivo")) {
            return new Workspace(discs(q), furnaces, loads);
        }
    }

    public List<Disc> loaded(long cycleId) throws SQLException {
        try (var q =
                c.prepareStatement(
                        DISC_SELECT
                                + " JOIN TON.CARICO_DISCHI a ON a.disco_id=d.disco_id WHERE"
                                + " a.ciclo_id=? ORDER BY"
                                + " d.piano_id,d.numero_riga,d.progressivo")) {
            q.setLong(1, cycleId);
            return discs(q);
        }
    }

    public Load create(Request request) throws SQLException {
        Furnace actual;
        try (var q =
                c.prepareStatement(
                        "UPDATE TON.FORNI SET versione_modifica=versione_modifica WHERE forno_id=?"
                            + " AND versione_modifica=? AND attivo=TRUE")) {
            q.setLong(1, request.furnace().id());
            q.setInt(2, request.furnace().revision());
            if (q.executeUpdate() != 1)
                throw new IllegalArgumentException(
                        "Il forno è stato modificato o archiviato. Aggiorna l’elenco.");
        }
        try (var q =
                c.prepareStatement(
                        "SELECT codice,nome,versione_modifica FROM TON.FORNI WHERE forno_id=?")) {
            q.setLong(1, request.furnace().id());
            try (var r = q.executeQuery()) {
                r.next();
                actual =
                        new Furnace(
                                request.furnace().id(),
                                r.getInt(3),
                                r.getString(1),
                                r.getString(2));
            }
        }
        var ids = request.discs().stream().sorted().toList();
        for (long id : ids) {
            try (var q =
                    c.prepareStatement(
                            "UPDATE TON.DISCHI_PRODOTTI SET progressivo=progressivo WHERE"
                                + " disco_id=?")) {
                q.setLong(1, id);
                if (q.executeUpdate() != 1)
                    throw new IllegalArgumentException(
                            "Un disco selezionato non esiste più. Aggiorna l’elenco.");
            }
            try (var q =
                    c.prepareStatement("SELECT ciclo_id FROM TON.CARICO_DISCHI WHERE disco_id=?")) {
                q.setLong(1, id);
                try (var r = q.executeQuery()) {
                    if (r.next())
                        throw new IllegalArgumentException(
                                "Un disco selezionato è già stato caricato da un’altra postazione."
                                    + " Aggiorna l’elenco.");
                }
            }
            try (var q =
                    c.prepareStatement(
                            "SELECT e.data_produzione FROM TON.DISCHI_PRODOTTI d JOIN"
                                + " TON.ESECUZIONI_PIANO e ON e.esecuzione_id=d.esecuzione_id WHERE"
                                + " d.disco_id=?")) {
                q.setLong(1, id);
                try (var r = q.executeQuery()) {
                    r.next();
                    if (request.date().isBefore(r.getDate(1).toLocalDate()))
                        throw new IllegalArgumentException(
                                "La data del carico precede la produzione di un disco"
                                    + " selezionato.");
                }
            }
        }
        long cycle;
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.CICLI_COTTURA(numero,forno_id,data_prevista,note,caricato_il,forno_codice,forno_nome,forno_revisione)"
                            + " VALUES (?,?,?,?,CURRENT_TIMESTAMP,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setString(1, "CAR-" + UUID.randomUUID().toString().replace("-", ""));
            q.setLong(2, actual.id());
            q.setDate(3, java.sql.Date.valueOf(request.date()));
            q.setString(4, request.notes());
            q.setString(5, actual.code());
            q.setString(6, actual.name());
            q.setInt(7, actual.revision());
            q.executeUpdate();
            try (var keys = q.getGeneratedKeys()) {
                keys.next();
                cycle = keys.getLong(1);
            }
        }
        long lot;
        try (var q =
                c.prepareStatement(
                        "INSERT INTO TON.LOTTI_COTTURA(codice,ciclo_id) VALUES (?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setString(1, request.lot());
            q.setLong(2, cycle);
            try {
                q.executeUpdate();
            } catch (SQLException duplicate) {
                if ("23505".equals(duplicate.getSQLState()))
                    throw new IllegalArgumentException(
                            "Questo lotto è già assegnato a un carico. Scegli un codice lotto"
                                + " diverso.");
                throw duplicate;
            }
            try (var keys = q.getGeneratedKeys()) {
                keys.next();
                lot = keys.getLong(1);
            }
        }
        try (var q =
                c.prepareStatement(
                        "INSERT INTO TON.CARICO_DISCHI(disco_id,ciclo_id,lotto_id) VALUES"
                            + " (?,?,?)")) {
            for (long id : ids) {
                q.setLong(1, id);
                q.setLong(2, cycle);
                q.setLong(3, lot);
                q.addBatch();
            }
            q.executeBatch();
        }
        try (var q =
                c.prepareStatement(
                        "SELECT c.*,l.codice AS lotto,(SELECT COUNT(*) FROM TON.CARICO_DISCHI a"
                            + " WHERE a.ciclo_id=c.ciclo_id) AS numero_dischi FROM"
                            + " TON.CICLI_COTTURA c JOIN TON.LOTTI_COTTURA l ON"
                            + " l.ciclo_id=c.ciclo_id WHERE c.ciclo_id=?")) {
            q.setLong(1, cycle);
            try (var r = q.executeQuery()) {
                r.next();
                return load(r);
            }
        }
    }

    private List<Disc> discs(PreparedStatement q) throws SQLException {
        var discs = new ArrayList<Disc>();
        q.setFetchSize(256);
        try (var r = q.executeQuery()) {
            while (r.next())
                discs.add(
                        new Disc(
                                r.getLong("disco_id"),
                                r.getLong("esecuzione_id"),
                                r.getLong("piano_id"),
                                r.getInt("numero_riga"),
                                r.getInt("progressivo"),
                                r.getString("articolo_codice"),
                                r.getString("prodotto_codice"),
                                r.getString("linea_codice"),
                                r.getString("colore"),
                                r.getBigDecimal("diametro_mm"),
                                r.getBigDecimal("altezza_mm"),
                                r.getDate("data_produzione").toLocalDate()));
        }
        return List.copyOf(discs);
    }

    private Load load(ResultSet r) throws SQLException {
        return new Load(
                r.getLong("ciclo_id"),
                r.getLong("forno_id"),
                r.getString("forno_codice"),
                r.getString("forno_nome"),
                r.getInt("forno_revisione"),
                r.getString("lotto"),
                r.getDate("data_prevista").toLocalDate(),
                r.getTimestamp("caricato_il").toLocalDateTime(),
                Objects.requireNonNullElse(r.getString("note"), ""),
                r.getInt("numero_dischi"));
    }
}

package com.orodent.ecos.ton.features.diskmodels.repository;

import static com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Caller-owned connection; aggregate writes lock the oldest version of the code first. */
public final class DiskModelRepository {
    private final Connection connection;

    public DiskModelRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    public Page<Summary> page(String search, Filter filter, int offset, int limit)
            throws SQLException {
        String pattern =
                "%"
                        + search.toUpperCase(Locale.ROOT)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                        + "%";
        String where =
                " WHERE m.numero_versione=(SELECT MAX(x.numero_versione) FROM TON.MODELLI_DISCO x"
                        + " WHERE x.codice=m.codice) AND m.codice LIKE ? ESCAPE '\\'";
        if (filter != Filter.ALL)
            where +=
                    " AND EXISTS (SELECT 1 FROM TON.MODELLI_DISCO x WHERE x.codice=m.codice AND"
                            + " x.stato='"
                            + (filter == Filter.DRAFT ? "BOZZA" : "APPROVATA")
                            + "')";
        long total;
        try (var q =
                connection.prepareStatement("SELECT COUNT(*) FROM TON.MODELLI_DISCO m" + where)) {
            q.setString(1, pattern);
            try (var r = q.executeQuery()) {
                r.next();
                total = r.getLong(1);
            }
        }
        var result = new ArrayList<Summary>();
        try (var q =
                connection.prepareStatement(
                        "SELECT m.modello_id,m.codice,m.diametro_pressatura_mm,(SELECT COUNT(*)"
                            + " FROM TON.MODELLO_STRATI s WHERE s.modello_id=m.modello_id),(SELECT"
                            + " MAX(x.numero_versione) FROM TON.MODELLI_DISCO x WHERE"
                            + " x.codice=m.codice AND x.stato='APPROVATA'),(SELECT"
                            + " MAX(x.numero_versione) FROM TON.MODELLI_DISCO x WHERE"
                            + " x.codice=m.codice AND x.stato='BOZZA') FROM TON.MODELLI_DISCO m"
                                + where
                                + " ORDER BY m.codice OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            q.setString(1, pattern);
            q.setInt(2, offset);
            q.setInt(3, limit);
            try (var r = q.executeQuery()) {
                while (r.next())
                    result.add(
                            new Summary(
                                    r.getLong(1),
                                    r.getString(2),
                                    r.getBigDecimal(3),
                                    r.getInt(4),
                                    (Integer) r.getObject(5),
                                    (Integer) r.getObject(6)));
            }
        }
        return new Page<>(result, total, offset, limit);
    }

    public Revision load(long id) throws SQLException {
        try (var q =
                connection.prepareStatement("SELECT * FROM TON.MODELLI_DISCO WHERE modello_id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next())
                    throw invalid("Il modello non è più disponibile. Aggiorna l'elenco.");
                var d =
                        new Draft(
                                r.getString("codice"),
                                r.getBigDecimal("diametro_pressatura_mm"),
                                r.getBigDecimal("pressione_kg_cm2"),
                                r.getBigDecimal("grammi_per_mm"),
                                r.getBigDecimal("sovramateriale_superiore_mm"),
                                r.getBigDecimal("sovramateriale_inferiore_mm"),
                                r.getString("note"),
                                layers(id),
                                ranges(id));
                return new Revision(
                        id,
                        r.getInt("numero_versione"),
                        State.valueOf(r.getString("stato")),
                        r.getInt("versione_modifica"),
                        r.getTimestamp("creato_il").toLocalDateTime(),
                        r.getTimestamp("aggiornato_il").toLocalDateTime(),
                        r.getTimestamp("approvato_il") == null
                                ? null
                                : r.getTimestamp("approvato_il").toLocalDateTime(),
                        d);
            }
        }
    }

    private List<BigDecimal> layers(long id) throws SQLException {
        var layers = new ArrayList<BigDecimal>();
        try (var q =
                connection.prepareStatement(
                        "SELECT percentuale_altezza FROM TON.MODELLO_STRATI WHERE modello_id=?"
                                + " ORDER BY numero_strato")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                while (r.next()) layers.add(r.getBigDecimal(1));
            }
        }
        return layers;
    }

    private List<Range> ranges(long id) throws SQLException {
        var ranges = new ArrayList<Range>();
        try (var q =
                connection.prepareStatement(
                        "SELECT altezza_da_mm,altezza_a_mm,superiore_mm,inferiore_mm FROM"
                                + " TON.MODELLO_SOVRAMATERIALI WHERE modello_id=? ORDER BY"
                                + " altezza_da_mm,altezza_a_mm")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                while (r.next())
                    ranges.add(
                            new Range(
                                    r.getBigDecimal(1),
                                    r.getBigDecimal(2),
                                    r.getBigDecimal(3),
                                    r.getBigDecimal(4)));
            }
        }
        return ranges;
    }

    public Page<Version> versions(String code, int offset, int limit) throws SQLException {
        long total = count("SELECT COUNT(*) FROM TON.MODELLI_DISCO WHERE codice=?", code);
        var rows = new ArrayList<Version>();
        try (var q =
                connection.prepareStatement(
                        "SELECT modello_id,numero_versione,stato,aggiornato_il FROM"
                                + " TON.MODELLI_DISCO WHERE codice=? ORDER BY numero_versione DESC"
                                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            q.setString(1, code);
            q.setInt(2, offset);
            q.setInt(3, limit);
            try (var r = q.executeQuery()) {
                while (r.next())
                    rows.add(
                            new Version(
                                    r.getLong(1),
                                    r.getInt(2),
                                    State.valueOf(r.getString(3)),
                                    r.getTimestamp(4).toLocalDateTime()));
            }
        }
        return new Page<>(rows, total, offset, limit);
    }

    public Page<Usage> usages(long id, int offset, int limit) throws SQLException {
        long total =
                count("SELECT COUNT(*) FROM TON.CONFIGURAZIONI_ARTICOLO WHERE modello_id=?", id);
        var rows = new ArrayList<Usage>();
        try (var q =
                connection.prepareStatement(
                        "SELECT"
                            + " a.articolo_id,a.codice,p.codice,a.diametro_mm,a.altezza_mm,c.numero_versione,c.stato,CASE"
                            + " WHEN a.configurazione_predefinita_id=c.configurazione_id THEN 1"
                            + " ELSE 0 END FROM TON.CONFIGURAZIONI_ARTICOLO c JOIN TON.ARTICOLI a"
                            + " ON a.articolo_id=c.articolo_id JOIN TON.PRODOTTI p ON"
                            + " p.prodotto_id=a.prodotto_id WHERE c.modello_id=? ORDER BY"
                            + " a.codice,c.numero_versione DESC OFFSET ? ROWS FETCH NEXT ? ROWS"
                            + " ONLY")) {
            q.setLong(1, id);
            q.setInt(2, offset);
            q.setInt(3, limit);
            try (var r = q.executeQuery()) {
                while (r.next())
                    rows.add(
                            new Usage(
                                    r.getLong(1),
                                    r.getString(2),
                                    r.getString(3),
                                    r.getBigDecimal(4),
                                    r.getBigDecimal(5),
                                    r.getInt(6),
                                    r.getString(7),
                                    r.getInt(8) == 1));
            }
        }
        return new Page<>(rows, total, offset, limit);
    }

    /** Existing families use a stable approved/oldest row. Initial creation is guarded by UQ. */
    public boolean lockCode(String code) throws SQLException {
        long first = firstId(code);
        if (first == 0) return false;
        lock(first);
        return true;
    }

    public String code(long id) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "SELECT codice FROM TON.MODELLI_DISCO WHERE modello_id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new Conflict();
                return r.getString(1);
            }
        }
    }

    public long firstId(String code) throws SQLException {
        long first;
        try (var q =
                connection.prepareStatement(
                        "SELECT MIN(modello_id) FROM TON.MODELLI_DISCO WHERE codice=?")) {
            q.setString(1, code);
            try (var r = q.executeQuery()) {
                r.next();
                first = r.getLong(1);
                if (r.wasNull()) return 0;
            }
        }
        return first;
    }

    /**
     * Under REPEATABLE_READ this shared parent lock follows the same family-first order as writes.
     */
    public void readLock(long id) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "SELECT modello_id FROM TON.MODELLI_DISCO WHERE modello_id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new Conflict();
            }
        }
    }

    public void lock(long id) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "UPDATE TON.MODELLI_DISCO SET versione_modifica=versione_modifica WHERE"
                                + " modello_id=?")) {
            q.setLong(1, id);
            if (q.executeUpdate() != 1) throw new Conflict();
        }
    }

    public int maximum(String code) throws SQLException {
        return (int)
                count(
                        "SELECT COALESCE(MAX(numero_versione),0) FROM TON.MODELLI_DISCO WHERE"
                                + " codice=?",
                        code);
    }

    public boolean hasDraft(String code) throws SQLException {
        return count(
                        "SELECT COUNT(*) FROM TON.MODELLI_DISCO WHERE codice=? AND stato='BOZZA'",
                        code)
                > 0;
    }

    public boolean used(long id) throws SQLException {
        return count("SELECT COUNT(*) FROM TON.CONFIGURAZIONI_ARTICOLO WHERE modello_id=?", id) > 0;
    }

    public long insert(Draft d, int number, boolean approve) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " TON.MODELLI_DISCO(codice,numero_versione,diametro_pressatura_mm,pressione_kg_cm2,grammi_per_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm,note,stato,approvato_il)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setString(1, d.code());
            q.setInt(2, number);
            bindData(q, d, 3);
            q.setString(9, approve ? "APPROVATA" : "BOZZA");
            q.setTimestamp(10, approve ? new Timestamp(System.currentTimeMillis()) : null);
            q.executeUpdate();
            try (var r = q.getGeneratedKeys()) {
                r.next();
                long id = r.getLong(1);
                insertChildren(id, d);
                return id;
            }
        }
    }

    public void update(Revision old, Draft d, boolean approve) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "UPDATE TON.MODELLI_DISCO SET"
                            + " diametro_pressatura_mm=?,pressione_kg_cm2=?,grammi_per_mm=?,sovramateriale_superiore_mm=?,sovramateriale_inferiore_mm=?,note=?,stato=?,approvato_il=?,versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                            + " WHERE modello_id=? AND versione_modifica=? AND stato='BOZZA'")) {
            bindData(q, d, 1);
            q.setString(7, approve ? "APPROVATA" : "BOZZA");
            q.setTimestamp(8, approve ? new Timestamp(System.currentTimeMillis()) : null);
            q.setLong(9, old.id());
            q.setInt(10, old.editVersion());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
        deleteChildren(old.id());
        insertChildren(old.id(), d);
    }

    public void retire(Revision old) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "UPDATE TON.MODELLI_DISCO SET"
                            + " stato='RITIRATA',versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                            + " WHERE modello_id=? AND versione_modifica=? AND"
                            + " stato='APPROVATA'")) {
            q.setLong(1, old.id());
            q.setInt(2, old.editVersion());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
    }

    public void delete(Revision old) throws SQLException {
        deleteChildren(old.id());
        try (var q =
                connection.prepareStatement(
                        "DELETE FROM TON.MODELLI_DISCO WHERE modello_id=? AND versione_modifica=?"
                                + " AND stato='BOZZA'")) {
            q.setLong(1, old.id());
            q.setInt(2, old.editVersion());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
    }

    private void deleteChildren(long id) throws SQLException {
        for (String table : List.of("MODELLO_STRATI", "MODELLO_SOVRAMATERIALI"))
            try (var q =
                    connection.prepareStatement(
                            "DELETE FROM TON." + table + " WHERE modello_id=?")) {
                q.setLong(1, id);
                q.executeUpdate();
            }
    }

    private void insertChildren(long id, Draft d) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " TON.MODELLO_STRATI(modello_id,numero_strato,percentuale_altezza)"
                            + " VALUES (?,?,?)")) {
            for (int i = 0; i < d.layers().size(); i++) {
                q.setLong(1, id);
                q.setInt(2, i + 1);
                q.setBigDecimal(3, d.layers().get(i));
                q.addBatch();
            }
            q.executeBatch();
        }
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " TON.MODELLO_SOVRAMATERIALI(modello_id,altezza_da_mm,altezza_a_mm,superiore_mm,inferiore_mm)"
                            + " VALUES (?,?,?,?,?)")) {
            for (Range r : d.ranges()) {
                q.setLong(1, id);
                q.setBigDecimal(2, r.from());
                q.setBigDecimal(3, r.to());
                q.setBigDecimal(4, r.top());
                q.setBigDecimal(5, r.bottom());
                q.addBatch();
            }
            q.executeBatch();
        }
    }

    private static void bindData(PreparedStatement q, Draft d, int start) throws SQLException {
        q.setBigDecimal(start, d.diameter());
        q.setBigDecimal(start + 1, d.pressure());
        q.setBigDecimal(start + 2, d.grams());
        q.setBigDecimal(start + 3, d.top());
        q.setBigDecimal(start + 4, d.bottom());
        q.setString(start + 5, d.notes());
    }

    private long count(String sql, Object value) throws SQLException {
        try (var q = connection.prepareStatement(sql)) {
            q.setObject(1, value);
            try (var r = q.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }
}

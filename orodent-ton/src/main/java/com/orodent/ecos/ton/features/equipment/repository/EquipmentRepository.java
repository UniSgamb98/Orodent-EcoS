package com.orodent.ecos.ton.features.equipment.repository;

import static com.orodent.ecos.ton.features.equipment.model.Equipment.*;

import java.sql.*;
import java.util.*;

/** Identifiers come only from Kind; user text is always passed as a SQL parameter. */
public final class EquipmentRepository {
    private final Connection c;

    public EquipmentRepository(Connection c) {
        this.c = c;
    }

    private String columns(Kind kind) {
        return kind.key()
                + ",versione_modifica,codice,nome,"
                + (kind.measure() == null ? "CAST(NULL AS DECIMAL(10,3))" : kind.measure())
                + ",note,attivo";
    }

    public List<Entry> list(Kind kind, String query, boolean archived) throws SQLException {
        var rows = new ArrayList<Entry>();
        String filter = Objects.toString(query, "").strip().toUpperCase(Locale.ROOT);
        if (filter.length() > 200) filter = filter.substring(0, 200);
        String pattern =
                "%" + filter.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        try (var q =
                c.prepareStatement(
                        "SELECT "
                                + columns(kind)
                                + " FROM TON."
                                + kind.table()
                                + " WHERE (?=TRUE OR attivo=TRUE) AND (UPPER(codice) LIKE ? ESCAPE"
                                + " '!' OR UPPER(nome) LIKE ? ESCAPE '!') ORDER BY codice")) {
            q.setBoolean(1, archived);
            q.setString(2, pattern);
            q.setString(3, pattern);
            try (var r = q.executeQuery()) {
                while (r.next()) rows.add(entry(r));
            }
        }
        return List.copyOf(rows);
    }

    public Entry load(Kind kind, long id) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "SELECT "
                                + columns(kind)
                                + " FROM TON."
                                + kind.table()
                                + " WHERE "
                                + kind.key()
                                + "=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next())
                    throw new IllegalArgumentException(
                            "L’anagrafica non esiste più. Aggiorna l’elenco.");
                return entry(r);
            }
        }
    }

    private Entry entry(ResultSet r) throws SQLException {
        var dimension = r.getBigDecimal(5);
        return new Entry(
                r.getLong(1),
                r.getInt(2),
                r.getString(3),
                r.getString(4),
                dimension == null ? null : dimension.stripTrailingZeros(),
                r.getString(6),
                r.getBoolean(7));
    }

    public Entry create(Kind kind, Draft draft) throws SQLException {
        String measure = kind.measure() == null ? "" : "," + kind.measure();
        String placeholder = kind.measure() == null ? "" : ",?";
        try (var q =
                c.prepareStatement(
                        "INSERT INTO TON."
                                + kind.table()
                                + "(codice,nome,note,attivo"
                                + measure
                                + ") VALUES (?,?,?,?"
                                + placeholder
                                + ")",
                        Statement.RETURN_GENERATED_KEYS)) {
            parameters(q, draft);
            q.executeUpdate();
            try (var r = q.getGeneratedKeys()) {
                r.next();
                return load(kind, r.getLong(1));
            }
        }
    }

    public Entry save(Kind kind, Entry expected, Draft draft) throws SQLException {
        String measure = kind.measure() == null ? "" : "," + kind.measure() + "=?";
        try (var q =
                c.prepareStatement(
                        "UPDATE TON."
                                + kind.table()
                                + " SET codice=?,nome=?,note=?,attivo=?"
                                + measure
                                + ",versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                                + " WHERE "
                                + kind.key()
                                + "=? AND versione_modifica=?")) {
            int next = parameters(q, draft);
            q.setLong(next, expected.id());
            q.setInt(next + 1, expected.revision());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
        return load(kind, expected.id());
    }

    private int parameters(PreparedStatement q, Draft draft) throws SQLException {
        q.setString(1, draft.code());
        q.setString(2, draft.name());
        q.setString(3, draft.notes());
        q.setBoolean(4, draft.active());
        if (draft.dimension() != null) {
            q.setBigDecimal(5, draft.dimension());
            return 6;
        }
        return 5;
    }
}

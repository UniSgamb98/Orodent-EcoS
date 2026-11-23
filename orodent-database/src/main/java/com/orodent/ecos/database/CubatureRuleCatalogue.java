package com.orodent.ecos.database;

import java.sql.*;
import java.util.*;

/** Shared rule identities. The caller owns the connection and transaction. */
public final class CubatureRuleCatalogue {
    private CubatureRuleCatalogue() {}

    public record Draft(String code, String name, String description, boolean active) {
        public Draft {
            code = text(code);
            name = text(name);
            description = text(description);
            if (code.isEmpty() || code.length() > 50)
                throw new IllegalArgumentException(
                        "Codice regola obbligatorio, massimo 50 caratteri.");
            if (name.isEmpty() || name.length() > 150)
                throw new IllegalArgumentException(
                        "Nome regola obbligatorio, massimo 150 caratteri.");
            if (description.length() > 1000)
                throw new IllegalArgumentException("Descrizione: massimo 1000 caratteri.");
        }
    }

    public record Entry(
            long id,
            String code,
            String name,
            String description,
            boolean active,
            int revision,
            boolean used) {
        @Override
        public String toString() {
            return code + (code.equals(name) ? "" : " · " + name) + (active ? "" : " · Archiviata");
        }
    }

    public static final class Conflict extends IllegalArgumentException {
        public Conflict() {
            super(
                    "La regola è stata modificata da un altro operatore. Ricarica i dati prima di"
                        + " riprovare.");
        }
    }

    private static final String SELECT =
            "SELECT r.*,CASE WHEN EXISTS (SELECT 1 FROM TON.CONFIGURAZIONI_PRODOTTO c WHERE"
                + " c.versione_calcolo=r.codice) THEN TRUE ELSE FALSE END AS usata FROM"
                + " TON.REGOLE_CUBATURA r";

    public static List<Entry> list(Connection c, String filter, boolean archived)
            throws SQLException {
        String pattern =
                "%"
                        + text(filter)
                                .toUpperCase(Locale.ROOT)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                        + "%";
        try (var q =
                c.prepareStatement(
                        SELECT
                                + " WHERE (UPPER(r.codice) LIKE ? ESCAPE '\\' OR UPPER(r.nome) LIKE"
                                + " ? ESCAPE '\\')"
                                + (archived ? "" : " AND r.attivo=TRUE")
                                + " ORDER BY r.codice")) {
            q.setString(1, pattern);
            q.setString(2, pattern);
            try (var r = q.executeQuery()) {
                var result = new ArrayList<Entry>();
                while (r.next()) result.add(entry(r));
                return List.copyOf(result);
            }
        }
    }

    public static Entry load(Connection c, long id) throws SQLException {
        try (var q = c.prepareStatement(SELECT + " WHERE r.regola_id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next())
                    throw new IllegalArgumentException("Regola di cubatura non trovata.");
                return entry(r);
            }
        }
    }

    public static Entry save(Connection c, Entry expected, Draft draft) throws SQLException {
        try {
            long id;
            if (expected == null) {
                try (var q =
                        c.prepareStatement(
                                "INSERT INTO TON.REGOLE_CUBATURA(codice,nome,descrizione,attivo)"
                                    + " VALUES (?,?,?,?)",
                                Statement.RETURN_GENERATED_KEYS)) {
                    fields(q, draft);
                    q.executeUpdate();
                    try (var keys = q.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }
            } else {
                id = expected.id();
                try (var q =
                        c.prepareStatement(
                                "UPDATE TON.REGOLE_CUBATURA SET attivo=attivo WHERE regola_id=?")) {
                    q.setLong(1, id);
                    q.executeUpdate();
                }
                Entry current = load(c, id);
                if (current.revision() != expected.revision()) throw new Conflict();
                if (current.used() && !current.code().equals(draft.code()))
                    throw new IllegalArgumentException(
                            "Il codice della regola è già utilizzato dalle specifiche e non può"
                                + " essere cambiato.");
                try (var q =
                        c.prepareStatement(
                                "UPDATE TON.REGOLE_CUBATURA SET"
                                    + " codice=?,nome=?,descrizione=?,attivo=?,versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                                    + " WHERE regola_id=? AND versione_modifica=?")) {
                    fields(q, draft);
                    q.setLong(5, id);
                    q.setInt(6, expected.revision());
                    if (q.executeUpdate() != 1) throw new Conflict();
                }
            }
            return load(c, id);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState()))
                throw new IllegalArgumentException("Codice regola già utilizzato.", e);
            throw e;
        }
    }

    /** Serializes selection with archive/rename and rejects references outside the catalogue. */
    public static void requireActive(Connection c, String code) throws SQLException {
        try (var q =
                c.prepareStatement("UPDATE TON.REGOLE_CUBATURA SET attivo=attivo WHERE codice=?")) {
            q.setString(1, code);
            if (q.executeUpdate() != 1)
                throw new IllegalArgumentException(
                        "Seleziona una regola di cubatura presente nel catalogo.");
        }
        try (var q = c.prepareStatement("SELECT attivo FROM TON.REGOLE_CUBATURA WHERE codice=?")) {
            q.setString(1, code);
            try (var r = q.executeQuery()) {
                if (!r.next() || !r.getBoolean(1))
                    throw new IllegalArgumentException(
                            "La regola di cubatura è archiviata. Seleziona una regola attiva.");
            }
        }
    }

    private static void fields(PreparedStatement q, Draft d) throws SQLException {
        q.setString(1, d.code());
        q.setString(2, d.name());
        q.setString(3, d.description());
        q.setBoolean(4, d.active());
    }

    private static Entry entry(ResultSet r) throws SQLException {
        return new Entry(
                r.getLong("regola_id"),
                r.getString("codice"),
                r.getString("nome"),
                r.getString("descrizione"),
                r.getBoolean("attivo"),
                r.getInt("versione_modifica"),
                r.getBoolean("usata"));
    }

    private static String text(String value) {
        return Objects.requireNonNullElse(value, "").strip();
    }
}

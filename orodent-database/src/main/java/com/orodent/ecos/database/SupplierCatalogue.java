package com.orodent.ecos.database;

import java.sql.*;
import java.util.*;

/** Shared supplier identities; all operations run in the caller's transaction. */
public final class SupplierCatalogue {
    private SupplierCatalogue() {}

    public record Draft(String code, String name, boolean active) {
        public Draft {
            code = Objects.requireNonNullElse(code, "").strip().toUpperCase(Locale.ROOT);
            name = clean(name);
            if (code.isEmpty() || code.length() > 50)
                throw new IllegalArgumentException(
                        "Codice fornitore obbligatorio, massimo 50 caratteri.");
            if (name.isEmpty() || name.length() > 150)
                throw new IllegalArgumentException(
                        "Nome fornitore obbligatorio, massimo 150 caratteri.");
        }
    }

    public record Entry(long id, String code, String name, boolean active, int revision) {
        @Override
        public String toString() {
            return code + " · " + name + (active ? "" : " · Archiviato");
        }
    }

    public static final class Conflict extends IllegalArgumentException {
        public Conflict() {
            super(
                    "Il fornitore è stato modificato da un altro operatore. Ricarica i dati prima"
                        + " di riprovare.");
        }
    }

    public static String clean(String name) {
        return Objects.requireNonNullElse(name, "").strip().replaceAll("\\s+", " ");
    }

    public static String normalized(String name) {
        return clean(name).toUpperCase(Locale.ROOT);
    }

    /** Trusted SQL alias. Legacy text remains a fallback for old imports without an association. */
    public static String powderName(String alias) {
        return "COALESCE((SELECT sf.nome FROM ANAGRAFICHE.FORNITORI sf WHERE sf.fornitore_id="
                + alias
                + ".fornitore_id),"
                + alias
                + ".fornitore,'')";
    }

    public static List<Entry> list(Connection c, String filter, boolean archived)
            throws SQLException {
        String pattern =
                "%"
                        + normalized(filter)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                        + "%";
        try (var q =
                c.prepareStatement(
                        "SELECT * FROM ANAGRAFICHE.FORNITORI WHERE (UPPER(codice) LIKE ? ESCAPE"
                            + " '\\' OR nome_normalizzato LIKE ? ESCAPE '\\')"
                                + (archived ? "" : " AND attivo=TRUE")
                                + " ORDER BY nome_normalizzato,codice")) {
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
        try (var q =
                c.prepareStatement("SELECT * FROM ANAGRAFICHE.FORNITORI WHERE fornitore_id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Fornitore non trovato.");
                return entry(r);
            }
        }
    }

    public static Entry save(Connection c, Entry original, Draft draft) throws SQLException {
        Long alias = find(c, draft.name());
        if (alias != null && (original == null || alias != original.id()))
            throw new IllegalArgumentException(
                    "Esiste già un fornitore con questo nome (o un suo nome precedente).");
        try {
            long id;
            if (original == null) {
                try (var q =
                        c.prepareStatement(
                                "INSERT INTO"
                                    + " ANAGRAFICHE.FORNITORI(codice,nome,nome_normalizzato,attivo)"
                                    + " VALUES (?,?,?,?)",
                                Statement.RETURN_GENERATED_KEYS)) {
                    q.setString(1, draft.code());
                    q.setString(2, draft.name());
                    q.setString(3, normalized(draft.name()));
                    q.setBoolean(4, draft.active());
                    q.executeUpdate();
                    try (var keys = q.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }
            } else {
                id = original.id();
                try (var q =
                        c.prepareStatement(
                                "UPDATE ANAGRAFICHE.FORNITORI SET"
                                    + " codice=?,nome=?,nome_normalizzato=?,attivo=?,versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                                    + " WHERE fornitore_id=? AND versione_modifica=?")) {
                    q.setString(1, draft.code());
                    q.setString(2, draft.name());
                    q.setString(3, normalized(draft.name()));
                    q.setBoolean(4, draft.active());
                    q.setLong(5, id);
                    q.setInt(6, original.revision());
                    if (q.executeUpdate() != 1) throw new Conflict();
                }
            }
            if (alias == null) {
                try (var q =
                        c.prepareStatement(
                                "INSERT INTO"
                                    + " ANAGRAFICHE.FORNITORI_ALIAS(nome_normalizzato,fornitore_id)"
                                    + " VALUES (?,?)")) {
                    q.setString(1, normalized(draft.name()));
                    q.setLong(2, id);
                    q.executeUpdate();
                }
            }
            return load(c, id);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState()))
                throw new IllegalArgumentException("Codice o nome fornitore già utilizzato.", e);
            throw e;
        }
    }

    private static Long find(Connection c, String name) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "SELECT fornitore_id FROM ANAGRAFICHE.FORNITORI_ALIAS WHERE"
                            + " nome_normalizzato=?")) {
            q.setString(1, normalized(name));
            try (var r = q.executeQuery()) {
                return r.next() ? r.getLong(1) : null;
            }
        }
    }

    /** CSV source names are retained separately; aliases survive subsequent supplier renames. */
    public static Long resolve(Connection c, String name) throws SQLException {
        if (clean(name).isEmpty()) return null;
        Long id = find(c, name);
        if (id != null) return id;
        return save(
                        c,
                        null,
                        new Draft(
                                "AUTO-" + UUID.randomUUID().toString().substring(0, 12),
                                name,
                                true))
                .id();
    }

    /** Purchase CSVs can identify a supplier by its registry code or a current/previous name. */
    public static Long resolveSource(Connection c, String source) throws SQLException {
        // A source already imported keeps its identity even if the registry code is corrected.
        try (var q =
                c.prepareStatement(
                        "SELECT DISTINCT fornitore_id FROM COSTI.ORDINI_POLVERI WHERE fornitore=?"
                            + " AND fornitore_id IS NOT NULL")) {
            q.setString(1, source);
            try (var r = q.executeQuery()) {
                if (r.next()) {
                    long id = r.getLong(1);
                    if (r.next())
                        throw new IllegalArgumentException(
                                "La sorgente CSV è collegata a più fornitori. Verifica gli ordini"
                                    + " esistenti.");
                    return id;
                }
            }
        }
        try (var q =
                c.prepareStatement(
                        "SELECT fornitore_id FROM ANAGRAFICHE.FORNITORI WHERE codice=?")) {
            q.setString(1, normalized(source));
            try (var r = q.executeQuery()) {
                if (r.next()) return r.getLong(1);
            }
        }
        return resolve(c, source);
    }

    public static Long assignment(Connection c, Long id, String legacyName, Long originalId)
            throws SQLException {
        if (id == null) id = resolve(c, legacyName);
        if (id == null) return null;
        // Serialize a new assignment with archive/reactivation, without creating a revision.
        try (var q =
                c.prepareStatement(
                        "UPDATE ANAGRAFICHE.FORNITORI SET attivo=attivo WHERE fornitore_id=?")) {
            q.setLong(1, id);
            q.executeUpdate();
        }
        Entry supplier = load(c, id);
        if (!supplier.active() && !id.equals(originalId))
            throw new IllegalArgumentException(
                    "Il fornitore è archiviato. Scegli un fornitore attivo.");
        return id;
    }

    private static Entry entry(ResultSet r) throws SQLException {
        return new Entry(
                r.getLong("fornitore_id"),
                r.getString("codice"),
                r.getString("nome"),
                r.getBoolean("attivo"),
                r.getInt("versione_modifica"));
    }
}

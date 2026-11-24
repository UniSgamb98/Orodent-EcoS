package com.orodent.ecos.database;

import com.orodent.ecos.core.math.FormulaProgram;
import com.orodent.ecos.database.CubatureRuleCatalogue.*;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** Append-only snapshots: every save retains the preceding draft or approved content. */
public final class CubatureRuleVersions {
    private CubatureRuleVersions() {}

    public record Version(
            long id,
            long ruleId,
            int number,
            int revision,
            String state,
            String code,
            String name,
            String description,
            boolean active,
            String formulas,
            LocalDateTime createdAt,
            boolean current) {
        public boolean editable() {
            return current && state.equals("BOZZA");
        }

        public String label() {
            return "v" + number + " · r" + revision;
        }

        public String stateLabel() {
            return switch (state) {
                case "APPROVATA" -> "Approvata";
                case "STORICA" -> "Importata senza formule";
                default -> "Bozza";
            };
        }

        @Override
        public String toString() {
            return code
                    + " · "
                    + label()
                    + " · "
                    + name
                    + (state.equals("STORICA") ? " · " + stateLabel() : "");
        }
    }

    public record Detail(Entry rule, Version selected, List<Version> versions) {
        public Detail {
            versions = List.copyOf(versions);
        }
    }

    private static final String SELECT =
            "SELECT v.*,CASE WHEN EXISTS (SELECT 1 FROM TON.REGOLA_CUBATURA_VERSIONI n WHERE"
                + " n.regola_id=v.regola_id AND (n.numero_versione>v.numero_versione OR"
                + " (n.numero_versione=v.numero_versione AND"
                + " n.numero_revisione>v.numero_revisione))) THEN FALSE ELSE TRUE END AS corrente"
                + " FROM TON.REGOLA_CUBATURA_VERSIONI v";

    public static List<Version> list(Connection c, long ruleId) throws SQLException {
        try (var q =
                c.prepareStatement(
                        SELECT
                                + " WHERE v.regola_id=? ORDER BY v.numero_versione"
                                + " DESC,v.numero_revisione DESC")) {
            q.setLong(1, ruleId);
            return rows(q);
        }
    }

    public static Version load(Connection c, long id) throws SQLException {
        try (var q = c.prepareStatement(SELECT + " WHERE v.calcolo_versione_id=?")) {
            q.setLong(1, id);
            var values = rows(q);
            if (values.isEmpty())
                throw new IllegalArgumentException("Versione della regola non trovata.");
            return values.getFirst();
        }
    }

    public static Detail detail(Connection c, long ruleId, Long selectedId) throws SQLException {
        Entry rule = CubatureRuleCatalogue.load(c, ruleId);
        var versions = list(c, ruleId);
        Version selected = selectedId == null ? versions.getFirst() : load(c, selectedId);
        if (selected.ruleId() != ruleId)
            throw new IllegalArgumentException("La versione non appartiene alla regola.");
        return new Detail(rule, selected, versions);
    }

    static boolean hasPublished(Connection c, long id) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "SELECT COUNT(*) FROM TON.REGOLA_CUBATURA_VERSIONI WHERE regola_id=? AND"
                            + " stato<>'BOZZA'")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                r.next();
                return r.getLong(1) > 0;
            }
        }
    }

    static void recordMetadata(Connection c, Entry rule) throws SQLException {
        var versions = list(c, rule.id());
        Version previous = versions.isEmpty() ? null : versions.getFirst();
        boolean same = previous != null && previous.editable();
        insert(
                c,
                rule,
                same ? previous.number() : previous == null ? 1 : previous.number() + 1,
                same ? previous.revision() + 1 : 1,
                "BOZZA",
                previous == null ? "" : previous.formulas());
    }

    public static Detail save(
            Connection c,
            Entry expected,
            Version selected,
            Draft draft,
            String source,
            boolean approve)
            throws SQLException {
        source = Objects.requireNonNullElse(source, "").replace("\r\n", "\n").replace('\r', '\n');
        if (source.length() > 16000)
            throw new IllegalArgumentException("Formule: massimo 16000 caratteri.");
        if (approve) FormulaProgram.compile(source);
        if (expected == null && selected != null)
            throw new IllegalArgumentException("Versione non coerente con la nuova regola.");
        if (expected != null) {
            if (selected == null || selected.ruleId() != expected.id())
                throw new IllegalArgumentException("Seleziona la versione della regola.");
            // Lock the identity before verifying the append-only revision number.
            lock(c, expected);
            Version current = load(c, selected.id());
            if (current.ruleId() != expected.id())
                throw new IllegalArgumentException("La versione non appartiene alla regola.");
            if (!current.editable())
                throw new IllegalArgumentException(
                        "Versione in sola lettura. Crea una nuova versione per modificarla.");
            selected = current;
        }
        Entry saved = CubatureRuleCatalogue.saveIdentity(c, expected, draft);
        int number = selected == null ? 1 : selected.number(),
                revision = selected == null ? 1 : selected.revision() + 1;
        long id = insert(c, saved, number, revision, approve ? "APPROVATA" : "BOZZA", source);
        return detail(c, saved.id(), id);
    }

    public static Detail copy(Connection c, Entry expected, Version source) throws SQLException {
        lock(c, expected);
        Version current = load(c, source.id());
        if (current.ruleId() != expected.id())
            throw new IllegalArgumentException("La versione non appartiene alla regola.");
        var versions = list(c, expected.id());
        if (versions.getFirst().editable())
            throw new IllegalArgumentException(
                    "Esiste già una bozza. Aprila dalla scheda Versioni.");
        var metadata =
                new Draft(
                        expected.code(), current.name(), current.description(), expected.active());
        Entry saved = CubatureRuleCatalogue.saveIdentity(c, expected, metadata);
        long id =
                insert(c, saved, versions.getFirst().number() + 1, 1, "BOZZA", current.formulas());
        return detail(c, saved.id(), id);
    }

    private static void lock(Connection c, Entry expected) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "UPDATE TON.REGOLE_CUBATURA SET attivo=attivo WHERE regola_id=?")) {
            q.setLong(1, expected.id());
            q.executeUpdate();
        }
        if (CubatureRuleCatalogue.load(c, expected.id()).revision() != expected.revision())
            throw new Conflict();
    }

    public static List<Version> choices(Connection c) throws SQLException {
        try (var q =
                c.prepareStatement(
                        SELECT
                                + " WHERE v.stato='APPROVATA' OR EXISTS (SELECT 1 FROM"
                                + " TON.CONFIGURAZIONI_PRODOTTO p WHERE"
                                + " p.calcolo_versione_id=v.calcolo_versione_id) ORDER BY"
                                + " v.codice,v.numero_versione DESC,v.numero_revisione DESC")) {
            return rows(q);
        }
    }

    public static Version assignment(Connection c, String code, Long id) throws SQLException {
        CubatureRuleCatalogue.requireActive(c, code);
        Version selected;
        if (id != null) selected = load(c, id);
        else {
            try (var q =
                    c.prepareStatement(
                            SELECT
                                    + " WHERE v.codice=? AND v.stato='APPROVATA' ORDER BY"
                                    + " v.numero_versione DESC,v.numero_revisione DESC FETCH FIRST"
                                    + " ROW ONLY")) {
                q.setString(1, code);
                var values = rows(q);
                if (values.isEmpty())
                    throw new IllegalArgumentException(
                            "Approva una versione con formule della regola di cubatura prima di"
                                + " associarla.");
                selected = values.getFirst();
            }
        }
        if (!selected.code().equals(code) || !selected.state().equals("APPROVATA"))
            throw new IllegalArgumentException(
                    "Seleziona una versione approvata della regola di cubatura.");
        return selected;
    }

    private static long insert(
            Connection c, Entry rule, int version, int revision, String state, String formulas)
            throws SQLException {
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.REGOLA_CUBATURA_VERSIONI(regola_id,numero_versione,numero_revisione,stato,codice,nome,descrizione,attivo,formule)"
                            + " VALUES (?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setLong(1, rule.id());
            q.setInt(2, version);
            q.setInt(3, revision);
            q.setString(4, state);
            q.setString(5, rule.code());
            q.setString(6, rule.name());
            q.setString(7, rule.description());
            q.setBoolean(8, rule.active());
            q.setString(9, formulas);
            q.executeUpdate();
            try (var keys = q.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static List<Version> rows(PreparedStatement q) throws SQLException {
        var result = new ArrayList<Version>();
        try (var r = q.executeQuery()) {
            while (r.next())
                result.add(
                        new Version(
                                r.getLong("calcolo_versione_id"),
                                r.getLong("regola_id"),
                                r.getInt("numero_versione"),
                                r.getInt("numero_revisione"),
                                r.getString("stato"),
                                r.getString("codice"),
                                r.getString("nome"),
                                r.getString("descrizione"),
                                r.getBoolean("attivo"),
                                r.getString("formule"),
                                r.getTimestamp("creato_il").toLocalDateTime(),
                                r.getBoolean("corrente")));
        }
        return List.copyOf(result);
    }
}

package com.orodent.ecos.ton.features.marking.repository;

import static com.orodent.ecos.ton.features.marking.model.MarkingTemplates.*;

import com.orodent.ecos.database.CubatureRuleCatalogue;
import com.orodent.ecos.database.CubatureRuleCatalogue.Conflict;
import com.orodent.ecos.database.CubatureRuleVersions;
import com.orodent.ecos.database.CubatureRuleVersions.Version;
import com.orodent.ecos.ton.features.marking.model.MarkingTemplates;

import java.sql.*;
import java.util.*;

/** The family row serializes writes; content snapshots are only ever inserted. */
public final class MarkingTemplateRepository {
    private static final String SELECT =
            "SELECT v.*,m.codice,m.versione_modifica,CASE WHEN EXISTS (SELECT 1 FROM"
                + " TON.MARCATURA_VERSIONI n WHERE n.modello_id=v.modello_id AND"
                + " (n.numero_versione>v.numero_versione OR (n.numero_versione=v.numero_versione"
                + " AND n.numero_revisione>v.numero_revisione))) THEN FALSE ELSE TRUE END AS"
                + " corrente FROM TON.MARCATURA_VERSIONI v JOIN TON.MODELLI_MARCATURA m ON"
                + " m.modello_id=v.modello_id";
    private final Connection c;

    public MarkingTemplateRepository(Connection c) {
        this.c = c;
    }

    public List<Entry> list(String query, boolean archived) throws SQLException {
        var entries = new ArrayList<Entry>();
        try (var q =
                c.prepareStatement(
                        SELECT
                                + " ORDER BY m.codice,v.numero_versione DESC,v.numero_revisione"
                                + " DESC")) {
            try (var r = q.executeQuery()) {
                String search = query.strip().toLowerCase(Locale.ROOT);
                while (r.next())
                    if (r.getBoolean("corrente")) {
                        var entry = entry(r);
                        if ((archived || entry.latest().active())
                                && (entry.code() + " " + entry.latest().name())
                                        .toLowerCase(Locale.ROOT)
                                        .contains(search)) entries.add(entry);
                    }
            }
        }
        return List.copyOf(entries);
    }

    public Detail load(long modelId, Long snapshotId) throws SQLException {
        var snapshots = new ArrayList<Snapshot>();
        Entry entry = null;
        try (var q =
                c.prepareStatement(
                        SELECT
                                + " WHERE m.modello_id=? ORDER BY v.numero_versione"
                                + " DESC,v.numero_revisione DESC")) {
            q.setLong(1, modelId);
            try (var r = q.executeQuery()) {
                while (r.next()) {
                    if (entry == null) entry = entry(r);
                    snapshots.add(snapshot(r));
                }
            }
        }
        if (entry == null) throw new IllegalArgumentException("Modello di marcatura non trovato.");
        Snapshot selected =
                snapshotId == null
                        ? snapshots.getFirst()
                        : snapshots.stream()
                                .filter(v -> v.id() == snapshotId)
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "La versione non appartiene al modello di"
                                                            + " marcatura."));
        return new Detail(entry, selected, snapshots, rules());
    }

    public List<Version> rules() throws SQLException {
        return CubatureRuleVersions.choices(c).stream()
                .filter(v -> v.state().equals("APPROVATA"))
                .toList();
    }

    public Detail save(Entry expected, Snapshot selected, Draft draft, boolean approve)
            throws SQLException {
        Version rule = draft.ruleId() == null ? null : CubatureRuleVersions.load(c, draft.ruleId());
        if (rule != null && !rule.state().equals("APPROVATA"))
            throw new IllegalArgumentException(
                    "Scegli una versione approvata della regola di cubatura.");
        if (approve) {
            if (rule != null) CubatureRuleCatalogue.requireActive(c, rule.code());
            MarkingTemplates.validate(draft, rule);
        }
        long id;
        int number = 1, revision = 1;
        if (expected == null) {
            if (selected != null)
                throw new IllegalArgumentException("Versione non coerente con il nuovo modello.");
            try (var q =
                    c.prepareStatement(
                            "INSERT INTO TON.MODELLI_MARCATURA(codice) VALUES (?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                q.setString(1, draft.code());
                try {
                    q.executeUpdate();
                } catch (SQLException duplicate) {
                    if ("23505".equals(duplicate.getSQLState()))
                        throw new IllegalArgumentException(
                                "Esiste già un modello di marcatura con questo codice.");
                    throw duplicate;
                }
                try (var keys = q.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getLong(1);
                }
            }
        } else {
            lock(expected);
            var current = load(expected.id(), selected == null ? null : selected.id());
            if (selected == null || !current.selected().editable())
                throw new IllegalArgumentException(
                        "Versione in sola lettura. Crea una nuova versione.");
            if (!draft.code().equals(current.entry().code()))
                throw new IllegalArgumentException(
                        "Il codice del modello salvato non può essere cambiato.");
            id = expected.id();
            number = current.selected().number();
            revision = current.selected().revision() + 1;
        }
        long version = insert(id, number, revision, approve ? "APPROVATA" : "BOZZA", draft);
        return load(id, version);
    }

    public Detail copy(Entry expected, Snapshot source) throws SQLException {
        lock(expected);
        var detail = load(expected.id(), source.id());
        if (detail.entry().latest().editable())
            throw new IllegalArgumentException(
                    "Esiste già una bozza. Aprila dalla scheda Versioni.");
        var v = detail.selected();
        var draft =
                new Draft(
                        expected.code(),
                        v.name(),
                        v.description(),
                        v.active(),
                        v.text(),
                        v.ruleId(),
                        v.decimals(),
                        v.separator());
        long id = insert(expected.id(), detail.entry().latest().number() + 1, 1, "BOZZA", draft);
        return load(expected.id(), id);
    }

    private void lock(Entry expected) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "UPDATE TON.MODELLI_MARCATURA SET versione_modifica=versione_modifica+1"
                            + " WHERE modello_id=? AND codice=? AND versione_modifica=?")) {
            q.setLong(1, expected.id());
            q.setString(2, expected.code());
            q.setInt(3, expected.token());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
    }

    private long insert(long modelId, int number, int revision, String state, Draft d)
            throws SQLException {
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.MARCATURA_VERSIONI(modello_id,numero_versione,numero_revisione,stato,nome,descrizione,attivo,testo,calcolo_versione_id,decimali,separatore)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setLong(1, modelId);
            q.setInt(2, number);
            q.setInt(3, revision);
            q.setString(4, state);
            q.setString(5, d.name());
            q.setString(6, d.description());
            q.setBoolean(7, d.active());
            q.setString(8, d.text());
            q.setObject(9, d.ruleId(), Types.BIGINT);
            q.setString(10, formatsJson(d.decimals()));
            q.setString(11, d.separator());
            q.executeUpdate();
            try (var keys = q.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private Entry entry(ResultSet r) throws SQLException {
        return new Entry(
                r.getLong("modello_id"),
                r.getString("codice"),
                r.getInt("versione_modifica"),
                snapshot(r));
    }

    private Snapshot snapshot(ResultSet r) throws SQLException {
        return new Snapshot(
                r.getLong("marcatura_versione_id"),
                r.getLong("modello_id"),
                r.getInt("numero_versione"),
                r.getInt("numero_revisione"),
                r.getString("stato"),
                r.getString("nome"),
                r.getString("descrizione"),
                r.getBoolean("attivo"),
                r.getString("testo"),
                (Long) r.getObject("calcolo_versione_id"),
                formats(r.getString("decimali")),
                r.getString("separatore"),
                r.getTimestamp("creato_il").toLocalDateTime(),
                r.getBoolean("corrente"));
    }
}

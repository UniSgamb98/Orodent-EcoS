package com.orodent.ecos.ton.features.firingprograms.repository;

import static com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms.*;

import java.sql.*;
import java.util.*;

/** All saved revisions, including drafts, are immutable physical program IDs. */
public final class FiringProgramRepository {
    private final Connection c;
    private static final String SELECT =
            "SELECT p.*,f.versione_modifica AS famiglia_token,CASE WHEN EXISTS(SELECT 1 FROM"
                + " TON.PROGRAMMI_COTTURA n WHERE n.famiglia_id=p.famiglia_id AND"
                + " (n.numero_versione>p.numero_versione OR (n.numero_versione=p.numero_versione"
                + " AND n.numero_revisione>p.numero_revisione))) THEN FALSE ELSE TRUE END AS"
                + " corrente FROM TON.PROGRAMMI_COTTURA p JOIN TON.PROGRAMMI_FAMIGLIE f ON"
                + " f.famiglia_id=p.famiglia_id";

    public FiringProgramRepository(Connection c) {
        this.c = c;
    }

    public List<Entry> list(String query, boolean archived) throws SQLException {
        var entries = new ArrayList<Entry>();
        String search = query.strip().toLowerCase(Locale.ROOT);
        try (var q =
                        c.prepareStatement(
                                SELECT
                                        + " ORDER BY p.codice,p.numero_versione"
                                        + " DESC,p.numero_revisione DESC");
                var r = q.executeQuery()) {
            while (r.next())
                if (r.getBoolean("corrente")
                        && (archived || r.getBoolean("attivo"))
                        && (r.getString("codice") + " " + r.getString("nome"))
                                .toLowerCase(Locale.ROOT)
                                .contains(search))
                    entries.add(
                            new Entry(
                                    r.getLong("famiglia_id"),
                                    r.getString("codice"),
                                    r.getInt("famiglia_token"),
                                    snapshot(r)));
        }
        return List.copyOf(entries);
    }

    public Detail load(long familyId, Long snapshotId) throws SQLException {
        Entry entry = null;
        var versions = new ArrayList<Snapshot>();
        try (var q =
                c.prepareStatement(
                        SELECT
                                + " WHERE p.famiglia_id=? ORDER BY p.numero_versione"
                                + " DESC,p.numero_revisione DESC")) {
            q.setLong(1, familyId);
            try (var r = q.executeQuery()) {
                while (r.next()) {
                    var snapshot = snapshot(r);
                    if (entry == null)
                        entry =
                                new Entry(
                                        familyId,
                                        r.getString("codice"),
                                        r.getInt("famiglia_token"),
                                        snapshot);
                    versions.add(snapshot);
                }
            }
        }
        if (entry == null) throw new IllegalArgumentException("Programma di cottura non trovato.");
        Snapshot selected =
                snapshotId == null
                        ? versions.getFirst()
                        : versions.stream()
                                .filter(v -> v.id() == snapshotId)
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "La versione non appartiene al programma"
                                                            + " selezionato."));
        return new Detail(entry, selected, versions);
    }

    public Detail save(Entry expected, Snapshot selected, Draft draft, boolean approve)
            throws SQLException {
        if (approve) draft.requireApprovable();
        long family;
        int version = 1, revision = 1;
        if (expected == null) {
            if (selected != null)
                throw new IllegalArgumentException("Versione non coerente con il nuovo programma.");
            try (var q =
                    c.prepareStatement(
                            "INSERT INTO TON.PROGRAMMI_FAMIGLIE(codice) VALUES (?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                q.setString(1, draft.code());
                try {
                    q.executeUpdate();
                } catch (SQLException duplicate) {
                    if ("23505".equals(duplicate.getSQLState()))
                        throw new IllegalArgumentException(
                                "Esiste già un programma con questo codice.");
                    throw duplicate;
                }
                try (var keys = q.getGeneratedKeys()) {
                    keys.next();
                    family = keys.getLong(1);
                }
            }
        } else {
            lock(expected);
            var detail = load(expected.id(), selected == null ? null : selected.id());
            if (selected == null || !detail.selected().editable())
                throw new IllegalArgumentException(
                        "Versione in sola lettura. Crea una nuova versione per modificarla.");
            if (!draft.code().equals(detail.entry().code()))
                throw new IllegalArgumentException(
                        "Il codice del programma salvato non può essere cambiato.");
            family = expected.id();
            version = detail.selected().number();
            revision = detail.selected().revision() + 1;
        }
        long id = insert(family, version, revision, draft, approve);
        return load(family, id);
    }

    public Detail copy(Entry expected, Snapshot source) throws SQLException {
        lock(expected);
        var detail = load(expected.id(), source.id());
        if (detail.entry().latest().editable())
            throw new IllegalArgumentException(
                    "Esiste già una bozza. Aprila dalla scheda Versioni.");
        var snapshot = detail.selected();
        var draft =
                new Draft(
                        detail.entry().code(),
                        snapshot.name(),
                        snapshot.notes(),
                        snapshot.active(),
                        snapshot.steps());
        long id = insert(expected.id(), detail.entry().latest().number() + 1, 1, draft, false);
        return load(expected.id(), id);
    }

    private void lock(Entry expected) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "UPDATE TON.PROGRAMMI_FAMIGLIE SET versione_modifica=versione_modifica+1"
                            + " WHERE famiglia_id=? AND codice=? AND versione_modifica=?")) {
            q.setLong(1, expected.id());
            q.setString(2, expected.code());
            q.setInt(3, expected.token());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
    }

    private long insert(long family, int version, int revision, Draft draft, boolean approve)
            throws SQLException {
        long id;
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.PROGRAMMI_COTTURA(famiglia_id,codice,numero_versione,numero_revisione,nome,note,attivo,stato,approvato_il)"
                            + " VALUES (?,?,?,?,?,?,?,?,"
                                + (approve ? "CURRENT_TIMESTAMP" : "NULL")
                                + ")",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setLong(1, family);
            q.setString(2, draft.code());
            q.setInt(3, version);
            q.setInt(4, revision);
            q.setString(5, draft.name());
            q.setString(6, draft.notes());
            q.setBoolean(7, draft.active());
            q.setString(8, approve ? "APPROVATA" : "BOZZA");
            q.executeUpdate();
            try (var keys = q.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
        }
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.PROGRAMMA_PASSI(programma_id,numero_passo,temperatura_obiettivo_c,durata_rampa_minuti,durata_mantenimento_minuti)"
                            + " VALUES (?,?,?,?,?)")) {
            int number = 0;
            for (var step : draft.steps()) {
                q.setLong(1, id);
                q.setInt(2, ++number);
                q.setBigDecimal(3, step.temperature());
                q.setInt(4, step.rampMinutes());
                q.setInt(5, step.holdMinutes());
                q.addBatch();
            }
            q.executeBatch();
        }
        return id;
    }

    private Snapshot snapshot(ResultSet r) throws SQLException {
        long id = r.getLong("programma_id");
        var steps = new ArrayList<Step>();
        try (var q =
                c.prepareStatement(
                        "SELECT"
                            + " temperatura_obiettivo_c,durata_rampa_minuti,durata_mantenimento_minuti"
                            + " FROM TON.PROGRAMMA_PASSI WHERE programma_id=? ORDER BY"
                            + " numero_passo")) {
            q.setLong(1, id);
            try (var data = q.executeQuery()) {
                while (data.next())
                    steps.add(new Step(data.getBigDecimal(1), data.getInt(2), data.getInt(3)));
            }
        }
        return new Snapshot(
                id,
                r.getLong("famiglia_id"),
                r.getInt("numero_versione"),
                r.getInt("numero_revisione"),
                r.getString("stato"),
                r.getString("nome"),
                Objects.requireNonNullElse(r.getString("note"), ""),
                r.getBoolean("attivo"),
                steps,
                r.getTimestamp("aggiornato_il").toLocalDateTime(),
                r.getBoolean("corrente"));
    }
}

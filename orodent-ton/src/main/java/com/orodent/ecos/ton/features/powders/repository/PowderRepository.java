package com.orodent.ecos.ton.features.powders.repository;

import com.orodent.ecos.ton.features.powders.model.PowderModel.*;

import java.sql.*;
import java.util.*;

/**
 * SQL only, on a caller-owned connection. Every write claims the parent aggregate before touching a
 * revision.
 */
public final class PowderRepository {
    private final Connection connection;

    public PowderRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    public Page findPage(String filter, boolean inactive, int offset, int limit)
            throws SQLException {
        String pattern =
                "%"
                        + filter.toUpperCase(Locale.ROOT)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                        + "%";
        String where =
                " WHERE (UPPER(codice) LIKE ? ESCAPE '\\' OR UPPER(nome) LIKE ? ESCAPE '\\')"
                        + (inactive ? "" : " AND attivo=TRUE");
        long count;
        try (var query = connection.prepareStatement("SELECT COUNT(*) FROM TON.POLVERI" + where)) {
            query.setString(1, pattern);
            query.setString(2, pattern);
            try (var rows = query.executeQuery()) {
                rows.next();
                count = rows.getLong(1);
            }
        }
        var powders = new ArrayList<Powder>();
        try (var query =
                connection.prepareStatement(
                        "SELECT * FROM TON.POLVERI"
                                + where
                                + " ORDER BY CASE colore WHEN 'BIANCA' THEN 0 WHEN 'GIALLA' THEN 1"
                                + " WHEN 'ROSA' THEN 2 WHEN 'GRIGIA' THEN 3 ELSE 4"
                                + " END,codice,polvere_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            query.setString(1, pattern);
            query.setString(2, pattern);
            query.setInt(3, offset);
            query.setInt(4, limit);
            try (var rows = query.executeQuery()) {
                while (rows.next()) powders.add(powder(rows));
            }
        }
        var summaries = new HashMap<Long, List<RevisionSummary>>();
        if (!powders.isEmpty()) {
            String parameters = String.join(",", Collections.nCopies(powders.size(), "?"));
            try (var query =
                    connection.prepareStatement(
                            "SELECT polvere_id,numero_versione,stato,classe_ittria_y FROM"
                                    + " TON.POLVERI_REVISIONI WHERE polvere_id IN ("
                                    + parameters
                                    + ") ORDER BY numero_versione DESC")) {
                for (int i = 0; i < powders.size(); i++) query.setLong(i + 1, powders.get(i).id());
                try (var rows = query.executeQuery()) {
                    while (rows.next())
                        summaries
                                .computeIfAbsent(rows.getLong(1), ignored -> new ArrayList<>())
                                .add(
                                        new RevisionSummary(
                                                rows.getInt(2),
                                                State.valueOf(rows.getString(3)),
                                                (Integer) rows.getObject(4)));
                }
            }
        }
        List<Row> result =
                powders.stream()
                        .map(
                                powder -> {
                                    var revisions = summaries.getOrDefault(powder.id(), List.of());
                                    var approved =
                                            revisions.stream()
                                                    .filter(r -> r.state == State.APPROVATA)
                                                    .findFirst()
                                                    .orElse(null);
                                    var draft =
                                            revisions.stream()
                                                    .filter(r -> r.state == State.BOZZA)
                                                    .findFirst()
                                                    .orElse(null);
                                    var display =
                                            approved != null
                                                    ? approved
                                                    : revisions.isEmpty()
                                                            ? null
                                                            : revisions.getFirst();
                                    return new Row(
                                            powder,
                                            display == null ? null : display.yttria,
                                            approved == null ? null : approved.number,
                                            draft == null ? null : draft.number);
                                })
                        .toList();
        return new Page(result, count, offset, limit);
    }

    public Detail load(long id) throws SQLException {
        Powder powder;
        try (var query =
                connection.prepareStatement("SELECT * FROM TON.POLVERI WHERE polvere_id=?")) {
            query.setLong(1, id);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) throw new SQLException("Polvere non trovata.");
                powder = powder(rows);
            }
        }
        var oxides = new HashMap<Long, List<Oxide>>();
        try (var query =
                connection.prepareStatement(
                        """
                        SELECT o.* FROM TON.POLVERE_OSSIDI o JOIN TON.POLVERI_REVISIONI r
                        ON r.polvere_revisione_id=o.polvere_revisione_id WHERE r.polvere_id=?
                        ORDER BY o.percentuale_massa DESC,o.ossido_codice
                        """)) {
            query.setLong(1, id);
            try (var rows = query.executeQuery()) {
                while (rows.next())
                    oxides.computeIfAbsent(
                                    rows.getLong("polvere_revisione_id"),
                                    ignored -> new ArrayList<>())
                            .add(
                                    new Oxide(
                                            rows.getString("ossido_codice"),
                                            rows.getBigDecimal("percentuale_massa")));
            }
        }
        var revisions = new ArrayList<Revision>();
        try (var query =
                connection.prepareStatement(
                        "SELECT * FROM TON.POLVERI_REVISIONI WHERE polvere_id=? ORDER BY"
                                + " numero_versione DESC")) {
            query.setLong(1, id);
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    long revisionId = rows.getLong("polvere_revisione_id");
                    Timestamp approved = rows.getTimestamp("approvato_il");
                    revisions.add(
                            new Revision(
                                    revisionId,
                                    id,
                                    rows.getInt("numero_versione"),
                                    State.valueOf(rows.getString("stato")),
                                    rows.getInt("versione_modifica"),
                                    rows.getTimestamp("creato_il").toLocalDateTime(),
                                    approved == null ? null : approved.toLocalDateTime(),
                                    new TechnicalDraft(
                                            rows.getBigDecimal("resistenza_mpa"),
                                            rows.getBigDecimal("traslucenza"),
                                            (Integer) rows.getObject("classe_ittria_y"),
                                            rows.getString("note"),
                                            oxides.getOrDefault(revisionId, List.of()))));
                }
            }
        }
        return new Detail(powder, revisions);
    }

    public long insertPowder(IdentityDraft draft) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        "INSERT INTO TON.POLVERI(codice,nome,colore,attivo) VALUES" + " (?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            identity(statement, draft);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("ID polvere non disponibile.");
                return keys.getLong(1);
            }
        }
    }

    /**
     * This CAS UPDATE acquires the write lock until commit, including concurrent version creation.
     */
    public void claim(Powder original, IdentityDraft draft) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
                        UPDATE TON.POLVERI SET codice=?,nome=?,colore=?,attivo=?,
                        versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP
                        WHERE polvere_id=? AND versione_modifica=?
                        """)) {
            identity(statement, draft);
            statement.setLong(5, original.id());
            statement.setInt(6, original.revision());
            if (statement.executeUpdate() != 1) throw new Conflict();
        }
    }

    public long insertRevision(long powderId, int number, TechnicalDraft draft, boolean approve)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione,resistenza_mpa,traslucenza,classe_ittria_y,note,stato,approvato_il)
VALUES (?,?,?,?,?,?,?,%s)
"""
                                .formatted(approve ? "CURRENT_TIMESTAMP" : "NULL"),
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, powderId);
            statement.setInt(2, number);
            technical(statement, draft, 3);
            statement.setString(7, approve ? "APPROVATA" : "BOZZA");
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("ID scheda tecnica non disponibile.");
                long id = keys.getLong(1);
                replaceOxides(id, draft.oxides());
                return id;
            }
        }
    }

    public void updateDraft(Revision original, TechnicalDraft draft, boolean approve)
            throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
UPDATE TON.POLVERI_REVISIONI SET resistenza_mpa=?,traslucenza=?,classe_ittria_y=?,note=?,stato=?,
approvato_il=%s,versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP
WHERE polvere_revisione_id=? AND polvere_id=? AND versione_modifica=? AND stato='BOZZA'
"""
                                .formatted(approve ? "CURRENT_TIMESTAMP" : "NULL"))) {
            technical(statement, draft, 1);
            statement.setString(5, approve ? "APPROVATA" : "BOZZA");
            statement.setLong(6, original.id());
            statement.setLong(7, original.powderId());
            statement.setInt(8, original.revision());
            if (statement.executeUpdate() != 1) throw new Conflict();
        }
        replaceOxides(original.id(), draft.oxides());
    }

    public void retire(Revision original) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        """
UPDATE TON.POLVERI_REVISIONI SET stato='RITIRATA',versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP
WHERE polvere_revisione_id=? AND polvere_id=? AND versione_modifica=? AND stato='APPROVATA'
""")) {
            statement.setLong(1, original.id());
            statement.setLong(2, original.powderId());
            statement.setInt(3, original.revision());
            if (statement.executeUpdate() != 1) throw new Conflict();
        }
    }

    private void replaceOxides(long revisionId, List<Oxide> oxides) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        "DELETE FROM TON.POLVERE_OSSIDI WHERE polvere_revisione_id=?")) {
            statement.setLong(1, revisionId);
            statement.executeUpdate();
        }
        try (var statement =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " TON.POLVERE_OSSIDI(polvere_revisione_id,ossido_codice,percentuale_massa)"
                            + " VALUES (?,?,?)")) {
            for (Oxide oxide : oxides) {
                statement.setLong(1, revisionId);
                statement.setString(2, oxide.code());
                statement.setBigDecimal(3, oxide.weightPercent());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void identity(PreparedStatement statement, IdentityDraft draft)
            throws SQLException {
        statement.setString(1, draft.code());
        statement.setString(2, draft.name());
        statement.setString(3, draft.color() == null ? null : draft.color().name());
        statement.setBoolean(4, draft.active());
    }

    private static void technical(PreparedStatement statement, TechnicalDraft draft, int first)
            throws SQLException {
        statement.setBigDecimal(first, draft.strength());
        statement.setBigDecimal(first + 1, draft.translucency());
        statement.setObject(first + 2, draft.yttriaClass(), Types.INTEGER);
        statement.setString(first + 3, draft.notes());
    }

    private static Powder powder(ResultSet rows) throws SQLException {
        return new Powder(
                rows.getLong("polvere_id"),
                rows.getString("codice"),
                rows.getString("nome"),
                rows.getString("colore") == null
                        ? null
                        : PowderColor.valueOf(rows.getString("colore")),
                rows.getBoolean("attivo"),
                rows.getInt("versione_modifica"));
    }

    private record RevisionSummary(int number, State state, Integer yttria) {}
}

package com.orodent.ecos.costi.scenarios;

import com.orodent.ecos.costi.scenarios.Scenarios.*;

import java.sql.*;
import java.util.*;

public final class ScenarioRepository {
    private final Connection connection;
    private final ScenarioCodec codec = new ScenarioCodec();

    public ScenarioRepository(Connection connection) {
        this.connection = connection;
    }

    public List<Entry> list(boolean archived) throws SQLException {
        var result = new ArrayList<Entry>();
        try (var q =
                connection.prepareStatement(
                        "SELECT s.scenario_id,v.numero_versione,v.nome,v.archiviato,v.creato_il"
                            + " FROM COSTI.SCENARI s JOIN COSTI.SCENARI_REVISIONI v ON"
                            + " v.scenario_id=s.scenario_id AND v.numero_versione=s.numero_versione"
                                + (archived ? "" : " WHERE v.archiviato=FALSE")
                                + " ORDER BY v.creato_il DESC,s.scenario_id")) {
            try (var r = q.executeQuery()) {
                while (r.next())
                    result.add(
                            new Entry(
                                    UUID.fromString(r.getString(1)),
                                    r.getInt(2),
                                    r.getString(3),
                                    r.getBoolean(4),
                                    r.getTimestamp(5).toInstant()));
            }
        }
        return List.copyOf(result);
    }

    public List<Entry> history(UUID id) throws SQLException {
        var result = new ArrayList<Entry>();
        try (var q =
                connection.prepareStatement(
                        "SELECT numero_versione,nome,archiviato,creato_il FROM"
                                + " COSTI.SCENARI_REVISIONI WHERE scenario_id=? ORDER BY"
                                + " numero_versione DESC")) {
            q.setString(1, id.toString());
            try (var r = q.executeQuery()) {
                while (r.next())
                    result.add(
                            new Entry(
                                    id,
                                    r.getInt(1),
                                    r.getString(2),
                                    r.getBoolean(3),
                                    r.getTimestamp(4).toInstant()));
            }
        }
        return List.copyOf(result);
    }

    public Draft load(UUID id, int version) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "SELECT r.modifiche,b.fotografia FROM COSTI.SCENARI_REVISIONI r JOIN"
                            + " COSTI.SCENARI_BASI b ON b.base_id=r.base_id WHERE r.scenario_id=?"
                            + " AND r.numero_versione=?")) {
            q.setString(1, id.toString());
            q.setInt(2, version);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Scenario non più disponibile.");
                Draft draft = codec.edits(r.getString(1), codec.base(r.getString(2)));
                if (!draft.id().equals(id) || draft.version() != version)
                    throw new IllegalArgumentException("Revisione scenario non coerente.");
                return draft;
            }
        }
    }

    public Draft save(Draft draft) throws SQLException {
        if (draft.name().isBlank())
            throw new IllegalArgumentException("Assegna un nome allo scenario.");
        if (draft.version() == Integer.MAX_VALUE)
            throw new IllegalArgumentException("Limite revisioni raggiunto.");
        draft.recipes().values().forEach(ScenarioEngine::validateRecipe);
        draft.models().values().forEach(ScenarioEngine::validateModel);
        Draft saved = draft.saved(draft.version() + 1);
        String edits = codec.edits(saved), base = codec.base(draft.base());
        if (draft.version() == 0) {
            try (var q =
                    connection.prepareStatement(
                            "INSERT INTO COSTI.SCENARI(scenario_id,numero_versione) VALUES"
                                    + " (?,1)")) {
                q.setString(1, draft.id().toString());
                try {
                    q.executeUpdate();
                } catch (SQLException e) {
                    if ("23505".equals(e.getSQLState())) throw new Conflict();
                    throw e;
                }
            }
        } else
            try (var q =
                    connection.prepareStatement(
                            "UPDATE COSTI.SCENARI SET numero_versione=? WHERE scenario_id=? AND"
                                    + " numero_versione=?")) {
                q.setInt(1, saved.version());
                q.setString(2, draft.id().toString());
                q.setInt(3, draft.version());
                if (q.executeUpdate() != 1) throw new Conflict();
            }
        boolean exists;
        try (var q =
                connection.prepareStatement(
                        "SELECT fotografia FROM COSTI.SCENARI_BASI WHERE base_id=?")) {
            q.setString(1, draft.base().id().toString());
            try (var r = q.executeQuery()) {
                exists = r.next();
                if (exists && !codec.base(r.getString(1)).equals(draft.base()))
                    throw new IllegalArgumentException("La fotografia della base è immutabile.");
            }
        }
        if (!exists)
            try (var q =
                    connection.prepareStatement(
                            "INSERT INTO COSTI.SCENARI_BASI(base_id,fotografia) VALUES (?,?)")) {
                q.setString(1, draft.base().id().toString());
                q.setString(2, base);
                q.executeUpdate();
            }
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " COSTI.SCENARI_REVISIONI(scenario_id,numero_versione,base_id,nome,note,archiviato,modifiche)"
                            + " VALUES (?,?,?,?,?,?,?)")) {
            q.setString(1, draft.id().toString());
            q.setInt(2, saved.version());
            q.setString(3, draft.base().id().toString());
            q.setString(4, saved.name());
            q.setString(5, saved.notes());
            q.setBoolean(6, saved.archived());
            q.setString(7, edits);
            q.executeUpdate();
        }
        return saved;
    }
}

package com.orodent.ecos.crm.feature.impostazioni.service;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.feature.impostazioni.dto.*;

import java.sql.*;
import java.util.*;

/**
 * Updates stable catalogue identities; removing an item used by a customer is refused by its FK.
 */
public final class ImpostazioniService {
    private static final Map<String, List<String>> COLUMNS =
            Map.of(
                    "FORNI",
                    List.of("TECNOLOGIA", "MARCA", "MODELLO"),
                    "FRESATORI",
                    List.of("MARCA", "MODELLO"),
                    "MATERIALI_DI_CONSUMO",
                    List.of("MATERIALE", "MARCHIO", "MODELLO"),
                    "CANALI_DI_ACQUISTO",
                    List.of("MODALITA", "NOTA"));
    private final Database database;

    public ImpostazioniService(Database database) {
        this.database = database;
    }

    public List<ImpostazioneVoce> getVoci(String table, List<String> columns) {
        validate(table, columns);
        database.observeCatalogue(table);
        try (var q =
                        database.getConnection()
                                .prepareStatement(
                                        "SELECT ID,"
                                                + String.join(",", columns)
                                                + " FROM "
                                                + table
                                                + " ORDER BY "
                                                + columns.get(0));
                var r = q.executeQuery()) {
            var rows = new ArrayList<ImpostazioneVoce>();
            while (r.next()) {
                var values = new ArrayList<String>();
                for (String column : columns) values.add(Objects.toString(r.getString(column), ""));
                rows.add(new ImpostazioneVoce(UUID.fromString(r.getString("ID")), values));
            }
            return List.copyOf(rows);
        } catch (SQLException e) {
            throw new IllegalStateException("Lettura catalogo non riuscita", e);
        }
    }

    public void saveVoci(String table, List<String> columns, List<ImpostazioneVoce> rows) {
        validate(table, columns);
        database.claimCatalogue(table);
        try {
            Set<UUID> existing = new HashSet<>();
            try (var q = database.getConnection().createStatement();
                    var r = q.executeQuery("SELECT ID FROM " + table)) {
                while (r.next()) existing.add(UUID.fromString(r.getString(1)));
            }
            Set<UUID> retained = new HashSet<>();
            for (var row : rows) {
                if (row.valori().stream().allMatch(v -> v == null || v.isBlank())) continue;
                if (row.valori().size() != columns.size())
                    throw new IllegalArgumentException("Campi del catalogo incompleti.");
                UUID id = row.id() == null ? UUID.randomUUID() : row.id();
                if (!retained.add(id)) throw new IllegalArgumentException("Voce duplicata.");
                boolean update = existing.contains(id);
                String sql =
                        update
                                ? "UPDATE "
                                        + table
                                        + " SET "
                                        + String.join("=?,", columns)
                                        + "=? WHERE ID=?"
                                : "INSERT INTO "
                                        + table
                                        + " ("
                                        + String.join(",", columns)
                                        + ",ID) VALUES ("
                                        + String.join(
                                                ",", Collections.nCopies(columns.size() + 1, "?"))
                                        + ")";
                try (var q = database.getConnection().prepareStatement(sql)) {
                    for (int i = 0; i < columns.size(); i++)
                        q.setString(i + 1, row.valori().get(i));
                    q.setString(columns.size() + 1, id.toString());
                    q.executeUpdate();
                }
            }
            existing.removeAll(retained);
            try (var q =
                    database.getConnection()
                            .prepareStatement("DELETE FROM " + table + " WHERE ID=?")) {
                for (UUID id : existing) {
                    q.setString(1, id.toString());
                    q.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Salvataggio non riuscito. Una voce utilizzata nelle schede cliente non può"
                            + " essere eliminata; nessuna modifica è stata salvata.",
                    e);
        }
    }

    public List<Forno> getForni() {
        return getVoci("FORNI", COLUMNS.get("FORNI")).stream()
                .map(
                        r ->
                                new Forno(
                                        r.id(),
                                        r.valori().get(0),
                                        r.valori().get(1),
                                        r.valori().get(2)))
                .toList();
    }

    public void saveForni(List<Forno> rows) {
        saveVoci(
                "FORNI",
                COLUMNS.get("FORNI"),
                rows.stream()
                        .map(
                                r ->
                                        new ImpostazioneVoce(
                                                r.id(),
                                                List.of(r.tecnologia(), r.marca(), r.modello())))
                        .toList());
    }

    private void validate(String table, List<String> columns) {
        if (!Objects.equals(COLUMNS.get(table), columns))
            throw new IllegalArgumentException("Catalogo non valido");
    }
}

package com.orodent.ecos.crm.core.database.query.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.query.ClienteProfileQuery;
import com.orodent.ecos.crm.core.database.query.ClienteProfileQuery.AddressRecord;
import com.orodent.ecos.crm.core.database.query.ClienteProfileQuery.ContactRecord;
import com.orodent.ecos.crm.core.database.query.ClienteProfileQuery.ValueRecord;

import java.sql.Clob;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DerbyClienteProfileQuery implements ClienteProfileQuery {

    private final Database database;

    public DerbyClienteProfileQuery(Database database) {
        this.database = database;
    }

    @Override
    public Optional<ClienteProfileRecord> findById(UUID clienteId, UUID operatoreId) {
        String sql = "SELECT * FROM CLIENTI WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, clienteId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                database.observed(clienteId, resultSet.getInt("VERSIONE_MODIFICA"));
                return Optional.of(
                        new ClienteProfileRecord(
                                clienteId,
                                valueOrEmpty(resultSet.getString("RAGIONE_SOCIALE")),
                                valueOrEmpty(resultSet.getString("TIPO_CLIENTE")),
                                valueOrEmpty(resultSet.getString("STATO_TRATTATIVA")),
                                getInteger(resultSet, "COINVOLGIMENTO"),
                                valueOrEmpty(resultSet.getString("PARTITA_IVA")),
                                valueOrEmpty(resultSet.getString("CODICE_FISCALE")),
                                getDate(resultSet, "ACQUISIZIONE"),
                                isFavorite(clienteId, operatoreId),
                                findSimpleValues(
                                        "TELEFONI_CLIENTE", clienteId, "CONTATTO_ID IS NULL"),
                                findSimpleValues("EMAIL_CLIENTE", clienteId, "CONTATTO_ID IS NULL"),
                                findSimpleValues("SITI_WEB_CLIENTE", clienteId, null),
                                findIndirizzi(clienteId),
                                findContatti(clienteId),
                                findTimeline(clienteId)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento scheda cliente.", e);
        }
    }

    private boolean isFavorite(UUID clienteId, UUID operatoreId) throws SQLException {
        if (operatoreId == null) {
            return false;
        }

        String sql = "SELECT 1 FROM CLIENTI_PREFERITI WHERE CLIENTE_ID = ? AND OPERATORE_ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, clienteId.toString());
            statement.setString(2, operatoreId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private List<ValueRecord> findSimpleValues(
            String tableName, UUID clienteId, String extraCondition) throws SQLException {
        String sql = "SELECT ID, DESCRIZIONE FROM " + tableName + " WHERE CLIENTE_ID = ?";
        if (extraCondition != null && !extraCondition.isBlank()) {
            sql += " AND " + extraCondition;
        }
        sql += " ORDER BY ID";

        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, clienteId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ValueRecord> values = new ArrayList<>();
                while (resultSet.next()) {
                    addIfPresent(
                            values, getUuid(resultSet, "ID"), resultSet.getString("DESCRIZIONE"));
                }
                return values;
            }
        }
    }

    private List<AddressRecord> findIndirizzi(UUID clienteId) throws SQLException {
        String sql =
                "SELECT ID, PAESE, REGIONE, PROVINCIA, CITTA, INDIRIZZO, NUMERO_CIVICO, CAP,"
                    + " PRINCIPALE FROM INDIRIZZI_CLIENTE WHERE CLIENTE_ID = ? ORDER BY PRINCIPALE"
                    + " DESC, ID";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, clienteId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AddressRecord> indirizzi = new ArrayList<>();
                while (resultSet.next()) {
                    indirizzi.add(
                            new AddressRecord(
                                    getUuid(resultSet, "ID"),
                                    valueOrEmpty(resultSet.getString("PAESE")),
                                    valueOrEmpty(resultSet.getString("REGIONE")),
                                    valueOrEmpty(resultSet.getString("PROVINCIA")),
                                    valueOrEmpty(resultSet.getString("CITTA")),
                                    valueOrEmpty(resultSet.getString("INDIRIZZO")),
                                    valueOrEmpty(resultSet.getString("NUMERO_CIVICO")),
                                    valueOrEmpty(resultSet.getString("CAP")),
                                    resultSet.getInt("PRINCIPALE") == 1));
                }
                return indirizzi;
            }
        }
    }

    private List<ContactRecord> findContatti(UUID clienteId) throws SQLException {
        String sql =
                "SELECT ID, DESCRIZIONE FROM CONTATTI_CLIENTE WHERE CLIENTE_ID = ? ORDER BY ID";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, clienteId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ContactRecord> contatti = new ArrayList<>();
                while (resultSet.next()) {
                    UUID contattoId = getUuid(resultSet, "ID");
                    contatti.add(
                            new ContactRecord(
                                    contattoId,
                                    valueOrEmpty(resultSet.getString("DESCRIZIONE")),
                                    findLinkedValues("TELEFONI_CLIENTE", contattoId),
                                    findLinkedValues("EMAIL_CLIENTE", contattoId)));
                }
                return contatti;
            }
        }
    }

    private List<ValueRecord> findLinkedValues(String tableName, UUID contattoId)
            throws SQLException {
        String sql =
                "SELECT ID, DESCRIZIONE FROM " + tableName + " WHERE CONTATTO_ID = ? ORDER BY ID";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, contattoId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ValueRecord> values = new ArrayList<>();
                while (resultSet.next()) {
                    addIfPresent(
                            values, getUuid(resultSet, "ID"), resultSet.getString("DESCRIZIONE"));
                }
                return values;
            }
        }
    }

    private List<TimelineRecord> findTimeline(UUID clienteId) throws SQLException {
        List<TimelineRecord> timeline = new ArrayList<>();
        timeline.addAll(findStandaloneNotes(clienteId));
        timeline.addAll(findInterazioni(clienteId));
        return timeline.stream()
                .sorted(
                        Comparator.comparing(
                                        TimelineRecord::data,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(
                                        TimelineRecord::createdAt,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private List<TimelineRecord> findStandaloneNotes(UUID clienteId) throws SQLException {
        String sql =
                "SELECT N.ID, N.TESTO, N.CREATED_AT FROM NOTE_CLIENTE N WHERE N.CLIENTE_ID = ? AND"
                        + " NOT EXISTS (SELECT 1 FROM INTERAZIONI I WHERE I.NOTA_ID = N.ID)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, clienteId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TimelineRecord> notes = new ArrayList<>();
                while (resultSet.next()) {
                    notes.add(
                            new TimelineRecord(
                                    getUuid(resultSet, "ID"),
                                    null,
                                    getTimestampDate(resultSet, "CREATED_AT"),
                                    getTimestamp(resultSet, "CREATED_AT"),
                                    TimelineType.NOTA,
                                    null,
                                    null,
                                    getClobText(resultSet, "TESTO")));
                }
                return notes;
            }
        }
    }

    private List<TimelineRecord> findInterazioni(UUID clienteId) throws SQLException {
        String sql =
                "SELECT I.ID, I.NOTA_ID, I.DATA_CONTATTO, I.PROSSIMO_CONTATTO, I.ESITO,"
                        + " I.CREATED_AT, N.TESTO FROM INTERAZIONI I LEFT JOIN NOTE_CLIENTE N ON"
                        + " I.NOTA_ID = N.ID WHERE I.CLIENTE_ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, clienteId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TimelineRecord> interazioni = new ArrayList<>();
                while (resultSet.next()) {
                    LocalDate dataContatto = getDate(resultSet, "DATA_CONTATTO");
                    LocalDate createdAt = getTimestampDate(resultSet, "CREATED_AT");
                    interazioni.add(
                            new TimelineRecord(
                                    getUuid(resultSet, "NOTA_ID"),
                                    getUuid(resultSet, "ID"),
                                    dataContatto == null ? createdAt : dataContatto,
                                    getTimestamp(resultSet, "CREATED_AT"),
                                    TimelineType.CHIAMATA,
                                    getDate(resultSet, "PROSSIMO_CONTATTO"),
                                    com.orodent.ecos.crm.core.database.model.CallOutcome.fromCode(
                                            resultSet.getString("ESITO")),
                                    valueOrDefault(
                                            getClobText(resultSet, "TESTO"),
                                            "Chiamata registrata.")));
                }
                return interazioni;
            }
        }
    }

    private UUID getUuid(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private Integer getInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private LocalDate getDate(ResultSet resultSet, String column) throws SQLException {
        Date value = resultSet.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private LocalDate getTimestampDate(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime().toLocalDate();
    }

    private LocalDateTime getTimestamp(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private String getClobText(ResultSet resultSet, String column) throws SQLException {
        Clob clob = resultSet.getClob(column);
        if (clob == null) {
            return "";
        }
        long length = clob.length();
        if (length == 0) {
            return "";
        }
        return clob.getSubString(1, Math.toIntExact(length));
    }

    private void addIfPresent(List<ValueRecord> values, UUID id, String value) {
        String cleanValue = valueOrEmpty(value).trim();
        if (!cleanValue.isBlank()) {
            values.add(new ValueRecord(id, cleanValue));
        }
    }

    private String joinNonBlank(String... values) {
        List<String> cleanValues = new ArrayList<>();
        for (String value : values) {
            String cleanValue = valueOrEmpty(value).trim();
            if (!cleanValue.isBlank()) {
                cleanValues.add(cleanValue);
            }
        }
        return String.join(" · ", cleanValues);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String valueOrDefault(String value, String defaultValue) {
        String cleanValue = valueOrEmpty(value).trim();
        return cleanValue.isBlank() ? defaultValue : cleanValue;
    }
}

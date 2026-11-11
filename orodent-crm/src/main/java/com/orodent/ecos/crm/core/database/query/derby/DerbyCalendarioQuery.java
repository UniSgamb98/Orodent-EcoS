package com.orodent.ecos.crm.core.database.query.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.query.CalendarioQuery;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DerbyCalendarioQuery implements CalendarioQuery {

    private final Database database;

    public DerbyCalendarioQuery(Database database) {
        this.database = database;
    }

    @Override
    public List<CalendarioCallRecord> findProssimeChiamate(
            LocalDate from, LocalDate to, UUID clienteOperatoreId) {
        String operatorCondition = clienteOperatoreId == null ? "" : "AND C.OPERATORE_ID = ? ";
        String sql =
                "SELECT I.ID AS INTERAZIONE_ID, C.ID AS CLIENTE_ID, C.RAGIONE_SOCIALE, O.ID AS"
                    + " OPERATORE_ID, CASE WHEN TRIM(COALESCE(O.NOME, '') || ' ' ||"
                    + " COALESCE(O.COGNOME, '')) = '' THEN COALESCE(O.USERNAME, '') ELSE"
                    + " TRIM(COALESCE(O.NOME, '') || ' ' || COALESCE(O.COGNOME, '')) END AS"
                    + " OPERATORE, I.PROSSIMO_CONTATTO FROM CLIENTI C JOIN ECOS.OPERATORI O ON O.ID"
                    + " = C.OPERATORE_ID JOIN INTERAZIONI I ON I.CLIENTE_ID = C.ID WHERE"
                    + " I.PROSSIMO_CONTATTO BETWEEN ? AND ? "
                        + operatorCondition
                        + "AND NOT EXISTS (SELECT 1 FROM INTERAZIONI PI WHERE PI.CLIENTE_ID ="
                        + " I.CLIENTE_ID AND (COALESCE(PI.DATA_CONTATTO, DATE('0001-01-01')) >"
                        + " COALESCE(I.DATA_CONTATTO, DATE('0001-01-01')) OR"
                        + " (COALESCE(PI.DATA_CONTATTO, DATE('0001-01-01')) ="
                        + " COALESCE(I.DATA_CONTATTO, DATE('0001-01-01')) AND"
                        + " COALESCE(PI.CREATED_AT, TIMESTAMP('0001-01-01 00:00:00')) >"
                        + " COALESCE(I.CREATED_AT, TIMESTAMP('0001-01-01 00:00:00'))) OR"
                        + " (COALESCE(PI.DATA_CONTATTO, DATE('0001-01-01')) ="
                        + " COALESCE(I.DATA_CONTATTO, DATE('0001-01-01')) AND"
                        + " COALESCE(PI.CREATED_AT, TIMESTAMP('0001-01-01 00:00:00')) ="
                        + " COALESCE(I.CREATED_AT, TIMESTAMP('0001-01-01 00:00:00')) AND PI.ID >"
                        + " I.ID))) ORDER BY I.PROSSIMO_CONTATTO, C.RAGIONE_SOCIALE, C.ID";

        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setDate(1, java.sql.Date.valueOf(from));
            statement.setDate(2, java.sql.Date.valueOf(to));
            if (clienteOperatoreId != null) {
                statement.setString(3, clienteOperatoreId.toString());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<CalendarioCallRecord> calls = new ArrayList<>();
                while (resultSet.next()) {
                    calls.add(
                            new CalendarioCallRecord(
                                    uuid(resultSet, "INTERAZIONE_ID"),
                                    uuid(resultSet, "CLIENTE_ID"),
                                    resultSet.getString("RAGIONE_SOCIALE"),
                                    uuid(resultSet, "OPERATORE_ID"),
                                    resultSet.getString("OPERATORE"),
                                    resultSet.getDate("PROSSIMO_CONTATTO").toLocalDate()));
                }
                return calls;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento chiamate del calendario.", e);
        }
    }

    private UUID uuid(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : UUID.fromString(value);
    }
}

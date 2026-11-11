package com.orodent.ecos.crm.core.database.query.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.query.ClientiFilterQuery;
import com.orodent.ecos.crm.core.database.query.result.OperatoreClienteFilterResult;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DerbyClientiFilterQuery implements ClientiFilterQuery {

    private final Database database;

    public DerbyClientiFilterQuery(Database database) {
        this.database = database;
    }

    @Override
    public List<OperatoreClienteFilterResult> findOperatoriConClienti() {
        String sql =
                "SELECT DISTINCT O.ID, O.NOME, O.COGNOME, O.USERNAME "
                        + "FROM CLIENTI C "
                        + "JOIN ECOS.OPERATORI O ON O.ID = C.OPERATORE_ID "
                        + "ORDER BY O.USERNAME";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<OperatoreClienteFilterResult> operators = new ArrayList<>();
            while (resultSet.next()) {
                operators.add(
                        new OperatoreClienteFilterResult(
                                getUuid(resultSet, "ID"),
                                resultSet.getString("NOME"),
                                resultSet.getString("COGNOME"),
                                resultSet.getString("USERNAME")));
            }
            return operators;
        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento operatori.", e);
        }
    }

    @Override
    public List<String> findTipiCliente() {
        return findDistinctClientValues("TIPO_CLIENTE");
    }

    @Override
    public List<String> findStatiTrattativa() {
        return findDistinctClientValues("STATO_TRATTATIVA");
    }

    private List<String> findDistinctClientValues(String columnName) {
        String sql =
                "SELECT DISTINCT "
                        + columnName
                        + " FROM CLIENTI "
                        + "WHERE "
                        + columnName
                        + " IS NOT NULL AND TRIM("
                        + columnName
                        + ") <> '' "
                        + "ORDER BY "
                        + columnName;
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<String> values = new ArrayList<>();
            while (resultSet.next()) {
                values.add(valueOrEmpty(resultSet.getString(1)));
            }
            return values;
        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento filtri clienti.", e);
        }
    }

    private UUID getUuid(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}

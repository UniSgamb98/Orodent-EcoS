package com.orodent.ecos.crm.core.database.query.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.query.TipoClienteQuery;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DerbyTipoClienteQuery implements TipoClienteQuery {

    private final Database database;

    public DerbyTipoClienteQuery(Database database) {
        this.database = database;
    }

    @Override
    public List<TipoClienteRecord> findAll() {
        String sql = "SELECT ID, NOME, ORDINE FROM TIPI_CLIENTE ORDER BY ORDINE, NOME";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<TipoClienteRecord> records = new ArrayList<>();
            while (resultSet.next()) {
                records.add(
                        new TipoClienteRecord(
                                UUID.fromString(resultSet.getString("ID")),
                                valueOrEmpty(resultSet.getString("NOME")),
                                resultSet.getInt("ORDINE")));
            }
            return records;
        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento tipi cliente.", e);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}

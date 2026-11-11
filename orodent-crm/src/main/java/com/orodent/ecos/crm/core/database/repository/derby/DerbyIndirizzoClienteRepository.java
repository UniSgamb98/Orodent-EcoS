package com.orodent.ecos.crm.core.database.repository.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.model.IndirizzoCliente;
import com.orodent.ecos.crm.core.database.repository.IndirizzoClienteRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DerbyIndirizzoClienteRepository extends DerbyRepositorySupport
        implements IndirizzoClienteRepository {

    public DerbyIndirizzoClienteRepository(Database database) {
        super(database);
    }

    @Override
    public void insertAll(List<IndirizzoCliente> indirizzi) {
        String sql =
                "INSERT INTO INDIRIZZI_CLIENTE (ID, CLIENTE_ID, PAESE, REGIONE, PROVINCIA, CITTA,"
                    + " INDIRIZZO, NUMERO_CIVICO, CAP, PRINCIPALE, CREATED_AT, UPDATED_AT) VALUES"
                    + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            for (IndirizzoCliente indirizzo : indirizzi) {
                setUuid(statement, 1, indirizzo.id());
                setUuid(statement, 2, indirizzo.clienteId());
                statement.setString(3, indirizzo.paese());
                statement.setString(4, indirizzo.regione());
                statement.setString(5, indirizzo.provincia());
                statement.setString(6, indirizzo.citta());
                statement.setString(7, indirizzo.indirizzo());
                statement.setString(8, indirizzo.numeroCivico());
                statement.setString(9, indirizzo.cap());
                statement.setInt(10, indirizzo.principale() ? 1 : 0);
                setTimestamp(statement, 11, indirizzo.createdAt());
                setTimestamp(statement, 12, indirizzo.updatedAt());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw repositoryException("Errore inserimento indirizzi cliente.", e);
        }
    }

    @Override
    public void insert(IndirizzoCliente indirizzo) {
        insertAll(List.of(indirizzo));
    }

    @Override
    public void update(IndirizzoCliente indirizzo) {
        String sql =
                "UPDATE INDIRIZZI_CLIENTE SET PAESE = ?, REGIONE = ?, PROVINCIA = ?, CITTA = ?,"
                    + " INDIRIZZO = ?, NUMERO_CIVICO = ?, CAP = ?, PRINCIPALE = ?, UPDATED_AT = ?"
                    + " WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, indirizzo.paese());
            statement.setString(2, indirizzo.regione());
            statement.setString(3, indirizzo.provincia());
            statement.setString(4, indirizzo.citta());
            statement.setString(5, indirizzo.indirizzo());
            statement.setString(6, indirizzo.numeroCivico());
            statement.setString(7, indirizzo.cap());
            statement.setInt(8, indirizzo.principale() ? 1 : 0);
            setTimestamp(statement, 9, indirizzo.updatedAt());
            setUuid(statement, 10, indirizzo.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore aggiornamento indirizzo cliente.", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM INDIRIZZI_CLIENTE WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore eliminazione indirizzo cliente.", e);
        }
    }

    @Override
    public List<IndirizzoCliente> findByClienteId(UUID clienteId) {
        String sql =
                "SELECT * FROM INDIRIZZI_CLIENTE WHERE CLIENTE_ID = ? ORDER BY PRINCIPALE DESC,"
                        + " CREATED_AT";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, clienteId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<IndirizzoCliente> indirizzi = new ArrayList<>();
                while (resultSet.next()) {
                    indirizzi.add(
                            new IndirizzoCliente(
                                    getUuid(resultSet, "ID"),
                                    getUuid(resultSet, "CLIENTE_ID"),
                                    resultSet.getString("PAESE"),
                                    resultSet.getString("REGIONE"),
                                    resultSet.getString("PROVINCIA"),
                                    resultSet.getString("CITTA"),
                                    resultSet.getString("INDIRIZZO"),
                                    resultSet.getString("NUMERO_CIVICO"),
                                    resultSet.getString("CAP"),
                                    resultSet.getInt("PRINCIPALE") == 1,
                                    getTimestamp(resultSet, "CREATED_AT"),
                                    getTimestamp(resultSet, "UPDATED_AT")));
                }
                return indirizzi;
            }
        } catch (SQLException e) {
            throw repositoryException("Errore lettura indirizzi cliente.", e);
        }
    }
}

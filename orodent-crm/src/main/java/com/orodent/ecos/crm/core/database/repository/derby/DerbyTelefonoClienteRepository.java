package com.orodent.ecos.crm.core.database.repository.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.model.TelefonoCliente;
import com.orodent.ecos.crm.core.database.repository.TelefonoClienteRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DerbyTelefonoClienteRepository extends DerbyRepositorySupport
        implements TelefonoClienteRepository {

    public DerbyTelefonoClienteRepository(Database database) {
        super(database);
    }

    @Override
    public void insertAll(List<TelefonoCliente> telefoni) {
        String sql =
                "INSERT INTO TELEFONI_CLIENTE (ID, CLIENTE_ID, CONTATTO_ID, DESCRIZIONE) VALUES (?,"
                        + " ?, ?, ?)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            for (TelefonoCliente telefono : telefoni) {
                setUuid(statement, 1, telefono.id());
                setUuid(statement, 2, telefono.clienteId());
                setUuid(statement, 3, telefono.contattoId());
                statement.setString(4, telefono.descrizione());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw repositoryException("Errore inserimento telefoni cliente.", e);
        }
    }

    @Override
    public void insert(TelefonoCliente telefono) {
        insertAll(List.of(telefono));
    }

    @Override
    public void update(TelefonoCliente telefono) {
        String sql = "UPDATE TELEFONI_CLIENTE SET CONTATTO_ID = ?, DESCRIZIONE = ? WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, telefono.contattoId());
            statement.setString(2, telefono.descrizione());
            setUuid(statement, 3, telefono.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore aggiornamento telefono cliente.", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM TELEFONI_CLIENTE WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore eliminazione telefono cliente.", e);
        }
    }

    @Override
    public List<TelefonoCliente> findByClienteId(UUID clienteId) {
        String sql = "SELECT * FROM TELEFONI_CLIENTE WHERE CLIENTE_ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, clienteId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TelefonoCliente> telefoni = new ArrayList<>();
                while (resultSet.next()) {
                    telefoni.add(
                            new TelefonoCliente(
                                    getUuid(resultSet, "ID"),
                                    getUuid(resultSet, "CLIENTE_ID"),
                                    getUuid(resultSet, "CONTATTO_ID"),
                                    resultSet.getString("DESCRIZIONE")));
                }
                return telefoni;
            }
        } catch (SQLException e) {
            throw repositoryException("Errore lettura telefoni cliente.", e);
        }
    }
}

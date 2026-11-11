package com.orodent.ecos.crm.core.database.repository.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.repository.ClientePreferitoRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DerbyClientePreferitoRepository extends DerbyRepositorySupport
        implements ClientePreferitoRepository {

    public DerbyClientePreferitoRepository(Database database) {
        super(database);
    }

    @Override
    public void add(UUID operatoreId, UUID clienteId) {
        if (exists(operatoreId, clienteId)) {
            return;
        }

        String sql = "INSERT INTO CLIENTI_PREFERITI (OPERATORE_ID, CLIENTE_ID) VALUES (?, ?)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, operatoreId);
            setUuid(statement, 2, clienteId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore inserimento cliente preferito.", e);
        }
    }

    @Override
    public void remove(UUID operatoreId, UUID clienteId) {
        String sql = "DELETE FROM CLIENTI_PREFERITI WHERE OPERATORE_ID = ? AND CLIENTE_ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, operatoreId);
            setUuid(statement, 2, clienteId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore rimozione cliente preferito.", e);
        }
    }

    @Override
    public boolean exists(UUID operatoreId, UUID clienteId) {
        String sql = "SELECT 1 FROM CLIENTI_PREFERITI WHERE OPERATORE_ID = ? AND CLIENTE_ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, operatoreId);
            setUuid(statement, 2, clienteId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw repositoryException("Errore verifica cliente preferito.", e);
        }
    }

    @Override
    public List<UUID> findClienteIdsByOperatoreId(UUID operatoreId) {
        String sql = "SELECT CLIENTE_ID FROM CLIENTI_PREFERITI WHERE OPERATORE_ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, operatoreId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<UUID> clienteIds = new ArrayList<>();
                while (resultSet.next()) {
                    clienteIds.add(getUuid(resultSet, "CLIENTE_ID"));
                }
                return clienteIds;
            }
        } catch (SQLException e) {
            throw repositoryException("Errore elenco clienti preferiti.", e);
        }
    }
}

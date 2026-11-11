package com.orodent.ecos.crm.core.database.repository.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.model.ContattoCliente;
import com.orodent.ecos.crm.core.database.repository.ContattoClienteRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DerbyContattoClienteRepository extends DerbyRepositorySupport
        implements ContattoClienteRepository {

    public DerbyContattoClienteRepository(Database database) {
        super(database);
    }

    @Override
    public void insertAll(List<ContattoCliente> contatti) {
        String sql = "INSERT INTO CONTATTI_CLIENTE (ID, CLIENTE_ID, DESCRIZIONE) VALUES (?, ?, ?)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            for (ContattoCliente contatto : contatti) {
                setUuid(statement, 1, contatto.id());
                setUuid(statement, 2, contatto.clienteId());
                statement.setString(3, contatto.descrizione());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw repositoryException("Errore inserimento contatti cliente.", e);
        }
    }

    @Override
    public void insert(ContattoCliente contatto) {
        insertAll(List.of(contatto));
    }

    @Override
    public void update(ContattoCliente contatto) {
        String sql = "UPDATE CONTATTI_CLIENTE SET DESCRIZIONE = ? WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, contatto.descrizione());
            setUuid(statement, 2, contatto.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore aggiornamento contatto cliente.", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM CONTATTI_CLIENTE WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore eliminazione contatto cliente.", e);
        }
    }

    @Override
    public List<ContattoCliente> findByClienteId(UUID clienteId) {
        String sql = "SELECT * FROM CONTATTI_CLIENTE WHERE CLIENTE_ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, clienteId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ContattoCliente> contatti = new ArrayList<>();
                while (resultSet.next()) {
                    contatti.add(
                            new ContattoCliente(
                                    getUuid(resultSet, "ID"),
                                    getUuid(resultSet, "CLIENTE_ID"),
                                    resultSet.getString("DESCRIZIONE")));
                }
                return contatti;
            }
        } catch (SQLException e) {
            throw repositoryException("Errore lettura contatti cliente.", e);
        }
    }
}

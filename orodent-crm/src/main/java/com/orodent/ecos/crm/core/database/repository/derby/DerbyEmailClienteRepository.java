package com.orodent.ecos.crm.core.database.repository.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.model.EmailCliente;
import com.orodent.ecos.crm.core.database.repository.EmailClienteRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DerbyEmailClienteRepository extends DerbyRepositorySupport
        implements EmailClienteRepository {

    public DerbyEmailClienteRepository(Database database) {
        super(database);
    }

    @Override
    public void insertAll(List<EmailCliente> email) {
        String sql =
                "INSERT INTO EMAIL_CLIENTE (ID, CLIENTE_ID, CONTATTO_ID, DESCRIZIONE) VALUES (?, ?,"
                        + " ?, ?)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            for (EmailCliente item : email) {
                setUuid(statement, 1, item.id());
                setUuid(statement, 2, item.clienteId());
                setUuid(statement, 3, item.contattoId());
                statement.setString(4, item.descrizione());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw repositoryException("Errore inserimento email cliente.", e);
        }
    }

    @Override
    public void insert(EmailCliente email) {
        insertAll(List.of(email));
    }

    @Override
    public void update(EmailCliente email) {
        String sql = "UPDATE EMAIL_CLIENTE SET CONTATTO_ID = ?, DESCRIZIONE = ? WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, email.contattoId());
            statement.setString(2, email.descrizione());
            setUuid(statement, 3, email.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore aggiornamento email cliente.", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM EMAIL_CLIENTE WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore eliminazione email cliente.", e);
        }
    }

    @Override
    public List<EmailCliente> findByClienteId(UUID clienteId) {
        String sql = "SELECT * FROM EMAIL_CLIENTE WHERE CLIENTE_ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, clienteId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<EmailCliente> email = new ArrayList<>();
                while (resultSet.next()) {
                    email.add(
                            new EmailCliente(
                                    getUuid(resultSet, "ID"),
                                    getUuid(resultSet, "CLIENTE_ID"),
                                    getUuid(resultSet, "CONTATTO_ID"),
                                    resultSet.getString("DESCRIZIONE")));
                }
                return email;
            }
        } catch (SQLException e) {
            throw repositoryException("Errore lettura email cliente.", e);
        }
    }
}

package com.orodent.ecos.crm.core.database.repository.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.model.NotaCliente;
import com.orodent.ecos.crm.core.database.repository.NotaClienteRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DerbyNotaClienteRepository extends DerbyRepositorySupport
        implements NotaClienteRepository {

    public DerbyNotaClienteRepository(Database database) {
        super(database);
    }

    @Override
    public void insert(NotaCliente nota) {
        String sql =
                "INSERT INTO NOTE_CLIENTE (ID, CLIENTE_ID, OPERATORE_ID, TESTO, CREATED_AT,"
                        + " UPDATED_AT) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            bindNota(statement, nota);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore inserimento nota cliente.", e);
        }
    }

    @Override
    public void update(NotaCliente nota) {
        String sql =
                "UPDATE NOTE_CLIENTE SET OPERATORE_ID = ?, TESTO = ?, UPDATED_AT = ? WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, nota.operatoreId());
            statement.setString(2, nota.testo());
            setTimestamp(statement, 3, nota.updatedAt());
            setUuid(statement, 4, nota.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore aggiornamento nota cliente.", e);
        }
    }

    @Override
    public Optional<NotaCliente> findById(UUID id) {
        String sql = "SELECT * FROM NOTE_CLIENTE WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapNota(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw repositoryException("Errore lettura nota cliente.", e);
        }
    }

    @Override
    public List<NotaCliente> findByClienteId(UUID clienteId) {
        String sql = "SELECT * FROM NOTE_CLIENTE WHERE CLIENTE_ID = ? ORDER BY CREATED_AT DESC";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, clienteId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<NotaCliente> note = new ArrayList<>();
                while (resultSet.next()) {
                    note.add(mapNota(resultSet));
                }
                return note;
            }
        } catch (SQLException e) {
            throw repositoryException("Errore elenco note cliente.", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM NOTE_CLIENTE WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore eliminazione nota cliente.", e);
        }
    }

    private void bindNota(PreparedStatement statement, NotaCliente nota) throws SQLException {
        setUuid(statement, 1, nota.id());
        setUuid(statement, 2, nota.clienteId());
        setUuid(statement, 3, nota.operatoreId());
        statement.setString(4, nota.testo());
        setTimestamp(statement, 5, nota.createdAt());
        setTimestamp(statement, 6, nota.updatedAt());
    }

    private NotaCliente mapNota(ResultSet resultSet) throws SQLException {
        return new NotaCliente(
                getUuid(resultSet, "ID"),
                getUuid(resultSet, "CLIENTE_ID"),
                getUuid(resultSet, "OPERATORE_ID"),
                resultSet.getString("TESTO"),
                getTimestamp(resultSet, "CREATED_AT"),
                getTimestamp(resultSet, "UPDATED_AT"));
    }
}

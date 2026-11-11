package com.orodent.ecos.crm.core.database.repository.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.model.SitoWebCliente;
import com.orodent.ecos.crm.core.database.repository.SitoWebClienteRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DerbySitoWebClienteRepository extends DerbyRepositorySupport
        implements SitoWebClienteRepository {

    public DerbySitoWebClienteRepository(Database database) {
        super(database);
    }

    @Override
    public void insertAll(List<SitoWebCliente> sitiWeb) {
        String sql = "INSERT INTO SITI_WEB_CLIENTE (ID, CLIENTE_ID, DESCRIZIONE) VALUES (?, ?, ?)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            for (SitoWebCliente sitoWeb : sitiWeb) {
                setUuid(statement, 1, sitoWeb.id());
                setUuid(statement, 2, sitoWeb.clienteId());
                statement.setString(3, sitoWeb.descrizione());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw repositoryException("Errore inserimento siti web cliente.", e);
        }
    }

    @Override
    public void insert(SitoWebCliente sitoWeb) {
        insertAll(List.of(sitoWeb));
    }

    @Override
    public void update(SitoWebCliente sitoWeb) {
        String sql = "UPDATE SITI_WEB_CLIENTE SET DESCRIZIONE = ? WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, sitoWeb.descrizione());
            setUuid(statement, 2, sitoWeb.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore aggiornamento sito web cliente.", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM SITI_WEB_CLIENTE WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore eliminazione sito web cliente.", e);
        }
    }

    @Override
    public List<SitoWebCliente> findByClienteId(UUID clienteId) {
        String sql = "SELECT * FROM SITI_WEB_CLIENTE WHERE CLIENTE_ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, clienteId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SitoWebCliente> sitiWeb = new ArrayList<>();
                while (resultSet.next()) {
                    sitiWeb.add(
                            new SitoWebCliente(
                                    getUuid(resultSet, "ID"),
                                    getUuid(resultSet, "CLIENTE_ID"),
                                    resultSet.getString("DESCRIZIONE")));
                }
                return sitiWeb;
            }
        } catch (SQLException e) {
            throw repositoryException("Errore lettura siti web cliente.", e);
        }
    }
}

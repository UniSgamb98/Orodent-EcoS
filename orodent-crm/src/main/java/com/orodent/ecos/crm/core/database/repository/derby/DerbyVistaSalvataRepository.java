package com.orodent.ecos.crm.core.database.repository.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.model.VersionedViewPayload;
import com.orodent.ecos.crm.core.database.model.VistaSalvata;
import com.orodent.ecos.crm.core.database.repository.VistaSalvataRepository;
import com.orodent.ecos.crm.core.session.FeatureKey;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DerbyVistaSalvataRepository extends DerbyRepositorySupport
        implements VistaSalvataRepository {

    private static final String COLUMNS =
            "ID, OPERATORE_ID, FEATURE_KEY, NOME, CONFIGURAZIONE, "
                    + "CONFIG_VERSION, PREDEFINITA, CREATED_AT, UPDATED_AT";

    public DerbyVistaSalvataRepository(Database database) {
        super(database);
    }

    @Override
    public List<VistaSalvata> findAll(UUID operatoreId, FeatureKey feature) {
        String sql =
                "SELECT "
                        + COLUMNS
                        + " FROM VISTE_SALVATE "
                        + "WHERE OPERATORE_ID = ? AND FEATURE_KEY = ? ORDER BY NOME";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, operatoreId);
            statement.setString(2, feature.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<VistaSalvata> viste = new ArrayList<>();
                while (resultSet.next()) {
                    viste.add(readVista(resultSet));
                }
                return viste;
            }
        } catch (SQLException e) {
            throw repositoryException("Errore caricamento viste salvate.", e);
        }
    }

    @Override
    public Optional<VistaSalvata> findById(UUID id, UUID operatoreId) {
        String sql = "SELECT " + COLUMNS + " FROM VISTE_SALVATE WHERE ID = ? AND OPERATORE_ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, id);
            setUuid(statement, 2, operatoreId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readVista(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw repositoryException("Errore caricamento vista salvata.", e);
        }
    }

    @Override
    public Optional<VistaSalvata> findPredefinita(UUID operatoreId, FeatureKey feature) {
        String sql =
                "SELECT "
                        + COLUMNS
                        + " FROM VISTE_SALVATE "
                        + "WHERE OPERATORE_ID = ? AND FEATURE_KEY = ? AND PREDEFINITA = 1";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, operatoreId);
            statement.setString(2, feature.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readVista(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw repositoryException("Errore caricamento vista predefinita.", e);
        }
    }

    @Override
    public void insert(VistaSalvata vista) {
        Connection connection = database.getConnection();
        try {

            if (vista.predefinita()) {
                clearPredefinita(connection, vista.operatoreId(), vista.feature());
            }
            String sql =
                    "INSERT INTO VISTE_SALVATE (ID, OPERATORE_ID, FEATURE_KEY, NOME,"
                        + " CONFIGURAZIONE, CONFIG_VERSION, PREDEFINITA, CREATED_AT, UPDATED_AT)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindVista(statement, vista);
                statement.executeUpdate();
            }

        } catch (SQLException e) {

            throw repositoryException("Errore salvataggio vista.", e);
        }
    }

    @Override
    public boolean update(VistaSalvata vista) {
        Connection connection = database.getConnection();
        try {

            if (vista.predefinita()) {
                clearPredefinita(connection, vista.operatoreId(), vista.feature());
            }
            String sql =
                    "UPDATE VISTE_SALVATE SET NOME = ?, CONFIGURAZIONE = ?, CONFIG_VERSION = ?,"
                        + " PREDEFINITA = ?, UPDATED_AT = ? WHERE ID = ? AND OPERATORE_ID = ? AND"
                        + " FEATURE_KEY = ?";
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, vista.nome());
                setConfiguration(statement, 2, vista.payload().configuration());
                statement.setInt(3, vista.payload().version());
                statement.setInt(4, vista.predefinita() ? 1 : 0);
                setTimestamp(statement, 5, vista.updatedAt());
                setUuid(statement, 6, vista.id());
                setUuid(statement, 7, vista.operatoreId());
                statement.setString(8, vista.feature().name());
                updated = statement.executeUpdate();
            }
            if (updated != 1) {
                throw new Database.Conflict();
            }

            return true;
        } catch (SQLException e) {

            throw repositoryException("Errore aggiornamento vista salvata.", e);
        }
    }

    @Override
    public boolean delete(UUID id, UUID operatoreId) {
        String sql = "DELETE FROM VISTE_SALVATE WHERE ID = ? AND OPERATORE_ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, id);
            setUuid(statement, 2, operatoreId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw repositoryException("Errore eliminazione vista salvata.", e);
        }
    }

    @Override
    public boolean setPredefinita(UUID id, UUID operatoreId, FeatureKey feature) {
        Connection connection = database.getConnection();
        try {

            clearPredefinita(connection, operatoreId, feature);
            String sql =
                    "UPDATE VISTE_SALVATE SET PREDEFINITA = 1, UPDATED_AT = CURRENT_TIMESTAMP "
                            + "WHERE ID = ? AND OPERATORE_ID = ? AND FEATURE_KEY = ?";
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setUuid(statement, 1, id);
                setUuid(statement, 2, operatoreId);
                statement.setString(3, feature.name());
                updated = statement.executeUpdate();
            }
            if (updated != 1) {
                throw new Database.Conflict();
            }

            return true;
        } catch (SQLException e) {

            throw repositoryException("Errore impostazione vista predefinita.", e);
        }
    }

    private VistaSalvata readVista(ResultSet resultSet) throws SQLException {
        return new VistaSalvata(
                getUuid(resultSet, "ID"),
                getUuid(resultSet, "OPERATORE_ID"),
                FeatureKey.valueOf(resultSet.getString("FEATURE_KEY")),
                resultSet.getString("NOME"),
                new VersionedViewPayload(
                        resultSet.getInt("CONFIG_VERSION"), resultSet.getString("CONFIGURAZIONE")),
                resultSet.getInt("PREDEFINITA") == 1,
                getTimestamp(resultSet, "CREATED_AT"),
                getTimestamp(resultSet, "UPDATED_AT"));
    }

    private void bindVista(PreparedStatement statement, VistaSalvata vista) throws SQLException {
        setUuid(statement, 1, vista.id());
        setUuid(statement, 2, vista.operatoreId());
        statement.setString(3, vista.feature().name());
        statement.setString(4, vista.nome());
        setConfiguration(statement, 5, vista.payload().configuration());
        statement.setInt(6, vista.payload().version());
        statement.setInt(7, vista.predefinita() ? 1 : 0);
        setTimestamp(statement, 8, vista.createdAt());
        setTimestamp(statement, 9, vista.updatedAt());
    }

    private void setConfiguration(PreparedStatement statement, int index, String configuration)
            throws SQLException {
        statement.setCharacterStream(
                index, new StringReader(configuration), configuration.length());
    }

    private void clearPredefinita(Connection connection, UUID operatoreId, FeatureKey feature)
            throws SQLException {
        String sql =
                "UPDATE VISTE_SALVATE SET PREDEFINITA = 0, UPDATED_AT = CURRENT_TIMESTAMP "
                        + "WHERE OPERATORE_ID = ? AND FEATURE_KEY = ? AND PREDEFINITA = 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setUuid(statement, 1, operatoreId);
            statement.setString(2, feature.name());
            statement.executeUpdate();
        }
    }
}

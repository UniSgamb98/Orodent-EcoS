package com.orodent.ecos.crm.core.database.repository.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.model.CallOutcome;
import com.orodent.ecos.crm.core.database.model.Interazione;
import com.orodent.ecos.crm.core.database.repository.InterazioneRepository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DerbyInterazioneRepository extends DerbyRepositorySupport
        implements InterazioneRepository {

    public DerbyInterazioneRepository(Database database) {
        super(database);
    }

    @Override
    public void insert(Interazione interazione) {
        String sql =
                "INSERT INTO INTERAZIONI (ID, CLIENTE_ID, OPERATORE_ID, NOTA_ID, DATA_CONTATTO,"
                    + " PROSSIMO_CONTATTO, ESITO, COINVOLGIMENTO, CREATED_AT, UPDATED_AT) VALUES"
                    + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            bindInterazione(statement, interazione);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore inserimento interazione cliente.", e);
        }
    }

    @Override
    public void update(Interazione interazione) {
        String sql =
                "UPDATE INTERAZIONI SET OPERATORE_ID = ?, NOTA_ID = ?, DATA_CONTATTO = ?,"
                    + " PROSSIMO_CONTATTO = ?, ESITO = ?, COINVOLGIMENTO = ?, UPDATED_AT = ? WHERE"
                    + " ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, interazione.operatoreId());
            setUuid(statement, 2, interazione.notaId());
            setDate(statement, 3, interazione.dataContatto());
            setDate(statement, 4, interazione.prossimoContatto());
            statement.setString(5, interazione.esito() == null ? null : interazione.esito().name());
            statement.setBigDecimal(6, interazione.coinvolgimento());
            setTimestamp(statement, 7, interazione.updatedAt());
            setUuid(statement, 8, interazione.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore aggiornamento interazione cliente.", e);
        }
    }

    @Override
    public Optional<Interazione> findById(UUID id) {
        String sql = "SELECT * FROM INTERAZIONI WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapInterazione(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw repositoryException("Errore lettura interazione cliente.", e);
        }
    }

    @Override
    public List<Interazione> findByClienteId(UUID clienteId) {
        String sql =
                "SELECT * FROM INTERAZIONI WHERE CLIENTE_ID = ? ORDER BY DATA_CONTATTO DESC,"
                        + " CREATED_AT DESC";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, clienteId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Interazione> interazioni = new ArrayList<>();
                while (resultSet.next()) {
                    interazioni.add(mapInterazione(resultSet));
                }
                return interazioni;
            }
        } catch (SQLException e) {
            throw repositoryException("Errore elenco interazioni cliente.", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM INTERAZIONI WHERE ID = ?";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            setUuid(statement, 1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw repositoryException("Errore eliminazione interazione cliente.", e);
        }
    }

    private void bindInterazione(PreparedStatement statement, Interazione interazione)
            throws SQLException {
        setUuid(statement, 1, interazione.id());
        setUuid(statement, 2, interazione.clienteId());
        setUuid(statement, 3, interazione.operatoreId());
        setUuid(statement, 4, interazione.notaId());
        setDate(statement, 5, interazione.dataContatto());
        setDate(statement, 6, interazione.prossimoContatto());
        statement.setString(7, interazione.esito() == null ? null : interazione.esito().name());
        statement.setBigDecimal(8, interazione.coinvolgimento());
        setTimestamp(statement, 9, interazione.createdAt());
        setTimestamp(statement, 10, interazione.updatedAt());
    }

    private Interazione mapInterazione(ResultSet resultSet) throws SQLException {
        BigDecimal coinvolgimento = resultSet.getBigDecimal("COINVOLGIMENTO");
        return new Interazione(
                getUuid(resultSet, "ID"),
                getUuid(resultSet, "CLIENTE_ID"),
                getUuid(resultSet, "OPERATORE_ID"),
                getUuid(resultSet, "NOTA_ID"),
                getDate(resultSet, "DATA_CONTATTO"),
                getDate(resultSet, "PROSSIMO_CONTATTO"),
                CallOutcome.fromCode(resultSet.getString("ESITO")),
                coinvolgimento,
                getTimestamp(resultSet, "CREATED_AT"),
                getTimestamp(resultSet, "UPDATED_AT"));
    }
}

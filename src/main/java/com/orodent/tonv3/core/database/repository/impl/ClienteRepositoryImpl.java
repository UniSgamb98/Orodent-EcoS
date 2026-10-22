package com.orodent.tonv3.core.database.repository.impl;

import com.orodent.tonv3.core.database.model.Cliente;
import com.orodent.tonv3.core.database.repository.ClienteRepository;
import com.orodent.tonv3.core.database.repository.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ClienteRepositoryImpl(Connection connection) implements ClienteRepository {
    private static final int BATCH_SIZE = 500;

    public ClienteRepositoryImpl(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public int deleteAll() {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM clienti")) {
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RepositoryException("Errore durante la cancellazione dell'anagrafica clienti", exception);
        }
    }

    @Override
    public int insertAll(List<Cliente> clienti) {
        String sql = """
                INSERT INTO clienti (
                    codice_cliente, codice_iso, categoria, listino, agente,
                    tipo_cliente, ragione_sociale, importato_il
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int inserted = 0;
            for (int start = 0; start < clienti.size(); start += BATCH_SIZE) {
                statement.clearBatch();
                int end = Math.min(start + BATCH_SIZE, clienti.size());
                for (int index = start; index < end; index++) {
                    bind(statement, clienti.get(index));
                    statement.addBatch();
                }
                statement.executeBatch();
                inserted += end - start;
            }
            return inserted;
        } catch (SQLException exception) {
            throw new RepositoryException("Errore durante l'inserimento dell'anagrafica clienti", exception);
        }
    }

    @Override
    public Optional<Cliente> findByCode(String codiceCliente) {
        String sql = selectColumns() + " WHERE codice_cliente = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, codiceCliente);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Errore durante la ricerca del cliente", exception);
        }
    }

    @Override
    public List<Cliente> findAll() {
        try (PreparedStatement statement = connection.prepareStatement(selectColumns() + " ORDER BY ragione_sociale");
             ResultSet result = statement.executeQuery()) {
            List<Cliente> clienti = new ArrayList<>();
            while (result.next()) clienti.add(map(result));
            return List.copyOf(clienti);
        } catch (SQLException exception) {
            throw new RepositoryException("Errore durante la lettura dell'anagrafica clienti", exception);
        }
    }

    private void bind(PreparedStatement statement, Cliente cliente) throws SQLException {
        statement.setString(1, cliente.codiceCliente());
        statement.setString(2, cliente.codiceIso());
        statement.setString(3, cliente.categoria());
        statement.setString(4, cliente.listino());
        statement.setString(5, cliente.agente());
        statement.setString(6, cliente.tipoCliente());
        statement.setString(7, cliente.ragioneSociale());
        statement.setTimestamp(8, Timestamp.valueOf(
                cliente.importatoIl() == null ? LocalDateTime.now() : cliente.importatoIl()
        ));
    }

    private Cliente map(ResultSet result) throws SQLException {
        Timestamp imported = result.getTimestamp("importato_il");
        return new Cliente(
                result.getString("codice_cliente"), result.getString("codice_iso"),
                result.getString("categoria"), result.getString("listino"), result.getString("agente"),
                result.getString("tipo_cliente"), result.getString("ragione_sociale"),
                imported == null ? null : imported.toLocalDateTime()
        );
    }

    private String selectColumns() {
        return "SELECT codice_cliente, codice_iso, categoria, listino, agente, tipo_cliente, "
                + "ragione_sociale, importato_il FROM clienti";
    }
}

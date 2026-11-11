package com.orodent.ecos.crm.core.database;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.crm.core.database.service.CurrentOperatoreService;
import com.orodent.ecos.module.OperatorSession;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Transaction-scoped access to the EcoS database. No server, discovery, reconnect or shared
 * connection.
 */
public final class Database {
    private final ConnectionProvider connections;
    private final CurrentOperatoreService operators;
    private final Map<String, Integer> revisions = new ConcurrentHashMap<>();
    private final ThreadLocal<Scope> current = new ThreadLocal<>();

    private static final class Scope {
        final Connection connection;
        final Map<String, Integer> observed = new HashMap<>();
        final Set<String> claimed = new HashSet<>();

        Scope(Connection c) {
            connection = c;
        }
    }

    public Database(ConnectionProvider connections, OperatorSession operators) {
        this.connections = Objects.requireNonNull(connections);
        this.operators = new CurrentOperatoreService(operators);
    }

    public CurrentOperatoreService operators() {
        return operators;
    }

    public Connection getConnection() {
        Scope scope = current.get();
        if (scope == null)
            throw new IllegalStateException(
                    "Il database CRM richiede un'operazione in background con transazione.");
        return scope.connection;
    }

    public <T> T transaction(Supplier<T> work) {
        if (javafx.application.Platform.isFxApplicationThread())
            throw new IllegalStateException("JDBC non può essere eseguito sul thread grafico.");
        if (current.get() != null) return work.get();
        Scope[] completed = new Scope[1];
        T result =
                connections.withTransaction(
                        c -> {
                            c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                            c.setSchema("CRM");
                            Scope scope = new Scope(c);
                            current.set(scope);
                            try {
                                T value = work.get();
                                completed[0] = scope;
                                return value;
                            } finally {
                                current.remove();
                            }
                        });
        revisions.putAll(completed[0].observed);
        return result;
    }

    public void observed(UUID id, int revision) {
        current.get().observed.put("cliente:" + id, revision);
    }

    public void claim(UUID id) {
        claim(
                "cliente:" + id,
                "UPDATE CLIENTI SET VERSIONE_MODIFICA=VERSIONE_MODIFICA+1 WHERE ID=? AND"
                        + " VERSIONE_MODIFICA=?",
                id.toString());
    }

    public void observeCatalogue(String table) {
        try (var q =
                getConnection()
                        .prepareStatement("SELECT VERSIONE FROM CATALOGHI_VERSIONI WHERE NOME=?")) {
            q.setString(1, table);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Catalogo sconosciuto");
                current.get().observed.put("catalogo:" + table, r.getInt(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lettura revisione catalogo non riuscita", e);
        }
    }

    public void claimCatalogue(String table) {
        claim(
                "catalogo:" + table,
                "UPDATE CATALOGHI_VERSIONI SET VERSIONE=VERSIONE+1 WHERE NOME=? AND VERSIONE=?",
                table);
    }

    private void claim(String key, String sql, String id) {
        Scope scope = current.get();
        if (scope.claimed.contains(key)) return;
        Integer expected = scope.observed.getOrDefault(key, revisions.get(key));
        if (expected == null)
            throw new IllegalStateException("Ricarica la scheda prima di modificarla.");
        try (var q = getConnection().prepareStatement(sql)) {
            q.setString(1, id);
            q.setInt(2, expected);
            if (q.executeUpdate() != 1) throw new Conflict();
            scope.claimed.add(key);
            scope.observed.put(key, expected + 1);
        } catch (SQLException e) {
            throw new IllegalStateException("Salvataggio revisione non riuscito", e);
        }
    }

    public static final class Conflict extends RuntimeException {
        public Conflict() {
            super(
                    "Questi dati sono stati modificati da un'altra postazione. Ricarica la scheda;"
                            + " le modifiche non sono state sovrascritte.");
        }
    }
}

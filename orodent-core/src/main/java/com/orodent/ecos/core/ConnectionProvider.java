package com.orodent.ecos.core;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface ConnectionProvider {

    Connection openConnection();

    default <T> T withConnection(ConnectionWork<T> work) {
        try (Connection connection = openConnection()) {
            return work.execute(connection);
        } catch (SQLException exception) {
            throw new RuntimeException("Errore durante l'operazione sul database.", exception);
        }
    }

    default <T> T withTransaction(ConnectionWork<T> work) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RuntimeException("Errore durante la transazione sul database.", exception);
        }
    }

    @FunctionalInterface
    interface ConnectionWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}

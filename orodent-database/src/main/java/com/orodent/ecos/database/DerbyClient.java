package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.discovery.HostInfo;
import org.apache.derby.jdbc.ClientDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/** Every operation obtains its own connection; no transaction is shared across workers. */
public final class DerbyClient implements ConnectionProvider {
    private final ClientDataSource source = new ClientDataSource();
    private final int timeoutSeconds;
    private java.util.UUID identity;

    public DerbyClient(HostInfo host, int timeoutMs) {
        source.setServerName(host.address().getHostAddress());
        source.setPortNumber(host.dbPort());
        source.setDatabaseName(DatabaseConfiguration.DATABASE_NAME);
        source.setUser(DatabaseConfiguration.USER);
        source.setPassword(DatabaseConfiguration.PASSWORD);
        timeoutSeconds = Math.max(1, (timeoutMs + 999) / 1000);
        source.setLoginTimeout(timeoutSeconds);
    }

    public java.util.UUID verify() throws SQLException { return verify(null); }

    public java.util.UUID verify(java.util.UUID expectedIdentity) throws SQLException {
        try (Connection connection = source.getConnection()) {
            // No create=true, DDL or migration history writes on a remote host.
            java.util.UUID verified = DatabaseIdentity.require(connection, expectedIdentity);
            DatabaseSchema.verifyCompatible(connection);
            identity = verified;
            return identity;
        }
    }

    @Override
    public Connection openConnection() {
        Connection connection = null;
        try {
            connection = source.getConnection();
            DatabaseIdentity.require(connection, identity);
            return connection;
        } catch (SQLException exception) {
            if (connection != null) {
                try { connection.close(); }
                catch (SQLException closeFailure) { exception.addSuppressed(closeFailure); }
            }
            throw new IllegalStateException("Impossibile connettersi al database host.", exception);
        }
    }

    /** JDBC isValid uses Derby's socket timeout for this probe, not an unbounded query. */
    public boolean isReachable() throws SQLException {
        try (Connection connection = source.getConnection()) {
            return connection.isValid(timeoutSeconds);
        }
    }
}

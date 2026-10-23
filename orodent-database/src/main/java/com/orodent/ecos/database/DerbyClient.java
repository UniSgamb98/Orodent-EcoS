package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.discovery.HostInfo;
import org.apache.derby.jdbc.ClientDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/** Every operation obtains its own connection; no transaction is shared across workers. */
public final class DerbyClient implements ConnectionProvider {
    private final ClientDataSource source = new ClientDataSource();

    public DerbyClient(HostInfo host, int timeoutMs) {
        source.setServerName(host.address().getHostAddress());
        source.setPortNumber(host.dbPort());
        source.setDatabaseName(DatabaseConfiguration.DATABASE_NAME);
        source.setUser(DatabaseConfiguration.USER);
        source.setPassword(DatabaseConfiguration.PASSWORD);
        source.setLoginTimeout(Math.max(1, (timeoutMs + 999) / 1000));
    }

    public void verify() throws SQLException {
        try (Connection connection = source.getConnection()) {
            // No create=true, DDL or migration history writes on a remote host.
            DatabaseSchema.verifyCompatible(connection);
        }
    }

    @Override
    public Connection openConnection() {
        try {
            return source.getConnection();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile connettersi al database host.", exception);
        }
    }
}

package com.orodent.tonv3.core.database;

import com.orodent.tonv3.core.ConnectionProvider;
import com.orodent.tonv3.core.database.discovery.HostInfo;
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
        try (Connection connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.setQueryTimeout(3);
            // No create=true and no schema updates on a remote host.
            statement.executeQuery("SELECT codice_cliente FROM APP.clienti WHERE 1=0").close();
            statement.executeQuery("SELECT vendita_id FROM APP.vendite_dettaglio WHERE 1=0").close();
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

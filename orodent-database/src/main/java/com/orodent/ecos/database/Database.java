package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;

import com.orodent.ecos.database.discovery.DiscoveryClient;

import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/** Chooses the startup role; connection and resource ownership belong to the selected backend. */
public class Database implements ConnectionProvider {
    private static final System.Logger LOG = System.getLogger(Database.class.getName());
    private final DatabaseConfiguration configuration;
    private volatile State state = State.NEW;
    private volatile DatabaseMode mode = DatabaseMode.NOT_STARTED;
    private volatile ConnectionProvider connections;
    private DerbyHost host;

    public Database() {
        this(DatabaseConfiguration.forHome(resolveDatabaseHome(System.getProperty("ton.database.home"),
                System.getenv("TON_DATABASE_HOME"), System.getProperty("os.name", ""),
                System.getProperty("user.home"))));
    }

    public Database(DatabaseConfiguration configuration) {
        this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public Connection openConnection() {
        ConnectionProvider provider = connections;
        if (state != State.READY || provider == null)
            throw new IllegalStateException("Database non pronto: stato " + state);
        return provider.openConnection();
    }

    public synchronized void start() {
        if (state == State.READY) return;
        state = State.STARTING;
        try {
            if (!connectToDiscoveredHost()) {
                host = new DerbyHost(configuration);
                try {
                    host.start();
                    connections = host;
                    mode = DatabaseMode.HOST;
                } catch (DatabaseResourceBusyException busy) {
                    host.close();
                    host = null;
                    // Another instance can be initializing its schema before it starts advertising.
                    boolean connected = false;
                    for (int attempt = 0; attempt < 3 && !connected; attempt++) {
                        connected = connectToDiscoveredHost();
                    }
                    if (!connected) throw busy;
                }
            }
            state = State.READY;
        } catch (Exception failure) {
            releaseHost();
            connections = null;
            mode = DatabaseMode.NOT_STARTED;
            state = State.FAILED;
            if (failure instanceof InterruptedException || failure instanceof InterruptedIOException)
                Thread.currentThread().interrupt();
            String message = failure instanceof DatabaseResourceBusyException
                    ? "Database o porte occupati: nessun host Ton-V3 disponibile. Verifica che l'istanza principale sia avviata."
                    : "Impossibile avviare il database.";
            throw new DatabaseInitializationException(message, failure);
        }
    }

    private boolean connectToDiscoveredHost() throws java.io.IOException {
        DiscoveryClient discovery = new DiscoveryClient(configuration.discoveryPort(), configuration.discoveryTimeoutMs());
        return discovery.findHost(candidate -> {
            DerbyClient client = new DerbyClient(candidate, configuration.discoveryTimeoutMs());
            try {
                client.verify();
                connections = client;
                mode = DatabaseMode.CLIENT;
                return true;
            } catch (SQLException failure) {
                LOG.log(System.Logger.Level.DEBUG, "Host discovery non utilizzabile: SQLState " + failure.getSQLState());
                return false;
            }
        }).isPresent();
    }

    static Path resolveDatabaseHome(String propertyValue, String environmentValue,
                                    String operatingSystem, String userHome) {
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Path.of(propertyValue);
        }
        if (environmentValue != null && !environmentValue.isBlank()) {
            return Path.of(environmentValue);
        }
        return defaultDatabaseHome(operatingSystem, userHome);
    }

    private static Path defaultDatabaseHome(String operatingSystem, String userHome) {
        String normalizedOperatingSystem = operatingSystem == null ? "" : operatingSystem.toLowerCase();
        if (normalizedOperatingSystem.contains("win")) {
            // Preserve the location used by previous releases unless explicitly overridden.
            return Path.of("C:\\");
        }
        return Path.of(userHome, ".ton-v3", "database");
    }

    public synchronized void stop() {
        releaseHost();
        connections = null;
        mode = DatabaseMode.NOT_STARTED;
        state = State.STOPPED;
    }

    private void releaseHost() {
        if (host != null) {
            host.close();
            host = null;
        }
    }

    public DatabaseMode getMode() { return mode; }

    State state() { return state; }

    enum State { NEW, STARTING, READY, FAILED, STOPPED }

    public static class DatabaseInitializationException extends RuntimeException {
        public DatabaseInitializationException(String message, Throwable cause) { super(message, cause); }
    }
}

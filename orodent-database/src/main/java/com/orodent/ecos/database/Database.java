package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.discovery.DiscoveryClient;

import java.io.InterruptedIOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Owns role selection and recovery; failed user operations are never replayed. */
public class Database implements ConnectionProvider {
    private static final long HOST_STARTUP_GRACE_MS = 15_000;
    private static final long MONITOR_INTERVAL_MS = 2_000;
    private static final System.Logger LOG = System.getLogger(Database.class.getName());
    private final DatabaseConfiguration configuration;
    private final CopyOnWriteArrayList<Consumer<Status>> listeners = new CopyOnWriteArrayList<>();
    private volatile Status status = new Status(State.NEW, DatabaseMode.NOT_STARTED, "Database non avviato");
    private volatile ConnectionProvider connections;
    private volatile ScheduledExecutorService monitor;
    private volatile boolean stopping;
    private long monitorGeneration;
    private DerbyHost host;
    private UUID identity;

    public Database() { this(DatabaseSettings.load()); }

    public Database(DatabaseConfiguration configuration) {
        this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public Connection openConnection() {
        ConnectionProvider provider = connections;
        if (status.state() != State.READY || provider == null) {
            if (status.state() == State.RECONNECTING) throw new DatabaseDisconnectedException(null);
            throw new IllegalStateException("Database non pronto: stato " + status.state());
        }
        try {
            return provider.openConnection();
        } catch (RuntimeException failure) {
            throw operationFailure(provider, failure);
        }
    }

    @Override
    public <T> T withConnection(ConnectionWork<T> work) {
        ConnectionProvider provider = connections;
        try { return ConnectionProvider.super.withConnection(work); }
        catch (RuntimeException failure) { throw operationFailure(provider, failure); }
    }

    @Override
    public <T> T withTransaction(ConnectionWork<T> work) {
        ConnectionProvider provider = connections;
        try { return ConnectionProvider.super.withTransaction(work); }
        catch (RuntimeException failure) { throw operationFailure(provider, failure); }
    }

    private RuntimeException operationFailure(ConnectionProvider provider, RuntimeException failure) {
        if (failure instanceof DatabaseDisconnectedException || !ConnectionFailures.isConnectionLoss(failure)) return failure;
        synchronized (this) {
            if (provider == connections && status.state() == State.READY) connectionLost();
        }
        return new DatabaseDisconnectedException(failure);
    }

    public synchronized void start() {
        if (status.state() == State.READY || status.state() == State.RECONNECTING) return;
        stopping = false;
        boolean resuming = identity != null;
        publish(State.STARTING, DatabaseMode.NOT_STARTED, "Avvio del database");
        try {
            releaseHost();
            if (!connectToDiscoveredHost(configuration.discoveryTimeoutMs(), resuming)) {
                host = new DerbyHost(configuration);
                try {
                    if (resuming) host.startExisting(identity); else host.start();
                    connections = host;
                    identity = host.identity();
                    connected(DatabaseMode.HOST);
                } catch (DatabaseResourceBusyException busy) {
                    releaseHost();
                    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(HOST_STARTUP_GRACE_MS);
                    boolean connected = false;
                    while (!connected) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Avvio database interrotto");
                        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                        if (remaining <= 0) break;
                        connected = connectToDiscoveredHost((int) Math.min(remaining,
                                Math.min(configuration.discoveryTimeoutMs(), 500)), resuming);
                    }
                    if (!connected) throw busy;
                }
            }
            startMonitor();
        } catch (Exception failure) {
            releaseHost();
            connections = null;
            publish(State.FAILED, DatabaseMode.NOT_STARTED, initializationMessage(failure));
            if (failure instanceof InterruptedException || failure instanceof InterruptedIOException)
                Thread.currentThread().interrupt();
            if (failure instanceof DatabaseInitializationException initialization) throw initialization;
            String message = failure instanceof DatabaseResourceBusyException
                    ? "Database o porte occupati: nessun host Orodent-EcoS disponibile. Verifica che l'istanza principale sia avviata."
                    : initializationMessage(failure);
            throw new DatabaseInitializationException(message, failure);
        }
    }

    private static String initializationMessage(Exception failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && (DatabaseBackup.FAILURE_STATE.equals(sql.getSQLState())
                    || DatabaseSchema.INCOMPATIBLE_SCHEMA.equals(sql.getSQLState())
                    || DatabaseIdentity.MISMATCH.equals(sql.getSQLState()))) return sql.getMessage();
        }
        return "Impossibile avviare il database.";
    }

    private boolean connectToDiscoveredHost(int timeoutMs, boolean recovering) throws java.io.IOException {
        DiscoveryClient discovery = new DiscoveryClient(configuration.discoveryPort(), timeoutMs);
        return discovery.findHost(candidate -> {
            DerbyClient client = new DerbyClient(candidate, configuration.discoveryTimeoutMs());
            try {
                UUID verified = client.verify(recovering ? identity : null);
                connections = client;
                identity = verified;
                connected(DatabaseMode.CLIENT);
                return true;
            } catch (SQLException failure) {
                if (DatabaseSchema.INCOMPATIBLE_SCHEMA.equals(failure.getSQLState())
                        || (!recovering && DatabaseIdentity.MISMATCH.equals(failure.getSQLState()))) {
                    throw new DatabaseInitializationException("Database host incompatibile: " + failure.getMessage(), failure);
                }
                LOG.log(System.Logger.Level.DEBUG, "Host discovery non utilizzabile: SQLState " + failure.getSQLState());
                return false;
            }
        }).isPresent();
    }

    private void startMonitor() {
        if (monitor != null) monitor.shutdownNow();
        long generation = ++monitorGeneration;
        monitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ecos-database-monitor");
            thread.setDaemon(true);
            return thread;
        });
        monitor.scheduleWithFixedDelay(() -> monitorCycle(generation),
                MONITOR_INTERVAL_MS, MONITOR_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void monitorCycle(long generation) {
        if (stopping || generation != monitorGeneration) return;
        if (status.state() == State.READY && connections instanceof DerbyClient client) {
            try {
                if (client.isReachable()) return;
            } catch (SQLException | RuntimeException unavailable) {
                LOG.log(System.Logger.Level.DEBUG, "HOST non raggiungibile", unavailable);
            }
            connectionLost();
        }
        if (status.state() != State.RECONNECTING) return;
        try {
            if (connectToDiscoveredHost(Math.min(configuration.discoveryTimeoutMs(), 500), true)) return;
            // Promotion never creates an empty database or runs a migration during a live session.
            DerbyHost candidate = new DerbyHost(configuration);
            try {
                candidate.startExisting(identity);
                host = candidate;
                connections = candidate;
                connected(DatabaseMode.HOST);
            } catch (Exception failure) {
                candidate.close();
                throw failure;
            }
        } catch (DatabaseInitializationException incompatible) {
            connections = null;
            publish(State.FAILED, DatabaseMode.CLIENT, incompatible.getMessage());
        } catch (Exception unavailable) {
            if (!stopping && !Thread.currentThread().isInterrupted()) {
                String detail = unavailable instanceof DatabaseResourceBusyException
                        ? "Attesa dell'HOST: l'archivio è ancora occupato."
                        : unavailable.getMessage();
                publish(State.RECONNECTING, DatabaseMode.CLIENT,
                        "Riconnessione al database in corso. " + (detail == null ? "" : detail));
            }
        }
    }

    private void connectionLost() {
        if (status.mode() == DatabaseMode.HOST) {
            publish(State.FAILED, DatabaseMode.HOST, "Archivio database non disponibile. Verificare la cartella condivisa e riavviare l'HOST.");
        } else {
            publish(State.RECONNECTING, DatabaseMode.CLIENT, "Connessione persa. Ricerca dell'HOST in corso.");
        }
    }

    private void connected(DatabaseMode mode) {
        publish(State.READY, mode, "Database connesso · Modalità " + mode);
    }

    /** Callbacks must be nonblocking; JavaFX subscribers marshal changes with Platform.runLater. */
    public synchronized Runnable observeStatus(Consumer<Status> listener) {
        java.util.Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.accept(status);
        return () -> listeners.remove(listener);
    }

    private void publish(State state, DatabaseMode mode, String message) {
        Status next = new Status(state, mode, message);
        if (next.equals(status)) return;
        status = next;
        for (Consumer<Status> listener : listeners) {
            try { listener.accept(next); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "Errore listener stato database", failure); }
        }
    }

    public void stop() {
        stopping = true;
        ScheduledExecutorService worker = monitor;
        if (worker != null) worker.shutdownNow();
        synchronized (this) {
            monitorGeneration++;
            releaseHost();
            connections = null;
            publish(State.STOPPED, DatabaseMode.NOT_STARTED, "Database arrestato");
        }
    }

    private void releaseHost() {
        if (host != null) { host.close(); host = null; }
    }

    public DatabaseMode getMode() { return status.mode(); }
    public Status status() { return status; }
    State state() { return status.state(); }

    public enum State { NEW, STARTING, READY, RECONNECTING, FAILED, STOPPED }
    public record Status(State state, DatabaseMode mode, String message) { }

    public static class DatabaseInitializationException extends RuntimeException {
        public DatabaseInitializationException(String message, Throwable cause) { super(message, cause); }
    }
}

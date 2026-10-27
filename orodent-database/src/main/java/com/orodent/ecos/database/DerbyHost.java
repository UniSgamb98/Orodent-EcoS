package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.discovery.DiscoveryServer;
import org.apache.derby.drda.NetworkServerControl;

import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Owns only the database, network server and discovery socket started by this instance. */
public final class DerbyHost implements ConnectionProvider, AutoCloseable {
    private static final System.Logger LOG = System.getLogger(DerbyHost.class.getName());
    private final DatabaseConfiguration configuration;
    private FileChannel lockChannel;
    private FileLock lock;
    private DiscoveryServer discovery;
    private NetworkServerControl server;
    private boolean databaseStarted;
    private boolean serverStarted;
    private java.util.UUID identity;

    public DerbyHost(DatabaseConfiguration configuration) {
        this.configuration = configuration;
    }

    public void start() throws Exception {
        start(null);
    }

    /** Recovery opens only the pinned existing archive, without DDL or create=true. */
    public void startExisting(java.util.UUID expectedIdentity) throws Exception {
        java.util.Objects.requireNonNull(expectedIdentity, "expectedIdentity");
        if (!Files.isRegularFile(configuration.home().resolve(DatabaseConfiguration.DATABASE_NAME).resolve("service.properties")))
            throw new java.io.IOException("Archivio condiviso non disponibile. Nessun database alternativo è stato creato.");
        start(expectedIdentity);
    }

    private void start(java.util.UUID expectedIdentity) throws Exception {
        Files.createDirectories(configuration.home());
        lockChannel = FileChannel.open(configuration.home().resolve(".ton-host.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = lockChannel.tryLock();
        } catch (OverlappingFileLockException busy) {
            throw new DatabaseResourceBusyException("Database già gestito da un'altra istanza.", busy);
        }
        if (lock == null) throw new DatabaseResourceBusyException("Database già gestito da un'altra istanza.", null);

        // Reserve discovery before booting Derby, so concurrent instances cannot both become hosts.
        try {
            discovery = new DiscoveryServer(configuration.discoveryPort(), configuration.databasePort());
            try (ServerSocket reservation = new ServerSocket(configuration.databasePort())) {
                System.setProperty("derby.system.home", configuration.home().toString());
                System.setProperty("derby.drda.startNetworkServer", "false");
                Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getConstructor().newInstance();
                try (Connection connection = connect(expectedIdentity == null)) {
                    databaseStarted = true;
                    if (expectedIdentity != null) {
                        identity = DatabaseIdentity.require(connection, expectedIdentity);
                        DatabaseSchema.verifyCompatible(connection);
                    }
                } catch (SQLException failure) {
                    if (isDatabaseOccupied(failure)) {
                        throw new DatabaseResourceBusyException("Database occupato da un'altra istanza.", failure);
                    }
                    throw failure;
                }
                if (expectedIdentity == null) {
                    DatabaseBackup backup = new DatabaseBackup(configuration.home());
                    new DatabaseSchema(this).initialize(connection -> {
                        backup.create(connection);
                        return null;
                    });
                    try (Connection connection = connect(false)) {
                        identity = DatabaseIdentity.initialize(connection);
                    }
                }
            }
        } catch (BindException busy) {
            throw new DatabaseResourceBusyException("Porta database o discovery già occupata.", busy);
        }

        server = new NetworkServerControl(InetAddress.getByName("0.0.0.0"), configuration.databasePort());
        server.start(null);
        serverStarted = true;
        NetworkServerControl probe = new NetworkServerControl(InetAddress.getByName("127.0.0.1"), configuration.databasePort());
        Exception lastFailure = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            try {
                probe.ping();
                discovery.start(); // Advertise only after schema initialization and network readiness.
                return;
            } catch (InterruptedException interrupted) {
                throw interrupted;
            } catch (Exception failure) {
                lastFailure = failure;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Timeout avvio server Derby.", lastFailure);
    }

    private static boolean isDatabaseOccupied(SQLException failure) {
        for (SQLException current = failure; current != null; current = current.getNextException()) {
            if ("XSDB6".equals(current.getSQLState())) return true;
        }
        Throwable cause = failure.getCause();
        return cause instanceof SQLException sql && cause != failure && isDatabaseOccupied(sql);
    }

    private Connection connect(boolean create) throws SQLException {
        return DriverManager.getConnection("jdbc:derby:" + DatabaseConfiguration.DATABASE_NAME
                + (create ? ";create=true" : ""), DatabaseConfiguration.USER, DatabaseConfiguration.PASSWORD);
    }

    @Override
    public Connection openConnection() {
        try {
            return connect(false);
        } catch (SQLException failure) {
            throw new IllegalStateException("Impossibile connettersi al database locale.", failure);
        }
    }

    public java.util.UUID identity() { return identity; }

    @Override
    public void close() {
        if (discovery != null) discovery.close();
        if (serverStarted) {
            try { server.shutdown(); }
            catch (Exception failure) { LOG.log(System.Logger.Level.WARNING, "Errore arresto server Derby", failure); }
            serverStarted = false;
        }
        if (databaseStarted) {
            try {
                DriverManager.getConnection("jdbc:derby:" + DatabaseConfiguration.DATABASE_NAME + ";shutdown=true",
                        DatabaseConfiguration.USER, DatabaseConfiguration.PASSWORD);
            } catch (SQLException shutdown) {
                if (!"08006".equals(shutdown.getSQLState()))
                    LOG.log(System.Logger.Level.WARNING, "Errore arresto database", shutdown);
            }
            databaseStarted = false;
        }
        try {
            if (lock != null && lock.isValid()) lock.release();
        } catch (Exception failure) {
            LOG.log(System.Logger.Level.WARNING, "Errore rilascio lock database", failure);
        } finally {
            try { if (lockChannel != null) lockChannel.close(); }
            catch (Exception failure) { LOG.log(System.Logger.Level.WARNING, "Errore chiusura lock database", failure); }
        }
    }
}

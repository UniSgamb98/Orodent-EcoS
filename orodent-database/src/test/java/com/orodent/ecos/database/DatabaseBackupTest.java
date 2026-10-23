package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseBackupTest {
    @TempDir Path temporary;
    private final List<String> databases = new ArrayList<>();
    private Path home;
    private ConnectionProvider connections;
    private String previousLog;

    @BeforeEach
    void open() throws Exception {
        previousLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", temporary.resolve("derby.log").toString());
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        home = Files.createDirectories(temporary.resolve("Ufficio d'Italia à"));
        connections = database(home.resolve("OrodentEcoS"), ";create=true");
        try (var ignored = connections.openConnection()) { }
    }

    @AfterEach
    void close() {
        for (String url : databases) {
            try { DriverManager.getConnection(url + ";shutdown=true", "APP", "pw"); }
            catch (SQLException expected) { assertEquals("08006", expected.getSQLState()); }
        }
        if (previousLog == null) System.clearProperty("derby.stream.error.file");
        else System.setProperty("derby.stream.error.file", previousLog);
    }

    @Test
    void takesARestorableSnapshotBeforeAnyBaselineOrMigrationWrites() throws Exception {
        legacyData();
        migrate();
        Path snapshot = onlySnapshot();
        Properties metadata = new Properties();
        try (var input = Files.newInputStream(snapshot.resolve("backup.properties"))) { metadata.load(input); }
        assertEquals("OrodentEcoS", metadata.getProperty("database"));
        assertEquals("2", metadata.getProperty("migration.target"));
        assertEquals(home.resolve("OrodentEcoS").toString(), metadata.getProperty("source.database"));
        ConnectionProvider restored = restore(snapshot);
        assertCustomer(restored, "APP.CLIENTI");
        restored.withConnection(connection -> {
            try (var tables = connection.getMetaData().getTables(null, "APP", "ECOS_SCHEMA_HISTORY", new String[]{"TABLE"})) {
                assertFalse(tables.next(), "Il backup deve precedere anche la registrazione della baseline.");
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT vendita_id, importo_netto FROM APP.VENDITE_DETTAGLIO")) {
                assertTrue(rows.next());
                assertEquals(42, rows.getLong(1));
                assertEquals("123.45", rows.getBigDecimal(2).toPlainString());
            }
            return null;
        });
        assertCustomer(connections, "ANAGRAFICHE.CLIENTI");
        migrate();
        assertEquals(snapshot, onlySnapshot(), "Un DB già aggiornato non richiede un altro backup.");
    }

    @Test
    void doesNotBackUpAnEmptyNewDatabaseOrOnOrdinaryRestart() {
        migrate();
        migrate();
        assertFalse(Files.exists(home.resolve("backups")));
        connections.withConnection(connection -> {
            connection.setReadOnly(true);
            DatabaseSchema.verifyCompatible(connection);
            return null;
        });
        assertFalse(Files.exists(home.resolve("backups")));
    }

    @Test
    void refusesToMigrateIfTheBackupCannotBeWritten() throws Exception {
        legacyData();
        Files.writeString(home.resolve("backups"), "Directory non disponibile");
        RuntimeException failure = assertThrows(RuntimeException.class, this::migrate);
        assertInstanceOf(SQLException.class, failure.getCause());
        assertEquals("XEC02", ((SQLException) failure.getCause()).getSQLState());
        assertCustomer(connections, "APP.CLIENTI");
        connections.withConnection(connection -> {
            try (var tables = connection.getMetaData().getTables(null, null, "ECOS_SCHEMA_HISTORY", new String[]{"TABLE"})) {
                assertFalse(tables.next());
            }
            return null;
        });
    }

    @Test
    void retainsTheCompletedSnapshotIfMigrationFailsAfterTheBackup() throws Exception {
        legacyData();
        execute("CREATE VIEW APP.CLIENTI_EXPORT AS SELECT codice_cliente FROM APP.CLIENTI");
        assertThrows(RuntimeException.class, this::migrate);
        assertCustomer(connections, "APP.CLIENTI");
        assertCustomer(restore(onlySnapshot()), "APP.CLIENTI");
    }

    private void migrate() {
        new DatabaseSchema(connections).initialize(connection -> {
            assertTrue(connection.getAutoCommit(), "Nessuna transazione di migrazione deve precedere il backup.");
            new DatabaseBackup(home).create(connection);
            return null;
        });
    }

    private Path onlySnapshot() throws Exception {
        try (var paths = Files.list(home.resolve("backups"))) {
            List<Path> copies = paths.toList();
            assertEquals(1, copies.size());
            assertTrue(Files.isRegularFile(copies.getFirst().resolve("backup.properties")));
            return copies.getFirst();
        }
    }

    private ConnectionProvider restore(Path snapshot) throws Exception {
        Path restoredHome = Files.createDirectories(temporary.resolve("restored"));
        ConnectionProvider restored = database(restoredHome.resolve("OrodentEcoS"),
                ";createFrom=" + snapshot.resolve("OrodentEcoS"));
        try (var ignored = restored.openConnection()) { }
        return database(restoredHome.resolve("OrodentEcoS"), "");
    }

    private ConnectionProvider database(Path path, String attributes) {
        String url = "jdbc:derby:" + path.toAbsolutePath();
        if (!databases.contains(url)) databases.add(url);
        return () -> {
            try { return DriverManager.getConnection(url + attributes, "APP", "pw"); }
            catch (SQLException failure) { throw new IllegalStateException(failure); }
        };
    }

    private void legacyData() throws Exception {
        try (var connection = connections.openConnection()) {
            new SchemaMigration(1, "Legacy", "/database/schema.sql").apply(connection, false);
        }
        execute("INSERT INTO APP.CLIENTI(codice_cliente, ragione_sociale) VALUES ('OLD', 'Cliente da conservare')");
        execute("ALTER TABLE APP.VENDITE_DETTAGLIO ALTER COLUMN vendita_id RESTART WITH 42");
        execute("INSERT INTO APP.VENDITE_DETTAGLIO(sorgente,documento_id,numero_riga,data_vendita,codice_cliente,codice_prodotto,quantita,importo_netto) "
                + "VALUES ('TEST','OLD',1,DATE('2025-01-01'),'OLD','P',1,123.45)");
    }

    private void execute(String sql) {
        connections.withConnection(connection -> {
            try (var statement = connection.createStatement()) { statement.executeUpdate(sql); }
            return null;
        });
    }

    private static void assertCustomer(ConnectionProvider provider, String table) {
        provider.withConnection(connection -> {
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT codice_cliente, ragione_sociale FROM " + table)) {
                assertTrue(rows.next());
                assertEquals("OLD", rows.getString(1));
                assertEquals("Cliente da conservare", rows.getString(2));
                assertFalse(rows.next());
            }
            return null;
        });
    }
}

package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseSchemaTest {
    private static final String DERBY_LOG_PROPERTY = "derby.stream.error.file";
    private String url;
    private ConnectionProvider connections;
    private String previousDerbyLog;

    @BeforeEach
    void createDatabase() throws Exception {
        previousDerbyLog = System.getProperty(DERBY_LOG_PROPERTY);
        System.setProperty(DERBY_LOG_PROPERTY, "target/schema-test-derby.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:schema_" + UUID.randomUUID().toString().replace("-", "");
        connections = () -> {
            try { return DriverManager.getConnection(url + ";create=true", "APP", "pw"); }
            catch (SQLException failure) { throw new IllegalStateException(failure); }
        };
    }

    @AfterEach
    void closeDatabase() {
        try { DriverManager.getConnection(url + ";drop=true", "APP", "pw"); }
        catch (SQLException expected) { assertEquals("08006", expected.getSQLState()); }
        finally {
            if (previousDerbyLog == null) System.clearProperty(DERBY_LOG_PROPERTY);
            else System.setProperty(DERBY_LOG_PROPERTY, previousDerbyLog);
        }
    }

    @Test
    void initializesAndRecordsBaselineExactlyOnce() {
        DatabaseSchema schema = new DatabaseSchema(connections);
        schema.initialize();
        schema.initialize();
        connections.withConnection(connection -> {
            DatabaseSchema.verifyCompatible(connection);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT version, description, checksum FROM APP.ECOS_SCHEMA_HISTORY")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt("version"));
                assertEquals(64, rows.getString("checksum").length());
                assertFalse(rows.next());
            }
            return null;
        });
    }

    @Test
    void registersExistingSchemaWithoutLosingItsRows() throws Exception {
        createLegacySchema();
        connections.withConnection(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO APP.clienti(codice_cliente, ragione_sociale) VALUES ('OLD', 'Existing customer')");
            }
            return null;
        });
        new DatabaseSchema(connections).initialize();
        connections.withConnection(connection -> {
            DatabaseSchema.verifyCompatible(connection);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT ragione_sociale FROM APP.clienti WHERE codice_cliente = 'OLD'")) {
                assertTrue(rows.next());
                assertEquals("Existing customer", rows.getString(1));
            }
            return null;
        });
    }

    @Test
    void clientChecksLegacySchemaWithoutCreatingMigrationHistory() throws Exception {
        createLegacySchema();
        connections.withConnection(connection -> {
            connection.setReadOnly(true);
            DatabaseSchema.verifyCompatible(connection);
            assertFalse(tableExists(connection, "ECOS_SCHEMA_HISTORY"));
            return null;
        });
    }

    @Test
    void rejectsFutureSchemaForBothHostAndClient() {
        new DatabaseSchema(connections).initialize();
        connections.withConnection(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE APP.ECOS_SCHEMA_HISTORY SET version = 2");
            }
            return null;
        });
        assertThrows(RuntimeException.class, () -> new DatabaseSchema(connections).initialize());
        connections.withConnection(connection -> {
            SQLException failure = assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            assertEquals("XEC01", failure.getSQLState());
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT version FROM APP.ECOS_SCHEMA_HISTORY")) {
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
            }
            return null;
        });
    }

    @Test
    void rejectsModifiedMigrationHistory() {
        new DatabaseSchema(connections).initialize();
        connections.withConnection(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE APP.ECOS_SCHEMA_HISTORY SET checksum = '" + "0".repeat(64) + "'");
            }
            return null;
        });
        assertThrows(RuntimeException.class, () -> new DatabaseSchema(connections).initialize());
    }

    @Test
    void rollsBackNewTablesWhenAnExistingTableIsIncompatible() {
        connections.withConnection(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE APP.clienti(codice_cliente INTEGER)");
            }
            return null;
        });
        assertThrows(RuntimeException.class, () -> new DatabaseSchema(connections).initialize());
        connections.withConnection(connection -> {
            assertTrue(tableExists(connection, "CLIENTI"));
            assertFalse(tableExists(connection, "VENDITE_DETTAGLIO"));
            assertFalse(tableExists(connection, "ECOS_SCHEMA_HISTORY"));
            return null;
        });
    }

    @Test
    void rejectsChangedColumnsEvenWhenMigrationVersionIsCurrent() {
        new DatabaseSchema(connections).initialize();
        connections.withConnection(connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE APP.clienti DROP COLUMN codice_iso");
            }
            assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            return null;
        });
    }

    @Test
    void rejectsLegacySchemaWithoutTheGeneratedSalesIdentity() throws Exception {
        createLegacySchema(script -> script.replace("GENERATED ALWAYS AS IDENTITY", ""));
        assertThrows(RuntimeException.class, () -> new DatabaseSchema(connections).initialize());
        connections.withConnection(connection -> {
            assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            assertFalse(tableExists(connection, "ECOS_SCHEMA_HISTORY"));
            return null;
        });
    }

    @Test
    void rejectsLegacySchemaWithoutImportTimestampDefaults() throws Exception {
        createLegacySchema(script -> script.replace("DEFAULT CURRENT_TIMESTAMP", ""));
        assertThrows(RuntimeException.class, () -> new DatabaseSchema(connections).initialize());
        connections.withConnection(connection -> {
            assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            assertFalse(tableExists(connection, "ECOS_SCHEMA_HISTORY"));
            return null;
        });
    }

    private void createLegacySchema() throws Exception {
        createLegacySchema(UnaryOperator.identity());
    }

    private void createLegacySchema(UnaryOperator<String> transform) throws Exception {
        String script;
        try (var input = DatabaseSchemaTest.class.getResourceAsStream("/database/schema.sql")) {
            assertNotNull(input);
            script = transform.apply(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        connections.withTransaction(connection -> {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    try (var statement = connection.createStatement()) { statement.executeUpdate(sql); }
                }
            }
            return null;
        });
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (var tables = connection.getMetaData().getTables(null, "APP", name, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}

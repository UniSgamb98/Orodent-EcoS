package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
    void upgradesVersionTwoWithoutReplacingSharedDataOrTheExistingHistory() {
        SchemaV2Fixture.create(connections);
        execute("INSERT INTO ANAGRAFICHE.CLIENTI(codice_cliente,ragione_sociale) VALUES ('KEEP','Da conservare')");
        var customers = rows("SELECT * FROM ANAGRAFICHE.CLIENTI");
        var history = rows("SELECT * FROM ECOS.ECOS_SCHEMA_HISTORY ORDER BY version");
        var backups = new java.util.concurrent.atomic.AtomicInteger();
        new DatabaseSchema(connections).initialize(connection -> {
            assertEquals(List.of(List.of(1), List.of(2)), rows("SELECT version FROM ECOS.ECOS_SCHEMA_HISTORY ORDER BY version"));
            assertFalse(tableExists(connection, "TON", "PRODOTTI"));
            backups.incrementAndGet();
            return null;
        });
        assertEquals(customers, rows("SELECT * FROM ANAGRAFICHE.CLIENTI"));
        assertEquals(history, rows("SELECT * FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version <= 2 ORDER BY version"));
        migrate();
        assertEquals(1, backups.get());
        assertEquals(List.of(List.of(0)), rows("SELECT COUNT(*) FROM TON.PRODOTTI"));
    }

    @Test
    void clientRejectsVersionTwoInReadOnlyModeWithoutCreatingTonTables() {
        SchemaV2Fixture.create(connections);
        connections.withConnection(connection -> {
            connection.setReadOnly(true);
            SQLException failure = assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            assertTrue(failure.getMessage().contains("aggiornare e riavviare l'HOST"));
            assertFalse(tableExists(connection, "TON", "PRODOTTI"));
            return null;
        });
    }

    @Test
    void migrationDoesNotAdoptOrEraseAnUntrackedTonCatalogue() {
        SchemaV2Fixture.create(connections);
        execute("CREATE SCHEMA TON", "CREATE TABLE TON.PRODOTTI(note VARCHAR(100))", "INSERT INTO TON.PRODOTTI VALUES ('KEEP')");
        assertThrows(RuntimeException.class, this::migrate);
        assertEquals(List.of(List.of("KEEP")), rows("SELECT note FROM TON.PRODOTTI"));
        assertEquals(List.of(List.of(1), List.of(2)), rows("SELECT version FROM ECOS.ECOS_SCHEMA_HISTORY ORDER BY version"));
    }

    @Test
    void currentVersionChecksTheTechnicalCatalogueStructure() {
        migrate();
        execute("ALTER TABLE TON.PRODOTTI DROP COLUMN attivo");
        assertThrows(RuntimeException.class, this::migrate);
        connections.withConnection(connection -> {
            assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            return null;
        });
    }

    @Test
    void initializesDomainSchemasAndRecordsEachMigrationExactlyOnce() {
        migrate();
        migrate();
        assertEquals(List.of(List.of(1), List.of(2), List.of(3)), rows("SELECT version FROM ECOS.ECOS_SCHEMA_HISTORY ORDER BY version"));
        connections.withConnection(connection -> {
            DatabaseSchema.verifyCompatible(connection);
            assertTrue(tableExists(connection, "ANAGRAFICHE", "CLIENTI"));
            assertTrue(tableExists(connection, "VENDITE", "VENDITE_DETTAGLIO"));
            assertTrue(tableExists(connection, "TON", "PRODOTTI"));
            assertLegacyTablesAbsent(connection);
            try (var schemas = connection.getMetaData().getSchemas()) {
                while (schemas.next()) {
                    assertNotEquals("STATISTICHE", schemas.getString("TABLE_SCHEM"));
                }
            }
            return null;
        });
    }

    @Test
    void migratesUntrackedLegacyDataWithoutChangingAnyValuesOrIdentifiers() throws Exception {
        createLegacySchema(UnaryOperator.identity());
        seedLegacyData();
        List<List<Object>> customers = rows("SELECT * FROM APP.CLIENTI");
        List<List<Object>> sales = rows("SELECT * FROM APP.VENDITE_DETTAGLIO ORDER BY vendita_id");
        migrate();
        assertEquals(customers, rows("SELECT * FROM ANAGRAFICHE.CLIENTI"));
        assertEquals(sales, rows("SELECT * FROM VENDITE.VENDITE_DETTAGLIO ORDER BY vendita_id"));
        execute("INSERT INTO VENDITE.VENDITE_DETTAGLIO (sorgente,documento_id,numero_riga,data_vendita,codice_cliente,codice_prodotto,quantita) "
                + "VALUES ('TEST','NEW',1,DATE('2025-01-02'),'OLD','P',1)");
        assertEquals(List.of(List.of(100L)), rows("SELECT vendita_id FROM VENDITE.VENDITE_DETTAGLIO WHERE documento_id='NEW'"));
        connections.withConnection(connection -> {
            assertLegacyTablesAbsent(connection);
            DatabaseSchema.verifyCompatible(connection);
            return null;
        });
    }

    @Test
    void preservesTheRecordedBaselineAndItsTimestamp() throws Exception {
        createTrackedLegacySchema();
        seedLegacyData();
        List<List<Object>> history = rows("SELECT * FROM APP.ECOS_SCHEMA_HISTORY");
        migrate();
        assertEquals(history, rows("SELECT * FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=1"));
        assertEquals(DatabaseSchema.CURRENT_VERSION, rows("SELECT version FROM ECOS.ECOS_SCHEMA_HISTORY").size());
    }

    @Test
    void clientRejectsLegacyHostWithoutCreatingOrMigratingAnything() throws Exception {
        createLegacySchema(UnaryOperator.identity());
        connections.withConnection(connection -> {
            connection.setReadOnly(true);
            SQLException failure = assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            assertEquals("XEC01", failure.getSQLState());
            assertTrue(failure.getMessage().contains("aggiornare e riavviare l'HOST"));
            assertFalse(tableExists(connection, "ECOS", "ECOS_SCHEMA_HISTORY"));
            assertTrue(tableExists(connection, "APP", "CLIENTI"));
            return null;
        });
    }

    @Test
    void clientChecksCurrentSchemaOnAReadOnlyConnection() {
        migrate();
        List<List<Object>> history = rows("SELECT * FROM ECOS.ECOS_SCHEMA_HISTORY ORDER BY version");
        connections.withConnection(connection -> {
            connection.setReadOnly(true);
            DatabaseSchema.verifyCompatible(connection);
            return null;
        });
        assertEquals(history, rows("SELECT * FROM ECOS.ECOS_SCHEMA_HISTORY ORDER BY version"));
    }

    @Test
    void rejectsFutureSchemaForBothHostAndClient() {
        migrate();
        execute("UPDATE ECOS.ECOS_SCHEMA_HISTORY SET version=" + (DatabaseSchema.CURRENT_VERSION + 1)
                + " WHERE version=" + DatabaseSchema.CURRENT_VERSION);
        assertThrows(RuntimeException.class, this::migrate);
        connections.withConnection(connection -> {
            SQLException failure = assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            assertEquals("XEC01", failure.getSQLState());
            return null;
        });
        assertEquals(List.of(List.of(1), List.of(2), List.of(DatabaseSchema.CURRENT_VERSION + 1)),
                rows("SELECT version FROM ECOS.ECOS_SCHEMA_HISTORY ORDER BY version"));
    }

    @Test
    void rejectsModifiedMigrationHistory() {
        migrate();
        execute("UPDATE ECOS.ECOS_SCHEMA_HISTORY SET checksum='" + "0".repeat(64) + "' WHERE version=1");
        assertThrows(RuntimeException.class, this::migrate);
    }

    @Test
    void rejectsMissingMigrationHistory() {
        migrate();
        execute("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=1");
        assertThrows(RuntimeException.class, this::migrate);
        connections.withConnection(connection -> {
            assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            return null;
        });
    }

    @Test
    void rejectsAlteredLegacyHistoryWithoutCreatingDomainTables() throws Exception {
        createTrackedLegacySchema();
        execute("UPDATE APP.ECOS_SCHEMA_HISTORY SET checksum='" + "0".repeat(64) + "'");
        assertThrows(RuntimeException.class, this::migrate);
        connections.withConnection(connection -> {
            assertTrue(tableExists(connection, "APP", "CLIENTI"));
            assertFalse(tableExists(connection, "ANAGRAFICHE", "CLIENTI"));
            return null;
        });
    }

    @Test
    void rollsBackNewTablesWhenAnExistingLegacyTableIsIncompatible() {
        execute("CREATE TABLE APP.CLIENTI(codice_cliente INTEGER)");
        assertThrows(RuntimeException.class, this::migrate);
        connections.withConnection(connection -> {
            assertTrue(tableExists(connection, "APP", "CLIENTI"));
            assertFalse(tableExists(connection, "APP", "VENDITE_DETTAGLIO"));
            assertFalse(tableExists(connection, "APP", "ECOS_SCHEMA_HISTORY"));
            assertFalse(tableExists(connection, "ECOS", "ECOS_SCHEMA_HISTORY"));
            return null;
        });
    }

    @Test
    void rollsBackCopiedDataAndDroppedTablesWhenALegacyDependencyBlocksTheMove() throws Exception {
        createTrackedLegacySchema();
        seedLegacyData();
        execute("CREATE VIEW APP.CLIENTI_EXPORT AS SELECT codice_cliente FROM APP.CLIENTI");
        List<List<Object>> before = rows("SELECT * FROM APP.VENDITE_DETTAGLIO");
        assertThrows(RuntimeException.class, this::migrate);
        assertEquals(before, rows("SELECT * FROM APP.VENDITE_DETTAGLIO"));
        assertEquals(List.of(List.of(1)), rows("SELECT version FROM APP.ECOS_SCHEMA_HISTORY"));
        connections.withConnection(connection -> {
            assertFalse(tableExists(connection, "ECOS", "ECOS_SCHEMA_HISTORY"));
            assertFalse(tableExists(connection, "ANAGRAFICHE", "CLIENTI"));
            assertFalse(tableExists(connection, "VENDITE", "VENDITE_DETTAGLIO"));
            return null;
        });
        execute("DROP VIEW APP.CLIENTI_EXPORT");
        migrate();
        assertEquals(before, rows("SELECT * FROM VENDITE.VENDITE_DETTAGLIO"));
    }

    @Test
    void refusesToMergeIntoPreexistingDomainTables() throws Exception {
        createLegacySchema(UnaryOperator.identity());
        seedLegacyData();
        execute("CREATE SCHEMA ANAGRAFICHE", "CREATE TABLE ANAGRAFICHE.CLIENTI(code VARCHAR(50))",
                "INSERT INTO ANAGRAFICHE.CLIENTI VALUES ('KEEP')");
        assertThrows(RuntimeException.class, this::migrate);
        assertEquals(List.of(List.of("KEEP")), rows("SELECT code FROM ANAGRAFICHE.CLIENTI"));
        assertEquals(List.of(List.of("OLD")), rows("SELECT codice_cliente FROM APP.CLIENTI"));
    }

    @Test
    void preservesLegacyDataIfItCannotFitInTheDestination() throws Exception {
        createLegacySchema(script -> script.replace("ragione_sociale VARCHAR(500)", "ragione_sociale VARCHAR(600)"));
        execute("INSERT INTO APP.CLIENTI(codice_cliente,ragione_sociale) VALUES ('LONG','" + "a".repeat(501) + "')");
        assertThrows(RuntimeException.class, this::migrate);
        assertEquals(List.of(List.of(501)), rows("SELECT LENGTH(ragione_sociale) FROM APP.CLIENTI"));
        connections.withConnection(connection -> {
            assertFalse(tableExists(connection, "ANAGRAFICHE", "CLIENTI"));
            return null;
        });
    }

    @Test
    void refusesToDiscardUnrecognizedLegacyColumns() throws Exception {
        createLegacySchema(UnaryOperator.identity());
        seedLegacyData();
        execute("ALTER TABLE APP.CLIENTI ADD COLUMN note_personali VARCHAR(200)",
                "UPDATE APP.CLIENTI SET note_personali='Da conservare' WHERE codice_cliente='OLD'");
        assertThrows(RuntimeException.class, this::migrate);
        assertEquals(List.of(List.of("Da conservare")), rows("SELECT note_personali FROM APP.CLIENTI"));
        connections.withConnection(connection -> {
            assertFalse(tableExists(connection, "ANAGRAFICHE", "CLIENTI"));
            assertTrue(tableExists(connection, "APP", "VENDITE_DETTAGLIO"));
            return null;
        });
    }

    @Test
    void rejectsChangedColumnsEvenWhenMigrationVersionIsCurrent() {
        migrate();
        execute("ALTER TABLE ANAGRAFICHE.CLIENTI DROP COLUMN codice_iso");
        connections.withConnection(connection -> {
            assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            return null;
        });
    }

    @Test
    void rejectsLegacySchemaWithoutTheGeneratedSalesIdentity() throws Exception {
        createLegacySchema(script -> script.replace("GENERATED ALWAYS AS IDENTITY", ""));
        assertThrows(RuntimeException.class, this::migrate);
    }

    @Test
    void rejectsLegacySchemaWithoutImportTimestampDefaults() throws Exception {
        createLegacySchema(script -> script.replace("DEFAULT CURRENT_TIMESTAMP", ""));
        assertThrows(RuntimeException.class, this::migrate);
    }

    @Test
    void repositoriesUseExplicitDomainsEvenWhenTheConnectionDefaultIsDifferent() {
        migrate();
        connections.withConnection(connection -> {
            connection.setSchema("ECOS");
            assertTrue(new com.orodent.ecos.database.repository.impl.ClienteRepositoryImpl(connection).findAll().isEmpty());
            assertTrue(new com.orodent.ecos.database.repository.impl.VenditaDettaglioRepositoryImpl(connection).findAll().isEmpty());
            return null;
        });
    }

    @Test
    void legacyNamesAreAliasesAndAnOldBootstrapCannotCreateAnEmptyParallelDatabase() throws Exception {
        createLegacySchema(UnaryOperator.identity());
        seedLegacyData();
        migrate();
        assertEquals(rows("SELECT * FROM ANAGRAFICHE.CLIENTI"), rows("SELECT * FROM APP.CLIENTI"));
        SchemaMigration oldBootstrap = new SchemaMigration(1, "Legacy", "/database/schema.sql");
        assertThrows(RuntimeException.class, () -> connections.withTransaction(connection -> {
            oldBootstrap.apply(connection, true);
            return null;
        }));
        connections.withConnection(connection -> {
            assertLegacyTablesAbsent(connection);
            DatabaseSchema.verifyCompatible(connection);
            return null;
        });
        assertEquals(List.of(List.of("OLD")), rows("SELECT codice_cliente FROM ANAGRAFICHE.CLIENTI"));
    }

    private void migrate() { new DatabaseSchema(connections).initialize(); }

    private void seedLegacyData() {
        execute("INSERT INTO APP.CLIENTI(codice_cliente,codice_iso,categoria,listino,agente,tipo_cliente,ragione_sociale,importato_il) "
                        + "VALUES ('OLD','IT','Laboratorio','2025',NULL,'Nazionale','Cliente già esistente',TIMESTAMP('2024-02-03 04:05:06.123456789'))",
                "ALTER TABLE APP.VENDITE_DETTAGLIO ALTER COLUMN vendita_id RESTART WITH 42",
                "INSERT INTO APP.VENDITE_DETTAGLIO(sorgente,documento_id,numero_riga,data_vendita,codice_cliente,codice_prodotto,quantita,importo_netto,importato_il) "
                        + "VALUES ('TEST','OLD',1,DATE('2024-01-01'),'OLD','P',1.125,123.45,TIMESTAMP('2024-01-01 12:34:56.987654321'))",
                "ALTER TABLE APP.VENDITE_DETTAGLIO ALTER COLUMN vendita_id RESTART WITH 99",
                "INSERT INTO APP.VENDITE_DETTAGLIO(sorgente,documento_id,numero_riga,data_vendita,codice_cliente,codice_prodotto,quantita) "
                        + "VALUES ('TEST','DELETED',1,DATE('2024-01-01'),'OLD','P',1)",
                "DELETE FROM APP.VENDITE_DETTAGLIO WHERE vendita_id=99");
    }

    private void createLegacySchema(UnaryOperator<String> transform) throws Exception {
        try (var input = DatabaseSchemaTest.class.getResourceAsStream("/database/schema.sql")) {
            assertNotNull(input);
            String script = transform.apply(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            execute(script.split(";"));
        }
    }

    private void createTrackedLegacySchema() throws Exception {
        createLegacySchema(UnaryOperator.identity());
        execute("CREATE TABLE APP.ECOS_SCHEMA_HISTORY(version INTEGER PRIMARY KEY, description VARCHAR(200) NOT NULL, "
                + "checksum CHAR(64) NOT NULL, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        SchemaMigration baseline = new SchemaMigration(1, "Shared customers and sales baseline", "/database/schema.sql");
        execute("INSERT INTO APP.ECOS_SCHEMA_HISTORY(version,description,checksum) VALUES (1,'"
                + baseline.description() + "','" + baseline.checksum() + "')");
    }

    private void execute(String... statements) {
        connections.withConnection(connection -> {
            try (var statement = connection.createStatement()) {
                for (String sql : statements) if (!sql.isBlank()) statement.executeUpdate(sql);
            }
            return null;
        });
    }

    private List<List<Object>> rows(String sql) {
        return connections.withConnection(connection -> {
            try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
                List<List<Object>> rows = new ArrayList<>();
                while (result.next()) {
                    List<Object> values = new ArrayList<>();
                    for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) values.add(result.getObject(column));
                    rows.add(values);
                }
                return rows;
            }
        });
    }

    private static void assertLegacyTablesAbsent(Connection connection) throws SQLException {
        for (String name : List.of("CLIENTI", "VENDITE_DETTAGLIO", "ECOS_SCHEMA_HISTORY")) {
            assertFalse(tableExists(connection, "APP", name), name);
        }
    }

    private static boolean tableExists(Connection connection, String schema, String name) throws SQLException {
        try (var tables = connection.getMetaData().getTables(null, schema, name, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}

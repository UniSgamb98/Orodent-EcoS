package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ScenarioSchemaTest {
    ConnectionProvider db;
    String url, old;

    @BeforeEach
    void open() throws Exception {
        old = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/scenarios-schema.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:scenario_schema_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        db.withTransaction(
                c -> {
                    var q = c.createStatement();
                    q.executeUpdate("DROP TABLE COSTI.SCENARI_REVISIONI");
                    q.executeUpdate("DROP TABLE COSTI.SCENARI");
                    q.executeUpdate("DROP TABLE COSTI.SCENARI_BASI");
                    q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=11");
                    q.executeUpdate(
                            "INSERT INTO TON.POLVERI(codice,nome) VALUES ('KEEP','Conservata')");
                    return null;
                });
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException ignored) {
        }
        if (old == null) System.clearProperty("derby.stream.error.file");
        else System.setProperty("derby.stream.error.file", old);
    }

    int version() {
        return db.withConnection(
                c -> {
                    var r =
                            c.createStatement()
                                    .executeQuery(
                                            "SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY");
                    r.next();
                    return r.getInt(1);
                });
    }

    @Test
    void upgradesV10AfterBackupOnceAndClientsOnlyCheck() {
        var calls = new AtomicInteger();
        db.withConnection(
                c -> {
                    c.setReadOnly(true);
                    assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(c));
                    return null;
                });
        assertEquals(10, version());
        new DatabaseSchema(db)
                .initialize(
                        c -> {
                            assertEquals(10, version());
                            calls.incrementAndGet();
                            return null;
                        });
        assertEquals(11, version());
        new DatabaseSchema(db)
                .initialize(
                        c -> {
                            calls.incrementAndGet();
                            return null;
                        });
        assertEquals(1, calls.get());
        db.withConnection(
                c -> {
                    c.setReadOnly(true);
                    DatabaseSchema.verifyCompatible(c);
                    var r = c.createStatement().executeQuery("SELECT codice FROM TON.POLVERI");
                    assertTrue(r.next());
                    assertEquals("KEEP", r.getString(1));
                    return null;
                });
    }

    @Test
    void failedMigrationRollsBackNewTablesAndHistory() {
        db.withTransaction(
                c -> {
                    c.createStatement().executeUpdate("CREATE TABLE COSTI.SCENARI (id INTEGER)");
                    return null;
                });
        assertThrows(RuntimeException.class, () -> new DatabaseSchema(db).initialize(c -> null));
        assertEquals(10, version());
        db.withConnection(
                c -> {
                    try (var r = c.getMetaData().getTables(null, "COSTI", "SCENARI_BASI", null)) {
                        assertFalse(r.next());
                    }
                    return null;
                });
    }

    @Test
    void missingScenarioColumnFailsReadOnlyCompatibilityCheck() {
        new DatabaseSchema(db).initialize(c -> null);
        db.withTransaction(
                c -> {
                    c.createStatement()
                            .executeUpdate(
                                    "ALTER TABLE COSTI.SCENARI_REVISIONI DROP COLUMN note"
                                            + " RESTRICT");
                    return null;
                });
        db.withConnection(
                c -> {
                    c.setReadOnly(true);
                    var e =
                            assertThrows(
                                    SQLException.class, () -> DatabaseSchema.verifyCompatible(c));
                    assertEquals("XEC01", e.getSQLState());
                    return null;
                });
    }
}

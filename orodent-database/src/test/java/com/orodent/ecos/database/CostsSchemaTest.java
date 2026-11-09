package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class CostsSchemaTest {
    private ConnectionProvider db;
    private String url, oldLog;

    @BeforeEach
    void open() throws Exception {
        oldLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/costs-schema.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:costs_schema_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        SchemaV5Fixture.create(db);
        db.withTransaction(
                c -> {
                    var v6 =
                            new SchemaMigration(
                                    6,
                                    "TON catalogue hierarchy and history",
                                    "/database/migrations/V6__ton_catalogue_history.sql");
                    v6.apply(c, false);
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " ECOS.ECOS_SCHEMA_HISTORY(version,description,checksum)"
                                        + " VALUES (6,?,?)")) {
                        q.setString(1, v6.description());
                        q.setString(2, v6.checksum());
                        q.executeUpdate();
                    }
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO TON.POLVERI(codice,nome,colore,versione_modifica)"
                                    + " VALUES ('KEEP','Bianca','BIANCA',5)");
                    }
                    return null;
                });
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException expected) {
            assertEquals("08006", expected.getSQLState());
        }
        if (oldLog == null) System.clearProperty("derby.stream.error.file");
        else System.setProperty("derby.stream.error.file", oldLog);
    }

    @Test
    void backsUpBeforeV7AndKeepsTonAndHistoricalChecksums() {
        var before =
                db.withConnection(
                        c -> {
                            try (var q = c.createStatement();
                                    var r =
                                            q.executeQuery(
                                                    "SELECT checksum FROM ECOS.ECOS_SCHEMA_HISTORY"
                                                        + " WHERE version=6")) {
                                r.next();
                                return r.getString(1);
                            }
                        });
        var backups = new AtomicInteger();
        new DatabaseSchema(db)
                .initialize(
                        c -> {
                            assertEquals(
                                    6, scalar("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
                            backups.incrementAndGet();
                            return null;
                        });
        new DatabaseSchema(db)
                .initialize(
                        c -> {
                            fail("Secondo backup non necessario");
                            return null;
                        });
        assertEquals(1, backups.get());
        assertEquals(DatabaseSchema.CURRENT_VERSION, scalar("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
        assertEquals(5, scalar("SELECT versione_modifica FROM TON.POLVERI WHERE polvere_id=1"));
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT checksum FROM ECOS.ECOS_SCHEMA_HISTORY WHERE"
                                                + " version=6")) {
                        r.next();
                        assertEquals(before, r.getString(1));
                    }
                    return null;
                });
        assertEquals(0, scalar("SELECT COUNT(*) FROM COSTI.POLVERI_PREZZI"));
    }

    @Test
    void clientChecksDoNotMigrateAndDetectBrokenCostTables() {
        db.withConnection(
                c -> {
                    c.setReadOnly(true);
                    var error =
                            assertThrows(
                                    SQLException.class, () -> DatabaseSchema.verifyCompatible(c));
                    assertEquals("XEC01", error.getSQLState());
                    return null;
                });
        assertEquals(6, scalar("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
        new DatabaseSchema(db).initialize();
        db.withConnection(
                c -> {
                    c.setReadOnly(true);
                    DatabaseSchema.verifyCompatible(c);
                    return null;
                });
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate("DROP TABLE COSTI.POLVERI_LOTTI");
                    }
                    return null;
                });
        db.withConnection(
                c -> {
                    assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(c));
                    return null;
                });
    }

    private int scalar(String sql) {
        return db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery(sql)) {
                        r.next();
                        return r.getInt(1);
                    }
                });
    }
}

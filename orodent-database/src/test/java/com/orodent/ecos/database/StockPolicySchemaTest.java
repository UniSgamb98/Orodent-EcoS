package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class StockPolicySchemaTest {
    private ConnectionProvider db;
    private String url, oldLog;

    @BeforeEach
    void open() throws Exception {
        oldLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/stock-schema.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:stock_schema_" + UUID.randomUUID().toString().replace("-", "");
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
                    for (var migration :
                            List.of(
                                    new SchemaMigration(
                                            6,
                                            "TON catalogue hierarchy and history",
                                            "/database/migrations/V6__ton_catalogue_history.sql"),
                                    new SchemaMigration(
                                            7,
                                            "Costs manual powder prices and lot snapshots",
                                            "/database/migrations/V7__costs_powder_prices.sql"),
                                    new SchemaMigration(
                                            8,
                                            "Costs product transformation parameters",
                                            "/database/migrations/V8__costs_product_parameters.sql"))) {
                        migration.apply(c, false);
                        try (var q =
                                c.prepareStatement(
                                        "INSERT INTO"
                                            + " ECOS.ECOS_SCHEMA_HISTORY(version,description,checksum)"
                                            + " VALUES (?,?,?)")) {
                            q.setInt(1, migration.version());
                            q.setString(2, migration.description());
                            q.setString(3, migration.checksum());
                            q.executeUpdate();
                        }
                    }
                    return null;
                });
    }

    @AfterEach
    void close() throws Exception {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
        if (oldLog == null) System.clearProperty("derby.stream.error.file");
        else System.setProperty("derby.stream.error.file", oldLog);
    }

    @Test
    void warehouseUpgradeAppendsBaseTotalsWithoutChangingLegacySnapshots() {
        db.withTransaction(
                c -> {
                    var q = c.createStatement();
                    q.executeUpdate("INSERT INTO TON.POLVERI(codice,nome) VALUES ('P','Bianca')");
                    q.executeUpdate(
                            "INSERT INTO"
                                + " COSTI.POLVERI_PREZZI(polvere_id,numero_versione,modalita,data_riferimento,prezzo_semplice,quantita_semplice)"
                                + " VALUES (1,1,'AVANZATA',DATE('2026-11-01'),92,999)");
                    q.executeUpdate(
                            "INSERT INTO"
                                + " COSTI.POLVERI_LOTTI(prezzo_id,posizione,lotto,quantita_kg,prezzo_kg)"
                                + " VALUES (1,1,'A',100,80),(1,2,'B',50,100)");
                    return null;
                });
        new DatabaseSchema(db).initialize(c -> null);
        db.withConnection(
                c -> {
                    var q = c.createStatement();
                    var r =
                            q.executeQuery(
                                    "SELECT"
                                        + " numero_versione,prezzo_semplice,quantita_semplice,modello_dati,prezzo_riferimento,quantita_totale"
                                        + " FROM COSTI.POLVERI_PREZZI ORDER BY numero_versione");
                    assertTrue(r.next());
                    assertEquals(1, r.getInt(1));
                    assertEquals(0, new java.math.BigDecimal("92").compareTo(r.getBigDecimal(2)));
                    assertEquals(0, new java.math.BigDecimal("999").compareTo(r.getBigDecimal(3)));
                    assertEquals(1, r.getInt(4));
                    assertTrue(r.next());
                    assertEquals(2, r.getInt(1));
                    assertEquals(2, r.getInt(4));
                    assertEquals(new java.math.BigDecimal("86.66666667"), r.getBigDecimal(5));
                    assertEquals(0, new java.math.BigDecimal("150").compareTo(r.getBigDecimal(6)));
                    assertFalse(r.next());
                    var lots = q.executeQuery("SELECT COUNT(*) FROM COSTI.POLVERI_LOTTI");
                    lots.next();
                    assertEquals(4, lots.getInt(1));
                    return null;
                });
        new DatabaseSchema(db).initialize();
        db.withConnection(
                c -> {
                    c.setReadOnly(true);
                    DatabaseSchema.verifyCompatible(c);
                    return null;
                });
    }

    @Test
    void migratesV8AfterBackupOnceAndClientChecksAreReadOnly() {
        db.withConnection(
                c -> {
                    c.setReadOnly(true);
                    assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(c));
                    return null;
                });
        assertEquals(8, version());
        var backups = new AtomicInteger();
        new DatabaseSchema(db)
                .initialize(
                        c -> {
                            assertEquals(8, version());
                            backups.incrementAndGet();
                            return null;
                        });
        assertEquals(1, backups.get());
        assertEquals(DatabaseSchema.CURRENT_VERSION, version());
        new DatabaseSchema(db)
                .initialize(
                        c -> {
                            fail("Backup ripetuto");
                            return null;
                        });
        db.withConnection(
                c -> {
                    c.setReadOnly(true);
                    DatabaseSchema.verifyCompatible(c);
                    return null;
                });
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate("DROP TABLE COSTI.SCORTA_MINIMA");
                    }
                    return null;
                });
        db.withConnection(
                c -> {
                    c.setReadOnly(true);
                    assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(c));
                    return null;
                });
    }

    private int version() {
        return db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY")) {
                        r.next();
                        return r.getInt(1);
                    }
                });
    }
}

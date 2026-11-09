package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class ProductCostsSchemaTest {
    private ConnectionProvider db;
    private String url, oldLog;

    @BeforeEach
    void open() throws Exception {
        oldLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/product-costs-schema.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url =
                "jdbc:derby:memory:product_costs_schema_"
                        + UUID.randomUUID().toString().replace("-", "");
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
                            new SchemaMigration[] {
                                new SchemaMigration(
                                        6,
                                        "TON catalogue hierarchy and history",
                                        "/database/migrations/V6__ton_catalogue_history.sql"),
                                new SchemaMigration(
                                        7,
                                        "Costs manual powder prices and lot snapshots",
                                        "/database/migrations/V7__costs_powder_prices.sql")
                            }) {
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
                    try (var q = c.createStatement()) {
                        q.executeUpdate("INSERT INTO TON.PRODOTTI(codice) VALUES ('KEEP')");
                        q.executeUpdate(
                                "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES"
                                        + " ('WHITE','Bianca','BIANCA')");
                        q.executeUpdate(
                                "INSERT INTO"
                                    + " COSTI.POLVERI_PREZZI(polvere_id,numero_versione,data_riferimento,modalita,prezzo_semplice)"
                                    + " VALUES (1,1,DATE('2026-11-09'),'SEMPLICE',92.125)");
                    }
                    return null;
                });
    }

    @AfterEach
    void close() throws Exception {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException expected) {
            assertEquals("08006", expected.getSQLState());
        }
        if (oldLog == null) System.clearProperty("derby.stream.error.file");
        else System.setProperty("derby.stream.error.file", oldLog);
    }

    @Test
    void backsUpBeforeV8PreservesPricesAndAppliesOnlyOnce() {
        String checksum = string("SELECT checksum FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=7");
        var backups = new AtomicInteger();
        new DatabaseSchema(db)
                .initialize(
                        c -> {
                            assertEquals(
                                    "7",
                                    string("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
                            backups.incrementAndGet();
                            return null;
                        });
        new DatabaseSchema(db)
                .initialize(
                        c -> {
                            fail("Backup non necessario");
                            return null;
                        });
        assertEquals(1, backups.get());
        assertEquals("8", string("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
        assertEquals(
                checksum, string("SELECT checksum FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=7"));
        assertEquals("92.1250", string("SELECT prezzo_semplice FROM COSTI.POLVERI_PREZZI"));
        assertEquals("0", string("SELECT COUNT(*) FROM COSTI.PRODOTTI_PARAMETRI"));
        db.withConnection(
                c -> {
                    c.setReadOnly(true);
                    DatabaseSchema.verifyCompatible(c);
                    return null;
                });
    }

    @Test
    void clientDoesNotMigrateV7AndVerifiesV8ColumnsReadOnly() {
        db.withConnection(
                c -> {
                    c.setReadOnly(true);
                    assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(c));
                    return null;
                });
        assertEquals("7", string("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
        new DatabaseSchema(db).initialize();
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "ALTER TABLE COSTI.PRODOTTI_PARAMETRI DROP COLUMN"
                                        + " trasformazione_eur");
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

    private String string(String sql) {
        return db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery(sql)) {
                        r.next();
                        return r.getString(1);
                    }
                });
    }
}

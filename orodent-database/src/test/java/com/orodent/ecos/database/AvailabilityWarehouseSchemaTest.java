package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

class AvailabilityWarehouseSchemaTest {
    String url;
    ConnectionProvider db;

    @BeforeEach
    void setup() {
        url = "jdbc:derby:memory:warehouses_" + UUID.randomUUID().toString().replace("-", "");
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
                    AvailabilityWarehouseFixtures.beforeV25(c);
                    return null;
                });
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    long oldImport(Connection c, String kind, int value) throws SQLException {
        long id;
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.DISPONIBILITA_IMPORT(tipo,nome_file,data_riferimento,codici_assenti_zero,righe)"
                            + " VALUES (?,'precedente.csv',CURRENT_DATE,FALSE,1)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setString(1, kind);
            q.executeUpdate();
            try (var r = q.getGeneratedKeys()) {
                assertTrue(r.next());
                id = r.getLong(1);
            }
        }
        try (var q =
                c.prepareStatement(
                        "INSERT INTO TON.DISPONIBILITA_VALORI VALUES (?,'ARTICOLO',?)")) {
            q.setLong(1, id);
            q.setInt(2, value);
            q.executeUpdate();
        }
        try (var q =
                c.prepareStatement("UPDATE TON.DISPONIBILITA_FONTI SET import_id=? WHERE tipo=?")) {
            q.setLong(1, id);
            q.setString(2, kind);
            q.executeUpdate();
        }
        return id;
    }

    @Test
    void preservesLegacyQuantitiesAndSourcesWithoutInventingTheirDepot() {
        long stock = db.withTransaction(c -> oldImport(c, "DISPONIBILITA", 12));
        db.withTransaction(c -> oldImport(c, "ORDINI", 3));
        var backups = new java.util.concurrent.atomic.AtomicInteger();
        new DatabaseSchema(db)
                .initialize(
                        c -> {
                            backups.incrementAndGet();
                            return null;
                        });
        new DatabaseSchema(db).initialize();
        assertEquals(1, backups.get());
        db.withConnection(
                c -> {
                    DatabaseSchema.verifyCompatible(c);
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT"
                                                + " F.import_id,I.magazzini_distinti,V.quantita,V.magazzino"
                                                + " FROM TON.DISPONIBILITA_FONTI F JOIN"
                                                + " TON.DISPONIBILITA_IMPORT I ON"
                                                + " I.import_id=F.import_id JOIN"
                                                + " TON.DISPONIBILITA_VALORI V ON"
                                                + " V.import_id=I.import_id WHERE"
                                                + " F.tipo='DISPONIBILITA'")) {
                        assertTrue(r.next());
                        assertEquals(stock, r.getLong(1));
                        assertFalse(r.getBoolean(2));
                        assertEquals(12, r.getInt(3));
                        assertEquals("NON_SPECIFICATO", r.getString(4));
                    }
                    return null;
                });
    }

    @Test
    void keyDistinguishesWarehousesAndEnforcesTheirCatalogue() {
        long stock = db.withTransaction(c -> oldImport(c, "DISPONIBILITA", 12));
        new DatabaseSchema(db).initialize();
        db.withTransaction(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " TON.DISPONIBILITA_VALORI(import_id,codice,quantita,magazzino)"
                                        + " VALUES (?,'ARTICOLO',2,'MAGC')")) {
                        q.setLong(1, stock);
                        q.executeUpdate();
                    }
                    return null;
                });
        db.withConnection(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " TON.DISPONIBILITA_VALORI(import_id,codice,quantita,magazzino)"
                                        + " VALUES (?,'ARTICOLO',2,?)")) {
                        q.setLong(1, stock);
                        q.setString(2, "MAGC");
                        assertEquals(
                                "23505",
                                assertThrows(SQLException.class, q::executeUpdate).getSQLState());
                        q.setString(2, "INESISTENTE");
                        assertEquals(
                                "23503",
                                assertThrows(SQLException.class, q::executeUpdate).getSQLState());
                    }
                    return null;
                });
    }
}

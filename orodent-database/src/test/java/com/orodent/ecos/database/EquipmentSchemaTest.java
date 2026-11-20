package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class EquipmentSchemaTest {
    @Test
    void upgradesV17WithoutChangingExistingFurnacesAndVerifiesTheNewPhysicalMeasureColumns()
            throws Exception {
        String url =
                "jdbc:derby:memory:equipment_schema_"
                        + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        try {
            db.withTransaction(
                    c -> {
                        try (var q = c.createStatement()) {
                            q.executeUpdate(
                                    "INSERT INTO TON.FORNI(codice,nome,versione_modifica) VALUES"
                                            + " ('F01','Forno conservato',12)");
                            q.executeUpdate(
                                    "INSERT INTO TON.CICLI_COTTURA(numero,forno_id,data_prevista)"
                                            + " VALUES ('C01',1,CURRENT_DATE)");
                            q.executeUpdate("DROP TABLE TON.PRESSE");
                            q.executeUpdate("DROP TABLE TON.STAMPI");
                            q.executeUpdate(
                                    "DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE VERSION=18");
                        }
                        return null;
                    });
            AtomicInteger backups = new AtomicInteger();
            new DatabaseSchema(db)
                    .initialize(
                            c -> {
                                backups.incrementAndGet();
                                return null;
                            });
            new DatabaseSchema(db)
                    .initialize(
                            c -> {
                                backups.incrementAndGet();
                                return null;
                            });
            assertEquals(1, backups.get());
            db.withConnection(
                    c -> {
                        DatabaseSchema.verifyCompatible(c);
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT f.nome,f.versione_modifica,c.forno_id FROM"
                                                        + " TON.FORNI f JOIN TON.CICLI_COTTURA c ON"
                                                        + " c.forno_id=f.forno_id")) {
                            assertTrue(r.next());
                            assertEquals("Forno conservato", r.getString(1));
                            assertEquals(12, r.getInt(2));
                            assertEquals(1, r.getLong(3));
                            assertFalse(r.next());
                        }
                        for (String table : List.of("PRESSE", "STAMPI"))
                            try (var q = c.createStatement();
                                    var r = q.executeQuery("SELECT COUNT(*) FROM TON." + table)) {
                                r.next();
                                assertEquals(0, r.getInt(1));
                            }
                        try (var q = c.createStatement()) {
                            assertEquals(
                                    "23513",
                                    assertThrows(
                                                    SQLException.class,
                                                    () ->
                                                            q.executeUpdate(
                                                                    "INSERT INTO"
                                                                        + " TON.PRESSE(codice,nome,alesaggio_mm)"
                                                                        + " VALUES"
                                                                        + " ('P','Pressa',0)"))
                                            .getSQLState());
                            assertEquals(
                                    "23513",
                                    assertThrows(
                                                    SQLException.class,
                                                    () ->
                                                            q.executeUpdate(
                                                                    "INSERT INTO"
                                                                        + " TON.STAMPI(codice,nome,diametro_mm)"
                                                                        + " VALUES"
                                                                        + " ('S','Stampo',-1)"))
                                            .getSQLState());
                        }
                        return null;
                    });
            db.withTransaction(
                    c -> {
                        try (var q = c.createStatement()) {
                            q.executeUpdate(
                                    "ALTER TABLE TON.PRESSE DROP CONSTRAINT"
                                        + " ck_ton_presse_alesaggio");
                            q.executeUpdate(
                                    "ALTER TABLE TON.PRESSE DROP COLUMN alesaggio_mm RESTRICT");
                            q.executeUpdate(
                                    "ALTER TABLE TON.PRESSE ADD COLUMN alesaggio_mm DECIMAL(11,3)");
                        }
                        return null;
                    });
            var failure =
                    assertThrows(RuntimeException.class, () -> new DatabaseSchema(db).initialize());
            assertEquals(
                    DatabaseSchema.INCOMPATIBLE_SCHEMA,
                    ((SQLException) failure.getCause()).getSQLState());
        } finally {
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException expected) {
                assertEquals("08006", expected.getSQLState());
            }
        }
    }
}

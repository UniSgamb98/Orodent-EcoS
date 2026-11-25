package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.UUID;

class FiringLoadProgramSchemaTest {
    @Test
    void upgradeKeepsOldLoadsAndProgramsWithoutInventingMissingTemperatures() {
        String url =
                "jdbc:derby:memory:load_target_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        try {
            new DatabaseSchema(db).initialize();
            db.withTransaction(
                    c -> {
                        FiringProgramFixtures.beforeV32(c);
                        try (var q = c.createStatement()) {
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.PROGRAMMI_COTTURA(codice,numero_versione,nome,stato,approvato_il)"
                                        + " VALUES ('OLD',1,'Vecchio"
                                        + " programma','APPROVATA',CURRENT_TIMESTAMP)");
                            q.executeUpdate(
                                    "INSERT INTO TON.PROGRAMMA_PASSI VALUES (1,1,1450,120,60)");
                            q.executeUpdate(
                                    "INSERT INTO TON.FORNI(codice,nome) VALUES ('F1','Forno')");
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.CICLI_COTTURA(numero,forno_id,programma_id,data_prevista,caricato_il)"
                                        + " VALUES ('OLD',1,1,CURRENT_DATE,CURRENT_TIMESTAMP)");
                        }
                        return null;
                    });
            new DatabaseSchema(db).initialize();
            new DatabaseSchema(db).initialize();
            db.withTransaction(
                    c -> {
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT"
                                                    + " programma_id,temperatura_finale_c,programma_precedente_id"
                                                    + " FROM TON.CICLI_COTTURA")) {
                            assertTrue(r.next());
                            assertEquals(1, r.getLong(1));
                            assertNull(r.getObject(2));
                            assertNull(r.getObject(3));
                            assertFalse(r.next());
                        }
                        try (var q = c.createStatement()) {
                            q.executeUpdate(
                                    "INSERT INTO TON.PROGRAMMA_PASSI VALUES (1,2,NULL,90,30)");
                            assertThrows(
                                    SQLException.class,
                                    () ->
                                            q.executeUpdate(
                                                    "UPDATE TON.CICLI_COTTURA SET"
                                                        + " temperatura_finale_c=-1"));
                            assertThrows(
                                    SQLException.class,
                                    () ->
                                            q.executeUpdate(
                                                    "UPDATE TON.CICLI_COTTURA SET"
                                                        + " programma_precedente_id=1"));
                            q.executeUpdate(
                                    "UPDATE TON.CICLI_COTTURA SET"
                                        + " temperatura_finale_c=1440,programma_precedente_id=1");
                        }
                        DatabaseSchema.verifyCompatible(c);
                        return null;
                    });
        } finally {
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException e) {
                assertEquals("08006", e.getSQLState());
            }
        }
    }
}

package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ModelMouldSchemaTest {
    @Test
    void upgradesOldModelsWithoutGuessingMouldAssignmentsAndRetainsExactHistoricalValues()
            throws Exception {
        String url =
                "jdbc:derby:memory:model_mould_migration_"
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
                        ModelMouldFixtures.beforeV19(c);
                        try (var q = c.createStatement()) {
                            q.executeUpdate(
                                    "INSERT INTO TON.STAMPI(codice,nome,diametro_mm) VALUES"
                                            + " ('S99','Stampo reale',99.25)");
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.MODELLI_DISCO(codice,numero_versione,diametro_pressatura_mm,pressione_kg_cm2,grammi_per_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm,stato,approvato_il,versione_modifica,note)"
                                        + " VALUES"
                                        + " ('OLD',1,99.25,1500,25,1.5,0.5,'APPROVATA',CURRENT_TIMESTAMP,7,'Conservare')");
                            q.executeUpdate("INSERT INTO TON.MODELLO_STRATI VALUES (1,1,100)");
                        }
                        return null;
                    });
            AtomicInteger backup = new AtomicInteger();
            new DatabaseSchema(db)
                    .initialize(
                            c -> {
                                backup.incrementAndGet();
                                return null;
                            });
            new DatabaseSchema(db)
                    .initialize(
                            c -> {
                                backup.incrementAndGet();
                                return null;
                            });
            assertEquals(1, backup.get());
            db.withConnection(
                    c -> {
                        DatabaseSchema.verifyCompatible(c);
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT"
                                                    + " modello_id,diametro_pressatura_mm,stato,versione_modifica,note"
                                                    + " FROM TON.MODELLI_DISCO")) {
                            assertTrue(r.next());
                            assertEquals(1, r.getLong(1));
                            assertEquals(0, new BigDecimal("99.25").compareTo(r.getBigDecimal(2)));
                            assertEquals("APPROVATA", r.getString(3));
                            assertEquals(7, r.getInt(4));
                            assertEquals("Conservare", r.getString(5));
                            assertFalse(r.next());
                        }
                        for (String table : List.of("MODELLO_STAMPO_RIF", "MODELLO_STAMPI_AMMESSI"))
                            try (var q = c.createStatement();
                                    var r = q.executeQuery("SELECT COUNT(*) FROM TON." + table)) {
                                r.next();
                                assertEquals(0, r.getInt(1));
                            }
                        try (var r =
                                c.getMetaData()
                                        .getColumns(
                                                null,
                                                "TON",
                                                "MODELLI_DISCO",
                                                "DIAMETRO_PRESSATURA_MM")) {
                            assertTrue(r.next());
                            assertEquals(3, r.getInt("DECIMAL_DIGITS"));
                        }
                        return null;
                    });
        } finally {
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException expected) {
                assertEquals("08006", expected.getSQLState());
            }
        }
    }
}

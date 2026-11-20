package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class ModelDiameterSchemaTest {
    @Test
    void v20RetainsV19SnapshotsAndNeverInventsNominalArticleDiameters() throws Exception {
        String url =
                "jdbc:derby:memory:model_diameter_" + UUID.randomUUID().toString().replace("-", "");
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
                        ModelDiameterFixtures.beforeV20(c);
                        try (var q = c.createStatement()) {
                            q.executeUpdate(
                                    "INSERT INTO TON.STAMPI(codice,nome,diametro_mm) VALUES"
                                        + " ('REF','Riferimento',99.123),('ALT','Alternativo',99.2)");
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.MODELLI_DISCO(codice,numero_versione,diametro_pressatura_mm,pressione_kg_cm2,grammi_per_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm,stato,approvato_il,versione_modifica)"
                                        + " VALUES"
                                        + " ('OLD',1,99.123,1500,25.12345,1.5,0.5,'APPROVATA',CURRENT_TIMESTAMP,7)");
                            q.executeUpdate("INSERT INTO TON.MODELLO_STRATI VALUES (1,1,100)");
                            q.executeUpdate(
                                    "INSERT INTO TON.MODELLO_STAMPO_RIF VALUES"
                                        + " (1,1,1,'REF','Riferimento',99.123)");
                            q.executeUpdate(
                                    "INSERT INTO TON.MODELLO_STAMPI_AMMESSI VALUES"
                                        + " (1,2,1,'ALT','Alternativo',99.2)");
                        }
                        return null;
                    });
            var backups = new AtomicInteger();
            var schema = new DatabaseSchema(db);
            schema.initialize(
                    c -> {
                        backups.incrementAndGet();
                        return null;
                    });
            schema.initialize(
                    c -> {
                        backups.incrementAndGet();
                        return null;
                    });
            assertEquals(1, backups.get());
            db.withConnection(
                    c -> {
                        DatabaseSchema.verifyCompatible(c);
                        try (var q = c.createStatement()) {
                            try (var r =
                                    q.executeQuery(
                                            "SELECT"
                                                + " diametro_pressatura_mm,grammi_per_mm,stato,versione_modifica"
                                                + " FROM TON.MODELLI_DISCO WHERE modello_id=1")) {
                                assertTrue(r.next());
                                assertEquals("99.123", r.getBigDecimal(1).toPlainString());
                                assertEquals("25.12345", r.getBigDecimal(2).toPlainString());
                                assertEquals("APPROVATA", r.getString(3));
                                assertEquals(7, r.getInt(4));
                            }
                            for (String table :
                                    new String[] {"MODELLO_STAMPO_RIF", "MODELLO_STAMPI_AMMESSI"})
                                try (var r = q.executeQuery("SELECT COUNT(*) FROM TON." + table)) {
                                    r.next();
                                    assertEquals(1, r.getInt(1));
                                }
                            for (String table :
                                    new String[] {"MODELLO_DIAMETRI", "MODELLO_DIAM_STAMPI"})
                                try (var r = q.executeQuery("SELECT COUNT(*) FROM TON." + table)) {
                                    r.next();
                                    assertEquals(0, r.getInt(1));
                                }
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

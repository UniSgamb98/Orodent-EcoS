package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ProductSpecificationMigrationTest {
    String url;
    ConnectionProvider db;

    @BeforeEach
    void open() {
        url = "jdbc:derby:memory:product_specs_" + UUID.randomUUID().toString().replace("-", "");
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
                    ProductSpecificationFixtures.beforeV21(c);
                    seed(c);
                    return null;
                });
    }

    @AfterEach
    void close() throws SQLException {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    @Test
    void deduplicatesIdenticalSpecificationsPreservesConflictsAndNeverChangesPreparedPlans() {
        var backups = new AtomicInteger();
        var schema = new DatabaseSchema(db);
        schema.initialize(
                c -> {
                    backups.incrementAndGet();
                    return null;
                });
        schema.initialize();
        assertEquals(1, backups.get());
        db.withConnection(
                c -> {
                    DatabaseSchema.verifyCompatible(c);
                    assertEquals(4, scalar(c, "SELECT COUNT(*) FROM TON.CONFIGURAZIONI_PRODOTTO"));
                    assertEquals(5, scalar(c, "SELECT COUNT(*) FROM TON.CONFIGURAZIONI_MIGRATE"));
                    assertEquals(
                            5,
                            scalar(
                                    c,
                                    "SELECT COUNT(*) FROM TON.CONFIGURAZIONI_ARTICOLO WHERE"
                                            + " versione_modifica=7"));
                    assertEquals(
                            1,
                            scalar(
                                    c,
                                    "SELECT COUNT(*) FROM TON.PRODOTTI WHERE prodotto_id=1 AND"
                                            + " configurazione_predefinita_id IS NOT NULL AND"
                                            + " specifiche_da_verificare=FALSE"));
                    assertEquals(
                            2,
                            scalar(
                                    c,
                                    "SELECT COUNT(*) FROM TON.PRODOTTI WHERE prodotto_id IN (2,3)"
                                            + " AND configurazione_predefinita_id IS NULL AND"
                                            + " specifiche_da_verificare=TRUE"));
                    assertEquals(
                            1,
                            scalar(
                                    c,
                                    "SELECT COUNT(DISTINCT prodotto_configurazione_id) FROM"
                                            + " TON.CONFIGURAZIONI_MIGRATE WHERE"
                                            + " articolo_configurazione_id IN (1,2)"));
                    assertEquals(
                            2,
                            scalar(
                                    c,
                                    "SELECT COUNT(DISTINCT prodotto_configurazione_id) FROM"
                                            + " TON.CONFIGURAZIONI_MIGRATE WHERE"
                                            + " articolo_configurazione_id IN (3,4)"));
                    assertEquals(
                            3,
                            scalar(
                                    c,
                                    "SELECT COUNT(*) FROM TON.PRODOTTI_REVISIONI WHERE"
                                            + " numero_revisione=2 AND origine='MIGRAZIONE'"));
                    assertEquals(
                            5,
                            scalar(
                                    c,
                                    "SELECT COUNT(*) FROM TON.ARTICOLI WHERE"
                                            + " configurazione_predefinita_id IS NOT NULL AND"
                                            + " versione_modifica=1"));
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT contenuto_piano,stato,versione_modifica FROM"
                                                    + " TON.PIANI_PRODUZIONE")) {
                        r.next();
                        assertEquals("immutable legacy snapshot", r.getString(1));
                        assertEquals("PREPARATA", r.getString(2));
                        assertEquals(4, r.getInt(3));
                    }
                    assertEquals(
                            1,
                            scalar(
                                    c,
                                    "SELECT COUNT(*) FROM TON.PIANO_PRODUZIONE_RIGHE WHERE"
                                            + " configurazione_id=1"));
                    try (var q = c.createStatement()) {
                        var error =
                                assertThrows(
                                        SQLException.class,
                                        () ->
                                                q.executeUpdate(
                                                        "UPDATE TON.PRODOTTI SET"
                                                            + " configurazione_predefinita_id=(SELECT"
                                                            + " configurazione_id FROM"
                                                            + " TON.CONFIGURAZIONI_PRODOTTO WHERE"
                                                            + " prodotto_id=1) WHERE"
                                                            + " prodotto_id=2"));
                        assertEquals("23503", error.getSQLState());
                    }
                    return null;
                });
    }

    @Test
    void failureRollsBackAllCopiedReferencesAndCatalogueUpdates() {
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO"
                                    + " TON.PRODOTTI_REVISIONI(prodotto_id,numero_revisione,codice,descrizione,linea_id,linea_codice,colore_commerciale,attivo,origine)"
                                    + " VALUES (1,2,'P1','Product 1',1,'L','A1',TRUE,'MODIFICA')");
                    }
                    return null;
                });
        assertThrows(RuntimeException.class, () -> new DatabaseSchema(db).initialize());
        db.withConnection(
                c -> {
                    assertEquals(
                            20, scalar(c, "SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
                    assertEquals(5, scalar(c, "SELECT COUNT(*) FROM TON.CONFIGURAZIONI_ARTICOLO"));
                    assertEquals(
                            3,
                            scalar(
                                    c,
                                    "SELECT COUNT(*) FROM TON.PRODOTTI WHERE versione_modifica=1"));
                    try (var tables =
                            c.getMetaData()
                                    .getTables(
                                            null,
                                            "TON",
                                            "CONFIGURAZIONI_PRODOTTO",
                                            new String[] {"TABLE"})) {
                        assertFalse(tables.next());
                    }
                    return null;
                });
    }

    private static int scalar(Connection c, String sql) throws SQLException {
        try (var q = c.createStatement();
                var r = q.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    private static void seed(Connection c) throws SQLException {
        try (var q = c.createStatement()) {
            q.executeUpdate("INSERT INTO TON.LINEE(codice,nome) VALUES ('L','Line')");
            q.executeUpdate(
                    "INSERT INTO"
                        + " TON.LINEE_REVISIONI(linea_id,numero_revisione,codice,nome,attivo,origine)"
                        + " VALUES (1,1,'L','Line',TRUE,'CREAZIONE')");
            for (int p = 1; p <= 3; p++) {
                q.executeUpdate(
                        "INSERT INTO TON.PRODOTTI(codice,descrizione,linea_id,colore_commerciale)"
                                + " VALUES ('P"
                                + p
                                + "','Product "
                                + p
                                + "',1,'A"
                                + p
                                + "')");
                q.executeUpdate(
                        "INSERT INTO"
                            + " TON.PRODOTTI_REVISIONI(prodotto_id,numero_revisione,codice,descrizione,linea_id,linea_codice,colore_commerciale,attivo,origine)"
                            + " VALUES ("
                                + p
                                + ",1,'P"
                                + p
                                + "','Product "
                                + p
                                + "',1,'L','A"
                                + p
                                + "',TRUE,'CREAZIONE')");
                q.executeUpdate(
                        "INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione) VALUES ("
                                + p
                                + ",1)");
            }
            q.executeUpdate(
                    "INSERT INTO"
                        + " TON.MODELLI_DISCO(codice,numero_versione,diametro_pressatura_mm,pressione_kg_cm2,grammi_per_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm)"
                        + " VALUES ('M',1,99,1500,25,1,1),('N',1,99,1500,26,1,1)");
            for (int a = 1; a <= 5; a++) {
                int p = a < 3 ? 1 : a < 5 ? 2 : 3;
                q.executeUpdate(
                        "INSERT INTO TON.ARTICOLI(codice,prodotto_id,diametro_mm,altezza_mm) VALUES"
                                + " ('A"
                                + a
                                + "',"
                                + p
                                + ",98,"
                                + (10 + a)
                                + ")");
                q.executeUpdate(
                        "INSERT INTO"
                            + " TON.ARTICOLI_REVISIONI(articolo_id,numero_revisione,codice,prodotto_id,prodotto_codice,linea_id,linea_codice,colore_commerciale,diametro_mm,altezza_mm,attivo,origine)"
                            + " VALUES ("
                                + a
                                + ",1,'A"
                                + a
                                + "',"
                                + p
                                + ",'P"
                                + p
                                + "',1,'L','A"
                                + p
                                + "',98,"
                                + (10 + a)
                                + ",TRUE,'CREAZIONE')");
                q.executeUpdate(
                        "INSERT INTO"
                            + " TON.CONFIGURAZIONI_ARTICOLO(articolo_id,prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,note,stato,approvato_il,linea_id,linea_revisione,prodotto_revisione,articolo_revisione,versione_modifica)"
                            + " VALUES ("
                                + a
                                + ","
                                + p
                                + ",1,"
                                + p
                                + ","
                                + (a == 4 ? 2 : 1)
                                + ",'rule','same','APPROVATA',CURRENT_TIMESTAMP,"
                                + (a == 5 ? "NULL,NULL,NULL,NULL" : "1,1,1,1")
                                + ",7)");
                q.executeUpdate(
                        "UPDATE TON.ARTICOLI SET configurazione_predefinita_id="
                                + a
                                + " WHERE articolo_id="
                                + a);
            }
            q.executeUpdate(
                    "INSERT INTO"
                        + " TON.PIANI_PRODUZIONE(prodotto_id,diametro_mm,data_preparazione,stato,etichetta_prodotto,numero_formati,numero_dischi,titolo_documento,contenuto_piano,preparato_il,versione_modifica)"
                        + " VALUES (1,98,CURRENT_DATE,'PREPARATA','Original',1,1,'Doc','immutable"
                        + " legacy snapshot',CURRENT_TIMESTAMP,4)");
            q.executeUpdate("INSERT INTO TON.PIANO_PRODUZIONE_RIGHE VALUES (1,1,1,1)");
        }
    }
}

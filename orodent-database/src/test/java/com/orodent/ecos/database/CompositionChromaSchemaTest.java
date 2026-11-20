package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

class CompositionChromaSchemaTest {
    @Test
    void upgradesV15WithoutRewritingExistingRecipesAndChecksInputBounds() throws Exception {
        String url =
                "jdbc:derby:memory:chroma_migration_"
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
                                    "INSERT INTO TON.LINEE(codice,nome) VALUES ('L','Linea')");
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.PRODOTTI(codice,descrizione,linea_id,colore_commerciale)"
                                        + " VALUES ('P','Prodotto',1,'A1')");
                            q.executeUpdate(
                                    "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES"
                                            + " ('W','Bianca','BIANCA')");
                            q.executeUpdate(
                                    "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione)"
                                            + " VALUES (1,1)");
                            q.executeUpdate(
                                    "INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione)"
                                            + " VALUES (1,1)");
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.COMPOSIZIONE_STRATI(composizione_id,numero_strato,nome)"
                                        + " VALUES (1,1,'Originale')");
                            q.executeUpdate(
                                    "INSERT INTO TON.COMPOSIZIONE_INGREDIENTI VALUES (1,1,1,100)");
                            q.executeUpdate("DROP TABLE TON.COMPOSIZIONE_CROMA_POLVERI");
                            q.executeUpdate("DROP TABLE TON.COMPOSIZIONE_CROMA");
                            q.executeUpdate(
                                    "DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE VERSION=16");
                        }
                        return null;
                    });
            new DatabaseSchema(db).initialize();
            new DatabaseSchema(db).initialize();
            db.withConnection(
                    c -> {
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT percentuale_massa FROM"
                                                        + " TON.COMPOSIZIONE_INGREDIENTI WHERE"
                                                        + " composizione_id=1")) {
                            assertTrue(r.next());
                            assertEquals(
                                    0,
                                    new java.math.BigDecimal("100").compareTo(r.getBigDecimal(1)));
                            assertFalse(r.next());
                        }
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT COUNT(*) FROM TON.COMPOSIZIONE_CROMA")) {
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
                                                                        + " TON.COMPOSIZIONE_CROMA"
                                                                        + " VALUES (1,1,2000.01)"))
                                            .getSQLState());
                            q.executeUpdate("INSERT INTO TON.COMPOSIZIONE_CROMA VALUES (1,1,0)");
                            assertEquals(
                                    "23513",
                                    assertThrows(
                                                    SQLException.class,
                                                    () ->
                                                            q.executeUpdate(
                                                                    "INSERT INTO"
                                                                        + " TON.COMPOSIZIONE_CROMA_POLVERI"
                                                                        + " VALUES"
                                                                        + " (1,1,1,'BIANCA',-0.01)"))
                                            .getSQLState());
                            q.executeUpdate(
                                    "INSERT INTO TON.COMPOSIZIONE_CROMA_POLVERI VALUES"
                                            + " (1,1,1,'BIANCA',100)");
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

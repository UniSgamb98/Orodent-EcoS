package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class CompositionChromaSchemaTest {
    @Test
    void upgradesV16RemovingOnlyDerivedParametersAndPreservingApprovedRecipePercentages()
            throws Exception {
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
            String ddl;
            try (var stream =
                    getClass()
                            .getResourceAsStream(
                                    "/database/migrations/V16__ton_composition_chroma.sql")) {
                ddl =
                        new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                                .replaceAll("(?m)^--.*$", "");
            }
            db.withTransaction(
                    c -> {
                        try (var q = c.createStatement()) {
                            for (String sql : ddl.split(";"))
                                if (!sql.isBlank()) q.executeUpdate(sql);
                            q.executeUpdate("DROP TABLE TON.PRESSE");
                            q.executeUpdate("DROP TABLE TON.STAMPI");
                            q.executeUpdate(
                                    "DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE VERSION>=17");
                            q.executeUpdate(
                                    "INSERT INTO TON.LINEE(codice,nome) VALUES ('L','Linea')");
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.PRODOTTI(codice,descrizione,linea_id,colore_commerciale)"
                                        + " VALUES ('P','Prodotto',1,'A1')");
                            q.executeUpdate(
                                    "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES"
                                            + " ('W','Bianca','BIANCA'),('Y','Gialla','GIALLA')");
                            q.executeUpdate(
                                    "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione)"
                                            + " VALUES (1,1),(2,1)");
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.COMPOSIZIONI(prodotto_id,numero_versione,stato,approvato_il)"
                                        + " VALUES (1,1,'APPROVATA',CURRENT_TIMESTAMP)");
                            q.executeUpdate(
                                    "INSERT INTO TON.COMPOSIZIONE_STRATI VALUES"
                                            + " (1,1,'Manuale'),(1,2,'Calcolato')");
                            q.executeUpdate(
                                    "INSERT INTO TON.COMPOSIZIONE_INGREDIENTI VALUES"
                                            + " (1,1,1,100),(1,2,1,99.6667),(1,2,2,0.3333)");
                            q.executeUpdate(
                                    "INSERT INTO TON.COMPOSIZIONE_CROMA VALUES (1,2,6.666)");
                            q.executeUpdate(
                                    "INSERT INTO TON.COMPOSIZIONE_CROMA_POLVERI VALUES"
                                            + " (1,2,1,'BIANCA',99.6667),(1,2,2,'CROMATICA',100)");
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
                        for (String table :
                                List.of("COMPOSIZIONE_CROMA", "COMPOSIZIONE_CROMA_POLVERI"))
                            try (var rs =
                                    c.getMetaData()
                                            .getTables(
                                                    null, "TON", table, new String[] {"TABLE"})) {
                                assertFalse(rs.next());
                            }
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT"
                                                    + " numero_strato,polvere_revisione_id,percentuale_massa"
                                                    + " FROM TON.COMPOSIZIONE_INGREDIENTI ORDER BY"
                                                    + " numero_strato,polvere_revisione_id")) {
                            for (String[] row :
                                    new String[][] {
                                        {"1", "1", "100"},
                                        {"2", "1", "99.6667"},
                                        {"2", "2", "0.3333"}
                                    }) {
                                assertTrue(r.next());
                                assertEquals(Integer.parseInt(row[0]), r.getInt(1));
                                assertEquals(Integer.parseInt(row[1]), r.getInt(2));
                                assertEquals(
                                        0,
                                        new java.math.BigDecimal(row[2])
                                                .compareTo(r.getBigDecimal(3)));
                            }
                            assertFalse(r.next());
                        }
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT stato FROM TON.COMPOSIZIONI WHERE"
                                                        + " composizione_id=1")) {
                            assertTrue(r.next());
                            assertEquals("APPROVATA", r.getString(1));
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

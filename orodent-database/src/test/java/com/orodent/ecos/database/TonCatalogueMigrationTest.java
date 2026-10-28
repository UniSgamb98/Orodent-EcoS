package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class TonCatalogueMigrationTest {
    private String url, previousLog;
    private ConnectionProvider connections;

    @BeforeEach
    void open() throws Exception {
        previousLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/ton-catalogue-migration.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:catalogue_" + UUID.randomUUID().toString().replace("-", "");
        connections =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        } finally {
            if (previousLog == null) System.clearProperty("derby.stream.error.file");
            else System.setProperty("derby.stream.error.file", previousLog);
        }
    }

    @Test
    void migrationPreservesIdsAndOnlyAdoptsUnambiguousExistingLines() {
        SchemaV5Fixture.create(connections);
        execute(
                "INSERT INTO TON.LINEE(codice,nome) VALUES ('L1','Linea 1'),('L2','Linea 2')",
                "INSERT INTO TON.PRODOTTI(codice,descrizione,versione_modifica) VALUES"
                    + " ('ONE','Unica',7),('MANY','Ambigua',1),('NONE','Senza linea',1)",
                "INSERT INTO TON.LINEE_PRODOTTI VALUES (1,1),(1,2),(2,2)",
                "INSERT INTO TON.ARTICOLI(codice,prodotto_id,diametro_mm,altezza_mm) VALUES"
                    + " ('ZR9816MSTA2',1,98,16)",
                "INSERT INTO ANAGRAFICHE.CLIENTI(codice_cliente,ragione_sociale) VALUES"
                    + " ('KEEP','Cliente')",
                "INSERT INTO"
                    + " VENDITE.VENDITE_DETTAGLIO(sorgente,documento_id,numero_riga,data_vendita,codice_cliente,codice_prodotto,quantita)"
                    + " VALUES ('X','X',1,CURRENT_DATE,'KEEP','ZR9816MSTA2',2)");
        var identity = connections.withConnection(DatabaseIdentity::initialize);
        var history = rows("SELECT * FROM ECOS.ECOS_SCHEMA_HISTORY ORDER BY version");
        var links = rows("SELECT * FROM TON.LINEE_PRODOTTI ORDER BY linea_id,prodotto_id");
        var sales = rows("SELECT * FROM VENDITE.VENDITE_DETTAGLIO");
        var backups = new AtomicInteger();
        new DatabaseSchema(connections)
                .initialize(
                        c -> {
                            assertEquals(
                                    5, number("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
                            backups.incrementAndGet();
                            return null;
                        });
        new DatabaseSchema(connections)
                .initialize(
                        c -> {
                            fail("Nessun secondo backup");
                            return null;
                        });
        assertEquals(1, backups.get());
        assertEquals(identity, connections.withConnection(DatabaseIdentity::read));
        assertEquals(
                history,
                rows("SELECT * FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version<=5 ORDER BY version"));
        assertEquals(links, rows("SELECT * FROM TON.LINEE_PRODOTTI ORDER BY linea_id,prodotto_id"));
        assertEquals(sales, rows("SELECT * FROM VENDITE.VENDITE_DETTAGLIO"));
        assertEquals(
                List.of(List.of(1L)), rows("SELECT linea_id FROM TON.PRODOTTI WHERE codice='ONE'"));
        assertEquals(2, number("SELECT COUNT(*) FROM TON.PRODOTTI WHERE linea_id IS NULL"));
        assertEquals(
                3, number("SELECT COUNT(*) FROM TON.PRODOTTI WHERE colore_commerciale IS NULL"));
        assertEquals(
                List.of(List.of(1L, 7, "ONE", "MIGRAZIONE")),
                rows(
                        "SELECT prodotto_id,numero_revisione,codice,origine FROM"
                            + " TON.PRODOTTI_REVISIONI WHERE prodotto_id=1"));
        assertEquals(
                1,
                number(
                        "SELECT COUNT(*) FROM TON.ARTICOLI_REVISIONI WHERE articolo_id=1 AND"
                            + " prodotto_id=1 AND linea_id=1"));
        connections.withConnection(
                c -> {
                    c.setReadOnly(true);
                    DatabaseSchema.verifyCompatible(c);
                    return null;
                });
    }

    @Test
    void clientDoesNotMigrateV5AndCurrentContractChecksTheHistory() {
        SchemaV5Fixture.create(connections);
        connections.withConnection(
                c -> {
                    c.setReadOnly(true);
                    assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(c));
                    return null;
                });
        assertEquals(5, number("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
        new DatabaseSchema(connections).initialize();
        execute("ALTER TABLE TON.ARTICOLI_REVISIONI DROP CONSTRAINT fk_ton_art_stor_pro");
        connections.withConnection(
                c -> {
                    assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(c));
                    return null;
                });
    }

    @Test
    void lateMigrationFailureRollsBackCanonicalLinksAndAllHistoryTables() {
        SchemaV5Fixture.create(connections);
        execute(
                "INSERT INTO TON.LINEE(codice,nome) VALUES ('L','Linea')",
                "INSERT INTO TON.PRODOTTI(codice) VALUES ('KEEP')",
                "INSERT INTO TON.LINEE_PRODOTTI VALUES (1,1)",
                "ALTER TABLE TON.CONFIGURAZIONI_ARTICOLO ADD CONSTRAINT ck_ton_cfg_stor CHECK"
                    + " (versione_modifica>0)");
        assertThrows(RuntimeException.class, () -> new DatabaseSchema(connections).initialize());
        assertEquals(5, number("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
        connections.withConnection(
                c -> {
                    try (var q = c.getMetaData().getColumns(null, "TON", "PRODOTTI", "LINEA_ID")) {
                        assertFalse(q.next());
                    }
                    try (var q =
                            c.getMetaData()
                                    .getTables(
                                            null,
                                            "TON",
                                            "PRODOTTI_REVISIONI",
                                            new String[] {"TABLE"})) {
                        assertFalse(q.next());
                    }
                    return null;
                });
        assertEquals(List.of(List.of(1L, 1L)), rows("SELECT * FROM TON.LINEE_PRODOTTI"));
        execute("ALTER TABLE TON.CONFIGURAZIONI_ARTICOLO DROP CONSTRAINT ck_ton_cfg_stor");
        new DatabaseSchema(connections).initialize();
        assertEquals(6, number("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
    }

    private void execute(String... sql) {
        connections.withTransaction(
                c -> {
                    for (String s : sql)
                        try (var q = c.createStatement()) {
                            q.executeUpdate(s);
                        }
                    return null;
                });
    }

    private int number(String sql) {
        return ((Number) rows(sql).getFirst().getFirst()).intValue();
    }

    private List<List<Object>> rows(String sql) {
        return connections.withConnection(
                c -> {
                    var result = new ArrayList<List<Object>>();
                    try (var q = c.createStatement();
                            var r = q.executeQuery(sql)) {
                        while (r.next()) {
                            var row = new ArrayList<Object>();
                            for (int i = 1; i <= r.getMetaData().getColumnCount(); i++)
                                row.add(r.getObject(i));
                            result.add(row);
                        }
                    }
                    return result;
                });
    }
}

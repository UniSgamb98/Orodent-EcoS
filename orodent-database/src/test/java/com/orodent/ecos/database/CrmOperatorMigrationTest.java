package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class CrmOperatorMigrationTest {
    static final String SEED = "00000000-0000-0000-0000-000000000001";
    String url;
    ConnectionProvider db;

    @BeforeEach
    void open() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url =
                "jdbc:derby:memory:operator_migration_"
                        + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        execute(
                "DROP TABLE TON.PRESSE",
                "DROP TABLE TON.STAMPI",
                "DROP TABLE TON.PIANI_DOCUMENTI",
                "DROP TABLE TON.PIANO_PRODUZIONE_RIGHE",
                "DROP TABLE TON.PIANI_PRODUZIONE",
                "DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE VERSION>=15",
                "DELETE FROM CRM.CATALOGHI_VERSIONI WHERE NOME='OPERATORI'",
                "DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE VERSION=14",
                "INSERT INTO ECOS.OPERATORI(ID,NOME,USERNAME) VALUES ('"
                        + SEED
                        + "','Operatore ufficio','utente')");
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    void execute(String... statements) {
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        for (var sql : statements) q.executeUpdate(sql);
                    }
                    return null;
                });
    }

    int scalar(String sql) {
        return db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery(sql)) {
                        r.next();
                        return r.getInt(1);
                    }
                });
    }

    @Test
    void removesOnlyTheUnusedPlaceholderAndMigratesOnce() {
        execute(
                "INSERT INTO ECOS.OPERATORI(ID,USERNAME) VALUES ('"
                        + UUID.randomUUID()
                        + "','Mario Rossi')");
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
        assertEquals(0, scalar("SELECT COUNT(*) FROM ECOS.OPERATORI WHERE ID='" + SEED + "'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM ECOS.OPERATORI"));
    }

    @Test
    void keepsLinkedHistoryAndDisablesThePlaceholder() {
        String client = UUID.randomUUID().toString(), note = UUID.randomUUID().toString();
        execute(
                "INSERT INTO CRM.CLIENTI(ID,RAGIONE_SOCIALE,OPERATORE_ID) VALUES ('"
                        + client
                        + "','Conservato','"
                        + SEED
                        + "')",
                "INSERT INTO CRM.NOTE_CLIENTE(ID,CLIENTE_ID,OPERATORE_ID,TESTO) VALUES ('"
                        + note
                        + "','"
                        + client
                        + "','"
                        + SEED
                        + "',CAST('Storico' AS CLOB))",
                "INSERT INTO CRM.INTERAZIONI(ID,CLIENTE_ID,OPERATORE_ID,NOTA_ID) VALUES ('"
                        + UUID.randomUUID()
                        + "','"
                        + client
                        + "','"
                        + SEED
                        + "','"
                        + note
                        + "')");
        new DatabaseSchema(db).initialize(c -> null);
        assertEquals(0, scalar("SELECT ATTIVO FROM ECOS.OPERATORI WHERE ID='" + SEED + "'"));
        for (String table : List.of("CLIENTI", "NOTE_CLIENTE", "INTERAZIONI"))
            assertEquals(
                    1,
                    scalar(
                            "SELECT COUNT(*) FROM CRM."
                                    + table
                                    + " WHERE OPERATORE_ID='"
                                    + SEED
                                    + "'"));
    }

    @Test
    void neverDisablesARenamedRealAccount() {
        execute("UPDATE ECOS.OPERATORI SET USERNAME='Mario',NOME='Mario' WHERE ID='" + SEED + "'");
        new DatabaseSchema(db).initialize(c -> null);
        assertEquals(1, scalar("SELECT ATTIVO FROM ECOS.OPERATORI WHERE ID='" + SEED + "'"));
    }
}

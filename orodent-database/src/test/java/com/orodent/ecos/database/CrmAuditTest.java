package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.audit.*;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

class CrmAuditTest {
    String url;
    ConnectionProvider db;
    UUID author = UUID.randomUUID();
    String customer = UUID.randomUUID().toString();

    @BeforeEach
    void setup() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:audit_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    int count(String table) {
        return db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        r.next();
                        return r.getInt(1);
                    }
                });
    }

    void client(Connection c) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "INSERT INTO CRM.CLIENTI(ID,RAGIONE_SOCIALE) VALUES (?,'Cliente di"
                                + " prova')")) {
            q.setString(1, customer);
            q.executeUpdate();
        }
    }

    void note(Connection c, String id, String text) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "INSERT INTO CRM.NOTE_CLIENTE(ID,CLIENTE_ID,TESTO) VALUES (?,?,?)")) {
            q.setString(1, id);
            q.setString(2, customer);
            q.setString(3, text);
            q.executeUpdate();
        }
    }

    @Test
    void distinguishesEmptyTextFromNullWithoutBreakingTheSave() {
        String id = UUID.randomUUID().toString();
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, author, "Mario", "CRM");
                    client(c);
                    note(c, id, null);
                    return null;
                });
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, author, "Mario", "CRM");
                    try (var q = c.createStatement()) {
                        q.executeUpdate("UPDATE CRM.NOTE_CLIENTE SET TESTO='' ");
                    }
                    return null;
                });
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT BEFORE_VALUE,AFTER_VALUE FROM AUDIT.CHANGES"
                                                + " WHERE CHANGE_KIND='UPDATE'")) {
                        assertTrue(r.next());
                        assertNull(AuditValues.decode(AuditValues.text(r.getClob(1))).get("TESTO"));
                        assertEquals(
                                "",
                                AuditValues.decode(AuditValues.text(r.getClob(2))).get("TESTO"));
                    }
                    return null;
                });
    }

    @Test
    void groupsBeforeAfterAndKeepsLongDeletedTextWithActorSnapshot() {
        String note = UUID.randomUUID().toString(), body = "prima: testo\n=à🙂 ".repeat(4000);
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, author, "Mario Rossi", "CRM");
                    client(c);
                    note(c, note, body);
                    return null;
                });
        assertEquals(1, count("AUDIT.OPERATIONS"));
        assertEquals(2, count("AUDIT.CHANGES"));
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, UUID.randomUUID(), "Lucia", "CRM");
                    try (var q = c.prepareStatement("DELETE FROM CRM.NOTE_CLIENTE WHERE ID=?")) {
                        q.setString(1, note);
                        q.executeUpdate();
                    }
                    return null;
                });
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT O.ACTOR_NAME,C.BEFORE_VALUE,C.AFTER_VALUE FROM"
                                                    + " AUDIT.OPERATIONS O JOIN AUDIT.CHANGES C ON"
                                                    + " O.OPERATION_ID=C.OPERATION_ID WHERE"
                                                    + " C.CHANGE_KIND='DELETE'")) {
                        assertTrue(r.next());
                        assertEquals("Lucia", r.getString(1));
                        assertEquals(
                                body,
                                AuditValues.decode(AuditValues.text(r.getClob(2))).get("TESTO"));
                        assertNull(r.getClob(3));
                    }
                    return null;
                });
    }

    @Test
    void rollsBackAuditTogetherWithDataAndCoalescesInternalDeleteInsert() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        db.withTransaction(
                                c -> {
                                    AuditContext.begin(c, author, "Mario", "CRM");
                                    client(c);
                                    throw new IllegalStateException("rollback");
                                }));
        assertEquals(0, count("AUDIT.OPERATIONS"));
        assertEquals(0, count("CRM.CLIENTI"));
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, author, "Mario", "CRM");
                    client(c);
                    return null;
                });
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, author, "Mario", "CRM");
                    String id = UUID.randomUUID().toString();
                    note(c, id, "temporanea");
                    try (var q = c.createStatement()) {
                        q.executeUpdate("DELETE FROM CRM.NOTE_CLIENTE");
                    }
                    return null;
                });
        assertEquals(1, count("AUDIT.OPERATIONS"));
        String note = UUID.randomUUID().toString();
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, author, "Mario", "CRM");
                    note(c, note, "stabile");
                    return null;
                });
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, author, "Mario", "CRM");
                    try (var q = c.createStatement()) {
                        q.executeUpdate("DELETE FROM CRM.NOTE_CLIENTE");
                    }
                    note(c, note, "stabile");
                    return null;
                });
        assertEquals(2, count("AUDIT.OPERATIONS"));
        assertEquals(2, count("AUDIT.CHANGES"));
    }

    @Test
    void capturesExternalSqlCascadesWithoutBorrowingAnOperator() {
        db.withTransaction(
                c -> {
                    client(c);
                    String contact = UUID.randomUUID().toString();
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO CRM.CONTATTI_CLIENTE VALUES ('"
                                        + contact
                                        + "','"
                                        + customer
                                        + "','Contatto')");
                        q.executeUpdate(
                                "INSERT INTO CRM.TELEFONI_CLIENTE VALUES ('"
                                        + UUID.randomUUID()
                                        + "','"
                                        + customer
                                        + "','"
                                        + contact
                                        + "','123')");
                    }
                    return null;
                });
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, author, "Mario", "CRM");
                    try (var q = c.createStatement()) {
                        q.executeUpdate("DELETE FROM CRM.CONTATTI_CLIENTE");
                    }
                    return null;
                });
        assertEquals(5, count("AUDIT.CHANGES"));
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT COUNT(*) FROM AUDIT.OPERATIONS WHERE ACTOR_ID"
                                                    + " IS NULL AND SOURCE LIKE 'SQL esterno%'")) {
                        r.next();
                        assertEquals(3, r.getInt(1));
                    }
                    return null;
                });
    }

    @Test
    void networkClientsKeepIndependentActorsAndClearTheContextAfterCommit() throws Exception {
        int port;
        try (var socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var server =
                new org.apache.derby.drda.NetworkServerControl(
                        java.net.InetAddress.getLoopbackAddress(), port);
        server.start(null);
        try {
            long limit = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (true) {
                try {
                    server.ping();
                    break;
                } catch (Exception pending) {
                    if (System.nanoTime() > limit) throw pending;
                    Thread.sleep(50);
                }
            }
            String remote =
                    "jdbc:derby://127.0.0.1:" + port + "/" + url.substring("jdbc:derby:".length());
            try (var a = DriverManager.getConnection(remote, "APP", "pw");
                    var b = DriverManager.getConnection(remote, "APP", "pw")) {
                a.setAutoCommit(false);
                b.setAutoCommit(false);
                AuditContext.begin(a, author, "Mario da rete", "CRM");
                client(a);
                a.commit();
                AuditContext.begin(b, UUID.randomUUID(), "Lucia da rete", "CRM");
                note(b, UUID.randomUUID().toString(), "Nota remota");
                b.commit();
                note(a, UUID.randomUUID().toString(), "SQL dopo commit");
                a.commit();
            }
            db.withConnection(
                    c -> {
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT ACTOR_NAME,SOURCE FROM AUDIT.OPERATIONS"
                                                        + " ORDER BY OCCURRED_AT")) {
                            assertTrue(r.next());
                            assertEquals("Mario da rete", r.getString(1));
                            assertTrue(r.next());
                            assertEquals("Lucia da rete", r.getString(1));
                            assertTrue(r.next());
                            assertNull(r.getString(1));
                            assertTrue(r.getString(2).startsWith("SQL esterno"));
                            assertFalse(r.next());
                        }
                        return null;
                    });
        } finally {
            server.shutdown();
        }
    }

    @Test
    void upgradesV23WithBackupAndRejectsMissingTriggers() {
        db.withTransaction(
                c -> {
                    AuditFixtures.beforeV24(c);
                    client(c);
                    return null;
                });
        var backups = new java.util.concurrent.atomic.AtomicInteger();
        new DatabaseSchema(db)
                .initialize(
                        c -> {
                            backups.incrementAndGet();
                            return null;
                        });
        assertEquals(1, backups.get());
        assertEquals(1, count("CRM.CLIENTI"));
        assertEquals(0, count("AUDIT.OPERATIONS"));
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate("DROP TRIGGER CRM.AUD_CLIENTI_I");
                    }
                    return null;
                });
        assertThrows(RuntimeException.class, () -> new DatabaseSchema(db).initialize());
    }
}

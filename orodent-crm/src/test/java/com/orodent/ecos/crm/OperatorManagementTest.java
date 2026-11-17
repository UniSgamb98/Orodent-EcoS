package com.orodent.ecos.crm;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.service.OperatorManagementService;
import com.orodent.ecos.crm.feature.auth.login.service.LoginService;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.module.OperatorSession;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

class OperatorManagementTest {
    String url;
    ConnectionProvider connections;
    Database db;
    OperatorSession session;
    OperatorManagementService service;
    UUID current;

    @BeforeEach
    void open() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:operators_" + UUID.randomUUID().toString().replace("-", "");
        connections =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(connections).initialize();
        session = new OperatorSession();
        db = new Database(connections, session);
        service = new OperatorManagementService(db);
        assertTrue(db.transaction(service::load).isEmpty());
        current = db.transaction(() -> new LoginService(db).createUser("Mario Rossi")).id();
        session.select(current, "Mario Rossi");
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    @Test
    void preservesCaseAndRejectsDuplicatesIncludingInactiveOperators() {
        assertEquals("Mario Rossi", db.transaction(service::load).getFirst().name());
        assertThrows(
                IllegalArgumentException.class,
                () -> db.transaction(() -> new LoginService(db).createUser("mARIO rOSSI")));
        UUID other = db.transaction(() -> service.create("Lucia"));
        db.transaction(
                () -> {
                    service.load();
                    service.active(other, false);
                    return null;
                });
        assertThrows(
                IllegalArgumentException.class,
                () -> db.transaction(() -> service.create("LUCIA")));
        assertEquals(1, db.transaction(() -> new LoginService(db).loadUsers()).size());
    }

    @Test
    void renameAndRemovalPreserveReferencesAndActivationRestoresLogin() {
        UUID other = db.transaction(() -> service.create("Lucia"));
        UUID client = UUID.randomUUID();
        connections.withTransaction(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO CRM.CLIENTI(ID,RAGIONE_SOCIALE,OPERATORE_ID)"
                                            + " VALUES (?,'Prova',?)")) {
                        q.setString(1, client.toString());
                        q.setString(2, other.toString());
                        q.executeUpdate();
                    }
                    return null;
                });
        db.transaction(
                () -> {
                    service.load();
                    service.rename(other, "Lucia Bianchi", "Lucia");
                    return null;
                });
        db.transaction(
                () -> {
                    service.load();
                    service.remove(other);
                    return null;
                });
        var entry =
                db.transaction(service::load).stream()
                        .filter(o -> o.id().equals(other))
                        .findFirst()
                        .orElseThrow();
        assertEquals("Lucia Bianchi", entry.name());
        assertFalse(entry.active());
        assertTrue(entry.linked());
        connections.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery("SELECT OPERATORE_ID FROM CRM.CLIENTI")) {
                        assertTrue(r.next());
                        assertEquals(other.toString(), r.getString(1));
                    }
                    return null;
                });
        assertEquals(
                List.of(current),
                db.transaction(() -> new LoginService(db).loadUsers()).stream()
                        .map(o -> o.id())
                        .toList());
        db.transaction(
                () -> {
                    service.active(other, true);
                    return null;
                });
        assertEquals(2, db.transaction(() -> new LoginService(db).loadUsers()).size());
    }

    @Test
    void removesUnusedOperatorsAndProtectsTheCurrentSession() {
        UUID unused = db.transaction(() -> service.create("Temporaneo"));
        db.transaction(
                () -> {
                    service.load();
                    service.remove(unused);
                    return null;
                });
        assertEquals(
                List.of(current), db.transaction(service::load).stream().map(o -> o.id()).toList());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.transaction(
                                () -> {
                                    service.remove(current);
                                    return null;
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.transaction(
                                () -> {
                                    service.active(current, false);
                                    return null;
                                }));
    }

    @Test
    void concurrentRenameDoesNotOverwriteAnotherWorkstation() {
        var otherDb = new Database(connections, new OperatorSession());
        var second = new OperatorManagementService(otherDb);
        db.transaction(service::load);
        otherDb.transaction(second::load);
        db.transaction(
                () -> {
                    service.rename(current, "Mario Verdi", "Mario Rossi");
                    return null;
                });
        assertThrows(
                Database.Conflict.class,
                () ->
                        otherDb.transaction(
                                () -> {
                                    second.rename(current, "Altro nome", "Mario Rossi");
                                    return null;
                                }));
        otherDb.transaction(second::load);
        assertThrows(
                Database.Conflict.class,
                () ->
                        otherDb.transaction(
                                () -> {
                                    second.rename(current, "Altro nome", "Mario Rossi");
                                    return null;
                                }),
                "A list refresh must not make a stale name draft overwrite another workstation");
        assertEquals("Mario Verdi", db.transaction(service::load).getFirst().name());
    }
}

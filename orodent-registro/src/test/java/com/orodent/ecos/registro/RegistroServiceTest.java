package com.orodent.ecos.registro;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.audit.AuditContext;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.*;
import java.util.*;

class RegistroServiceTest {
    String url;
    ConnectionProvider db;
    RegistroService service;
    String customer = UUID.randomUUID().toString(), note = UUID.randomUUID().toString();
    UUID mario = UUID.randomUUID(), lucia = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:register_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        service = new RegistroService(db);
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, mario, "Mario", "CRM");
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO CRM.CLIENTI(ID,RAGIONE_SOCIALE) VALUES ('"
                                        + customer
                                        + "','Laboratorio 10%')");
                        q.executeUpdate(
                                "INSERT INTO CRM.NOTE_CLIENTE(ID,CLIENTE_ID,TESTO) VALUES ('"
                                        + note
                                        + "','"
                                        + customer
                                        + "','Testo precedente')");
                    }
                    return null;
                });
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, lucia, "Lucia", "CRM");
                    try (var q = c.createStatement()) {
                        q.executeUpdate("UPDATE CRM.NOTE_CLIENTE SET TESTO='Testo nuovo'");
                    }
                    return null;
                });
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, lucia, "Lucia", "CRM");
                    try (var q = c.createStatement()) {
                        q.executeUpdate("DELETE FROM CRM.NOTE_CLIENTE");
                    }
                    return null;
                });
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    RegistroService.Filter filter(String actor, String kind, String search) {
        return new RegistroService.Filter(LocalDate.now(), LocalDate.now(), actor, kind, search, 0);
    }

    @Test
    void filtersAndTotalsPreserveWholeSavesAndDeletedSnapshots() {
        var all = service.load(filter("", "", ""));
        assertEquals(3, all.totals().events());
        assertEquals(2, all.totals().created());
        assertEquals(1, all.totals().modified());
        assertEquals(1, all.totals().deleted());
        var deleted = service.load(filter(lucia.toString(), "DELETE", "10%"));
        assertEquals(1, deleted.events().size());
        var event = deleted.events().getFirst();
        assertEquals("Lucia", event.actor());
        assertEquals(customer, event.customerId());
        var change = service.details(event.id()).getFirst();
        assertEquals("Testo nuovo", change.before().get("TESTO"));
        assertTrue(change.after().isEmpty());
        assertEquals(0, service.load(filter("", "", "10_")).totals().events());
        assertEquals(
                1, service.load(filter(mario.toString(), "INSERT", customer)).totals().events());
        assertEquals(
                2,
                service.details(
                                service.load(filter(mario.toString(), "INSERT", customer))
                                        .events()
                                        .getFirst()
                                        .id())
                        .size());
        assertTrue(service.customerExists(customer));
        assertFalse(service.customerExists(UUID.randomUUID().toString()));
        assertEquals(4, service.actors().size());
    }

    @Test
    void boundsDetailsOfLargeImportsWithoutSplittingTheSaveInTheMainList() {
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, mario, "Mario", "Importazione CRM2");
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO CRM.NOTE_CLIENTE(ID,CLIENTE_ID,TESTO) VALUES"
                                        + " (?,?,'Nota importata')")) {
                        for (int i = 0; i < 101; i++) {
                            q.setString(1, UUID.randomUUID().toString());
                            q.setString(2, customer);
                            q.executeUpdate();
                        }
                    }
                    return null;
                });
        var event =
                service.load(filter(mario.toString(), "INSERT", customer)).events().stream()
                        .filter(e -> e.changes() == 101)
                        .findFirst()
                        .orElseThrow();
        assertEquals(100, service.details(event.id()).size());
        assertEquals(1, service.details(event.id(), 1).size());
    }

    @Test
    void excludesOtherPeriodsAndRejectsInvalidRanges() {
        assertEquals(
                0,
                service.load(
                                new RegistroService.Filter(
                                        LocalDate.now().minusDays(3),
                                        LocalDate.now().minusDays(1),
                                        "",
                                        "",
                                        "",
                                        0))
                        .totals()
                        .events());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RegistroService.Filter(
                                LocalDate.now(), LocalDate.now().minusDays(1), "", "", "", 0));
    }
}

package com.orodent.ecos.crm;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.model.*;
import com.orodent.ecos.crm.core.database.service.*;
import com.orodent.ecos.crm.core.session.FeatureKey;
import com.orodent.ecos.crm.feature.calendario.service.CalendarioService;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.*;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.service.NuovoClienteService;
import com.orodent.ecos.crm.feature.clienti.schedacliente.service.SchedaClienteService;
import com.orodent.ecos.crm.feature.impostazioni.dto.ImpostazioneVoce;
import com.orodent.ecos.crm.feature.impostazioni.service.ImpostazioniService;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.module.OperatorSession;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.*;
import java.util.*;

class CrmIntegrationTest {
    String url;
    ConnectionProvider connections;
    Database db;
    OperatorSession session;

    @BeforeEach
    void open() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:crm_" + UUID.randomUUID().toString().replace("-", "");
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
        session.select(UUID.fromString("00000000-0000-0000-0000-000000000001"), "utente");
        db = new Database(connections, session);
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    UUID lead() {
        var service = new NuovoClienteService(new ClientePersistenceService(db), db.operators());
        return db.transaction(
                () ->
                        service.saveCliente(
                                        new NuovoClienteRequest(
                                                new ClienteInput(
                                                        "Laboratorio Rossi",
                                                        "Laboratorio",
                                                        "Presentazione",
                                                        3,
                                                        "IT123",
                                                        "",
                                                        LocalDate.now(),
                                                        ""),
                                                List.of(),
                                                List.of(new TelefonoClienteInput("123456")),
                                                List.of(),
                                                List.of(),
                                                List.of()))
                                .cliente()
                                .id());
    }

    int scalar(String sql) {
        return connections.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery(sql)) {
                        r.next();
                        return r.getInt(1);
                    }
                });
    }

    @Test
    void createsIndependentLeadAndConnectsCallsToCalendar() {
        UUID id = lead();
        assertEquals(1, scalar("SELECT COUNT(*) FROM CRM.CLIENTI"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM ANAGRAFICHE.CLIENTI"));
        var sheet = new SchedaClienteService(db);
        assertEquals(
                "Laboratorio Rossi", db.transaction(() -> sheet.loadProfile(id)).ragioneSociale());
        LocalDate next = LocalDate.now().plusDays(2);
        db.transaction(() -> sheet.addChiamata("Inviare listino", next, CallOutcome.LISTINO));
        assertEquals(1, scalar("SELECT COUNT(*) FROM CRM.INTERAZIONI"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM CRM.NOTE_CLIENTE"));
        var week = db.transaction(() -> new CalendarioService(db).getWeek(next, null));
        assertTrue(week.toString().contains("Laboratorio Rossi"));
    }

    @Test
    void rejectsStaleProfileInsteadOfOverwritingOtherOperatorsChanges() {
        UUID id = lead();
        var secondDb = new Database(connections, session);
        var a = new SchedaClienteService(db);
        var b = new SchedaClienteService(secondDb);
        db.transaction(() -> a.loadProfile(id));
        secondDb.transaction(() -> b.loadProfile(id));
        db.transaction(() -> a.updateCoinvolgimento(4));
        RuntimeException conflict =
                assertThrows(
                        RuntimeException.class,
                        () -> secondDb.transaction(() -> b.updateCoinvolgimento(2)));
        assertTrue(hasConflict(conflict));
        assertEquals(4, scalar("SELECT COINVOLGIMENTO FROM CRM.CLIENTI"));
    }

    @Test
    void rollsBackCompleteOperationAndDoesNotAdvanceCachedRevisionOnFailure() {
        UUID id = lead();
        var sheet = new SchedaClienteService(db);
        db.transaction(() -> sheet.loadProfile(id));
        assertThrows(
                IllegalStateException.class,
                () ->
                        db.transaction(
                                () -> {
                                    sheet.addNota("Da annullare");
                                    throw new IllegalStateException("Simulated failure");
                                }));
        assertEquals(0, scalar("SELECT COUNT(*) FROM CRM.NOTE_CLIENTE"));
        assertEquals(0, scalar("SELECT VERSIONE_MODIFICA FROM CRM.CLIENTI"));
        db.transaction(() -> sheet.addNota("Confermata"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM CRM.NOTE_CLIENTE"));
        assertEquals(1, scalar("SELECT VERSIONE_MODIFICA FROM CRM.CLIENTI"));
    }

    @Test
    void catalogueUpdatesKeepCustomerLinksAndRefuseDeletingUsedItems() {
        UUID id = lead(), oven = UUID.randomUUID();
        connections.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO CRM.FORNI(ID,TECNOLOGIA,MARCA,MODELLO) VALUES ('"
                                        + oven
                                        + "','Sinterizzazione','Marca','Uno')");
                        q.executeUpdate(
                                "INSERT INTO CRM.CLIENTI_FORNI(ID,CLIENTE_ID,FORNO_ID) VALUES ('"
                                        + UUID.randomUUID()
                                        + "','"
                                        + id
                                        + "','"
                                        + oven
                                        + "')");
                    }
                    return null;
                });
        var settings = new ImpostazioniService(db);
        var columns = List.of("TECNOLOGIA", "MARCA", "MODELLO");
        db.transaction(() -> settings.getVoci("FORNI", columns));
        db.transaction(
                () -> {
                    settings.saveVoci(
                            "FORNI",
                            columns,
                            List.of(
                                    new ImpostazioneVoce(
                                            oven, List.of("Sinterizzazione", "Marca", "Due"))));
                    return null;
                });
        assertEquals(1, scalar("SELECT COUNT(*) FROM CRM.CLIENTI_FORNI"));
        assertThrows(
                RuntimeException.class,
                () ->
                        db.transaction(
                                () -> {
                                    settings.saveVoci("FORNI", columns, List.of());
                                    return null;
                                }));
        assertEquals(1, scalar("SELECT COUNT(*) FROM CRM.CLIENTI_FORNI"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM CRM.FORNI"));
    }

    @Test
    void keepsSavedViewsPrivateToTheirOperator() {
        var views = new VistaSalvataService(db, db.operators());
        var saved =
                db.transaction(
                        () ->
                                views.create(
                                        FeatureKey.CLIENTI,
                                        "Richiami",
                                        new VersionedViewPayload(1, "{}"),
                                        true));
        assertEquals(
                saved.id(),
                db.transaction(() -> views.findPredefinita(FeatureKey.CLIENTI)).orElseThrow().id());
        UUID other = UUID.randomUUID();
        connections.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO ECOS.OPERATORI(ID,USERNAME) VALUES ('"
                                        + other
                                        + "','secondo')");
                    }
                    return null;
                });
        session.select(other, "secondo");
        assertTrue(db.transaction(() -> views.findAll(FeatureKey.CLIENTI)).isEmpty());
    }

    @Test
    void refusesAccessOutsideAnOperationAndRejectsBlankLead() {
        assertThrows(IllegalStateException.class, db::getConnection);
        var service = new NuovoClienteService(new ClientePersistenceService(db), db.operators());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.transaction(
                                () ->
                                        service.saveCliente(
                                                new NuovoClienteRequest(
                                                        new ClienteInput(
                                                                "", "", "", null, "", "", null, ""),
                                                        List.of(),
                                                        List.of(),
                                                        List.of(),
                                                        List.of(),
                                                        List.of()))));
    }

    private boolean hasConflict(Throwable error) {
        while (error != null) {
            if (error instanceof Database.Conflict) return true;
            error = error.getCause();
        }
        return false;
    }
}

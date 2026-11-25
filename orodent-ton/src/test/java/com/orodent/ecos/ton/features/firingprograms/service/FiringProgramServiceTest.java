package com.orodent.ecos.ton.features.firingprograms.service;

import static com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

class FiringProgramServiceTest {
    String url;
    ConnectionProvider db;
    ExecutorService workers;
    FiringProgramService service;

    @BeforeEach
    void setup() {
        url = "jdbc:derby:memory:programs_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException failure) {
                        throw new IllegalStateException(failure);
                    }
                };
        new DatabaseSchema(db).initialize();
        workers = Executors.newFixedThreadPool(2);
        service = new FiringProgramService(db, workers);
    }

    @AfterEach
    void close() throws Exception {
        workers.shutdown();
        assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException expected) {
            assertEquals("08006", expected.getSQLState());
        }
    }

    Draft draft(String name, int hold) {
        return new Draft(
                "STD",
                name,
                "Note del ciclo",
                true,
                List.of(
                        new Step(new BigDecimal("500"), 90, 5),
                        new Step(new BigDecimal("1450"), 60, hold),
                        new Step(new BigDecimal("900"), 45, 0)));
    }

    @Test
    void everySaveRetainsOrderedStepsAndNewApprovalKeepsTheProgramAlreadyAssignedToAFurnace() {
        var first = service.save(null, null, draft("Originale", 90), false).join();
        var edited =
                service.save(first.entry(), first.selected(), draft("Modificata", 120), false)
                        .join();
        var approved =
                service.save(edited.entry(), edited.selected(), draft("Approvata", 120), true)
                        .join();
        assertEquals(3, approved.versions().size());
        assertEquals(1, approved.selected().number());
        assertEquals(3, approved.selected().revision());
        assertFalse(approved.selected().editable());
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(
                                        approved.entry(),
                                        approved.selected(),
                                        draft("Sovrascritta", 0),
                                        false)
                                .join());
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate("INSERT INTO TON.FORNI(codice,nome) VALUES ('F1','Forno')");
                        q.executeUpdate(
                                "INSERT INTO"
                                    + " TON.CICLI_COTTURA(numero,forno_id,programma_id,data_prevista)"
                                    + " VALUES ('C1',1,"
                                        + approved.selected().id()
                                        + ",CURRENT_DATE)");
                    }
                    return null;
                });
        var copied = service.copy(approved.entry(), approved.selected()).join();
        assertEquals(2, copied.selected().number());
        assertThrows(
                CompletionException.class,
                () -> service.copy(copied.entry(), approved.selected()).join());
        var reordered =
                new Draft(
                        "STD",
                        "Seconda versione",
                        "",
                        true,
                        List.of(
                                new Step(new BigDecimal("900"), 30, 15),
                                new Step(new BigDecimal("1500"), 120, 60)));
        var second = service.save(copied.entry(), copied.selected(), reordered, true).join();
        var original = service.load(second.entry().id(), first.selected().id()).join().selected();
        assertEquals("Originale", original.name());
        assertEquals(90, original.steps().get(1).holdMinutes());
        assertEquals(500, original.steps().getFirst().temperature().intValue());
        assertFalse(original.editable());
        assertEquals(5, second.versions().size());
        assertEquals(900, second.selected().steps().getFirst().temperature().intValue());
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery("SELECT programma_id FROM TON.CICLI_COTTURA")) {
                        assertTrue(r.next());
                        assertEquals(approved.selected().id(), r.getLong(1));
                    }
                    return null;
                });
    }

    @Test
    void invalidApprovalsStaleSavesAndDuplicateCodesCannotAddOrOverwriteSnapshots() {
        var empty = new Draft("STD", "Vuota", "", true, List.of());
        var saved = service.save(null, null, empty, false).join();
        assertThrows(
                CompletionException.class,
                () -> service.save(saved.entry(), saved.selected(), empty, true).join());
        assertEquals(1, service.load(saved.entry().id(), null).join().versions().size());
        var changed =
                service.save(saved.entry(), saved.selected(), draft("Corretta", 30), false).join();
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(saved.entry(), saved.selected(), draft("Vecchia", 20), false)
                                .join());
        assertThrows(
                CompletionException.class,
                () -> service.save(null, null, draft("Duplicata", 30), false).join());
        assertEquals(
                changed.selected().id(),
                service.load(saved.entry().id(), null).join().selected().id());
        assertEquals(1, service.list("corretta", false).join().size());
    }

    @Test
    void concurrentSavesPreserveTheLosingOperatorsBaseline() {
        var first = service.save(null, null, draft("Base", 90), false).join();
        var one = service.save(first.entry(), first.selected(), draft("Operatore A", 120), false);
        var two = service.save(first.entry(), first.selected(), draft("Operatore B", 60), false);
        var outcomes =
                List.of(one, two).stream()
                        .map(f -> f.handle((value, error) -> error == null).join())
                        .toList();
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        var detail = service.load(first.entry().id(), null).join();
        assertEquals(2, detail.versions().size());
        assertEquals("Base", detail.versions().getLast().name());
    }
}

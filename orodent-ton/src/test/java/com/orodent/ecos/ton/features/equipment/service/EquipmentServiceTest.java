package com.orodent.ecos.ton.features.equipment.service;

import static com.orodent.ecos.ton.features.equipment.model.Equipment.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

class EquipmentServiceTest {
    String url;
    ConnectionProvider db;
    ExecutorService workers;
    EquipmentService service;
    AtomicBoolean usedWorker;

    @BeforeEach
    void open() {
        url = "jdbc:derby:memory:equipment_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        workers = Executors.newSingleThreadExecutor(r -> new Thread(r, "equipment-worker"));
        usedWorker = new AtomicBoolean();
        service =
                new EquipmentService(
                        () -> {
                            usedWorker.set(
                                    Thread.currentThread().getName().equals("equipment-worker"));
                            return db.openConnection();
                        },
                        workers);
    }

    @AfterEach
    void close() throws Exception {
        workers.shutdown();
        assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    Draft draft(String code, String name, String dimension, boolean active) {
        return new Draft(
                code, name, dimension == null ? null : new BigDecimal(dimension), "Note", active);
    }

    @Test
    void persistsEachCatalogueAndPreservesAnExistingFurnaceAndItsFiringLinks() {
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO TON.FORNI(codice,nome,note,versione_modifica) VALUES"
                                        + " ('F-OLD','Forno già presente','Conservato',7)");
                        q.executeUpdate(
                                "INSERT INTO TON.CICLI_COTTURA(numero,forno_id,data_prevista)"
                                        + " VALUES ('C-OLD',1,CURRENT_DATE)");
                    }
                    return null;
                });
        var press =
                service.save(
                                Kind.PRESSES,
                                null,
                                draft(" p300 ", "Pressa laboratorio", "250.125", true))
                        .join();
        var mould = service.save(Kind.MOULDS, null, draft("S99", "Stampo 99", "99", true)).join();
        assertEquals("P300", press.code());
        assertEquals(press, service.load(Kind.PRESSES, press.id()).join());
        assertEquals(mould, service.list(Kind.MOULDS, "", false).join().getFirst());
        assertTrue(usedWorker.get());
        var furnace = service.list(Kind.FURNACES, "", false).join().getFirst();
        assertEquals(7, furnace.revision());
        assertEquals("Conservato", furnace.notes());
        var renamed =
                service.save(Kind.FURNACES, furnace, draft("F-OLD", "Forno aggiornato", null, true))
                        .join();
        assertEquals(furnace.id(), renamed.id());
        assertEquals(8, renamed.revision());
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT forno_id FROM TON.CICLI_COTTURA WHERE"
                                                    + " numero='C-OLD'")) {
                        assertTrue(r.next());
                        assertEquals(furnace.id(), r.getLong(1));
                    }
                    return null;
                });
    }

    @Test
    void searchesLiterallyAndArchivesWithoutDeletingOrChangingTheOtherCatalogues() {
        var press =
                service.save(Kind.PRESSES, null, draft("100%_P", "Pressa speciale", "200", true))
                        .join();
        service.save(Kind.PRESSES, null, draft("P2", "Pressa grande", "300", true)).join();
        service.save(Kind.MOULDS, null, draft("P2", "Stampo", "98", true)).join();
        assertEquals(List.of(press), service.list(Kind.PRESSES, "%_", false).join());
        assertEquals(1, service.list(Kind.PRESSES, "GRANDE", false).join().size());
        var archived =
                service.save(Kind.PRESSES, press, draft(press.code(), press.name(), "200", false))
                        .join();
        assertEquals(press.id(), archived.id());
        assertEquals(1, service.list(Kind.PRESSES, "", false).join().size());
        assertEquals(2, service.list(Kind.PRESSES, "", true).join().size());
        assertEquals(1, service.list(Kind.MOULDS, "", false).join().size());
        assertFalse(service.load(Kind.PRESSES, press.id()).join().active());
    }

    @Test
    void rejectsDuplicatesAndStaleWritesAndDoesNotIncrementUnchangedRecords() {
        var first = service.save(Kind.MOULDS, null, draft("S99", "Stampo", "99", true)).join();
        assertEquals(first, service.save(Kind.MOULDS, first, first.draft()).join());
        var duplicate =
                assertThrows(
                        CompletionException.class,
                        () ->
                                service.save(Kind.MOULDS, null, draft("s99", "Altro", "99", true))
                                        .join());
        assertTrue(duplicate.getCause().getMessage().contains("già utilizzato"));
        var second =
                service.save(Kind.MOULDS, first, draft("S99", "Nuovo nome", "99.5", true)).join();
        var stale =
                assertThrows(
                        CompletionException.class,
                        () ->
                                service.save(
                                                Kind.MOULDS,
                                                first,
                                                draft("S99", "Vecchia copia", "100", true))
                                        .join());
        assertInstanceOf(Conflict.class, stale.getCause());
        assertEquals(second, service.load(Kind.MOULDS, first.id()).join());
    }

    @Test
    void validatesMandatoryFieldsMeasurementPrecisionAndCatalogueSpecificDimensions() {
        for (String invalid : List.of("0", "-1", "0.0001", "10000000"))
            assertThrows(IllegalArgumentException.class, () -> draft("P", "Pressa", invalid, true));
        assertThrows(IllegalArgumentException.class, () -> draft("", "Nome", "100", true));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.save(Kind.PRESSES, null, draft("P", "Nome", null, true)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.save(Kind.FURNACES, null, draft("F", "Nome", "100", true)));
    }
}

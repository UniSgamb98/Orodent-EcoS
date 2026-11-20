package com.orodent.ecos.ton.features.diskmodels.service;

import static com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.ton.features.equipment.model.Equipment;
import com.orodent.ecos.ton.features.equipment.service.EquipmentService;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

class ModelMouldServiceTest {
    String url;
    ConnectionProvider db;
    ExecutorService workers;
    DiskModelService service;
    EquipmentService equipment;

    @BeforeEach
    void open() {
        url = "jdbc:derby:memory:model_mould_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        workers = Executors.newFixedThreadPool(2);
        service = new DiskModelService(db, workers);
        equipment = new EquipmentService(db, workers);
        for (String[] item : new String[][] {{"S99", "99"}, {"S992", "99.2"}, {"S988", "98.8"}})
            equipment
                    .save(
                            Equipment.Kind.MOULDS,
                            null,
                            new Equipment.Draft(item[0], item[0], n(item[1]), "", true))
                    .join();
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

    static BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    Draft draft(String code, Mould ref, List<Mould> admitted) {
        return new Draft(
                code,
                ref.diameter(),
                n("1500"),
                n("25"),
                n("1.5"),
                n("0.5"),
                "",
                List.of(n("30"), n("20"), n("20"), n("30")),
                List.of(),
                new Moulds(ref, admitted));
    }

    Mould ref(String code) {
        return service.moulds().join().stream()
                .filter(m -> m.code().equals(code))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void preservesManualAdmissionAndVersionSnapshotsWhenTheEquipmentIsEdited() {
        var draft = draft("MODEL", ref("S99"), List.of(ref("S992")));
        var approved = service.create(draft, true).join().revision();
        assertEquals(draft, service.load(approved.id()).join().revision().data());
        assertEquals(1, approved.data().moulds().admitted().size());
        assertFalse(approved.data().moulds().all().stream().anyMatch(m -> m.code().equals("S988")));
        var copy = service.copy(approved).join().revision();
        assertEquals(draft, copy.data());
        var old = equipment.load(Equipment.Kind.MOULDS, draft.moulds().reference().id()).join();
        equipment
                .save(
                        Equipment.Kind.MOULDS,
                        old,
                        new Equipment.Draft(old.code(), "Nuova misura", n("99.123"), "", true))
                .join();
        assertEquals(draft, service.load(approved.id()).join().revision().data());
        var stale =
                assertThrows(
                        CompletionException.class,
                        () -> service.save(copy, copy.data(), true).join());
        assertTrue(stale.getCause().getMessage().contains("dati dello stampo aggiornati"));
        var changed = draft("MODEL", ref("S99"), List.of(ref("S992")));
        var next = service.save(copy, changed, true).join().revision();
        assertEquals(0, n("99.123").compareTo(next.data().diameter()));
        assertEquals(0, n("25").compareTo(next.data().grams()));
        assertEquals(draft, service.load(approved.id()).join().revision().data());
    }

    @Test
    void neverAutoAdmitsOtherMouldsAndRejectsArchivedAndForgedSelections() {
        var ref = ref("S99");
        var created = service.create(draft("ONE", ref, List.of()), false).join().revision();
        assertEquals(
                List.of(), service.load(created.id()).join().revision().data().moulds().admitted());
        var old = equipment.load(Equipment.Kind.MOULDS, ref.id()).join();
        var archived =
                equipment
                        .save(
                                Equipment.Kind.MOULDS,
                                old,
                                new Equipment.Draft(
                                        old.code(), old.name(), old.dimension(), "", false))
                        .join();
        var current =
                new Mould(
                        archived.id(),
                        archived.revision(),
                        archived.code(),
                        archived.name(),
                        archived.dimension());
        var failure =
                assertThrows(
                        CompletionException.class,
                        () -> service.save(created, draft("ONE", current, List.of()), true).join());
        assertTrue(failure.getCause().getMessage().contains("archiviato"));
        var fake = new Mould(2, 1, "S992", "S992", n("101"));
        assertThrows(
                CompletionException.class,
                () -> service.create(draft("FAKE", fake, List.of()), true).join());
        assertThrows(IllegalArgumentException.class, () -> new Moulds(ref, List.of(ref)));
        service.delete(created).join();
        assertEquals(0, service.catalogue("ONE", Filter.ALL).join().total());
    }
}

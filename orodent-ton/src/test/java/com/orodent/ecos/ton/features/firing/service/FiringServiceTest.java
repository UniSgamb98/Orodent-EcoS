package com.orodent.ecos.ton.features.firing.service;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.ProducedDiscs;
import com.orodent.ecos.ton.features.equipment.model.Equipment;
import com.orodent.ecos.ton.features.equipment.service.EquipmentService;
import com.orodent.ecos.ton.features.firing.model.Firing;
import com.orodent.ecos.ton.features.production.model.Production;
import com.orodent.ecos.ton.features.production.service.ProductionFixture;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

class FiringServiceTest {
    ConnectionProvider db;
    String url;
    ExecutorService workers;
    ProductionFixture fixture;
    FiringService service;
    EquipmentService equipment;
    final LocalDate loaded = LocalDate.of(2026, 11, 21);

    @BeforeEach
    void setup() {
        url = "jdbc:derby:memory:firing_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException failure) {
                        throw new IllegalStateException(failure);
                    }
                };
        new DatabaseSchema(db).initialize();
        workers = Executors.newFixedThreadPool(3);
        fixture = new ProductionFixture(db, workers);
        service = new FiringService(db, workers);
        equipment = new EquipmentService(db, workers);
        equipment
                .save(
                        Equipment.Kind.FURNACES,
                        null,
                        new Equipment.Draft("F1", "Forno uno", null, "", true))
                .join();
        equipment
                .save(
                        Equipment.Kind.FURNACES,
                        null,
                        new Equipment.Draft("F2", "Forno due", null, "", true))
                .join();
    }

    @AfterEach
    void close() throws Exception {
        workers.shutdown();
        assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException expected) {
            assertEquals("08006", expected.getSQLState());
        }
    }

    Production.Saved confirm() {
        return fixture.service.save(null, fixture.workspace(), fixture.request(false), true).join();
    }

    Firing.Quantity quantity(Firing.Group group, int count) {
        return new Firing.Quantity(group.key(), count);
    }

    Firing.Request load(Firing.Furnace furnace, String lot, Firing.Quantity... quantities) {
        return new Firing.Request(furnace, lot, loaded, "Carico registrato", List.of(quantities));
    }

    int count(String table) {
        return db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery("SELECT COUNT(*) FROM TON." + table)) {
                        r.next();
                        return r.getInt(1);
                    }
                });
    }

    @Test
    void confirmationCreatesAllDiscsOnceAndOutputFailureRollsBackTheDraftAsWell() {
        var draft =
                fixture.service
                        .save(null, fixture.workspace(), fixture.request(false), false)
                        .join();
        assertEquals(0, count("DISCHI_PRODOTTI"));
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "ALTER TABLE TON.DISCHI_PRODOTTI ADD CONSTRAINT reject_test_output"
                                    + " CHECK (articolo_codice<>'ZR9814MSTA4')");
                    }
                    return null;
                });
        assertThrows(
                CompletionException.class,
                () ->
                        fixture.service
                                .save(draft, fixture.workspace(), fixture.request(false), true)
                                .join());
        assertEquals(Production.State.BOZZA, fixture.service.load(draft.id()).join().state());
        assertEquals(0, count("DISCHI_PRODOTTI"));
        assertEquals(0, count("ESECUZIONI_PIANO"));
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "ALTER TABLE TON.DISCHI_PRODOTTI DROP CONSTRAINT"
                                    + " reject_test_output");
                    }
                    return null;
                });
        var saved =
                fixture.service
                        .save(draft, fixture.workspace(), fixture.request(false), true)
                        .join();
        assertEquals(64, service.workspace().join().pending().size());
        assertEquals(3, Firing.group(service.workspace().join().pending()).size());
        db.withTransaction(
                c -> {
                    ProducedDiscs.confirm(c, saved.id());
                    return null;
                });
        assertEquals(64, count("DISCHI_PRODOTTI"));
        assertEquals(1, count("ESECUZIONI_PIANO"));
        assertThrows(
                CompletionException.class,
                () ->
                        fixture.service
                                .save(draft, fixture.workspace(), fixture.request(false), true)
                                .join());
        assertEquals(saved.plan(), fixture.service.load(saved.id()).join().plan());
        confirm();
        var groups = Firing.group(service.workspace().join().pending());
        assertEquals(6, groups.size());
        assertEquals(
                2,
                groups.stream().filter(g -> g.reference().article().equals("ZR9814MSTA4")).count());
    }

    @Test
    void quantitiesSplitAGroupBetweenFurnacesAndPreserveIndividualIdentitiesAndSnapshots() {
        var saved = confirm();
        var before = service.workspace().join();
        var group = Firing.group(before.pending()).getFirst();
        assertEquals(16, group.count());
        var original =
                before.pending().stream()
                        .filter(
                                d ->
                                        d.planId() == group.key().planId()
                                                && d.row() == group.key().row())
                        .map(Firing.Disc::id)
                        .toList();
        var first =
                service.create(load(before.furnaces().getFirst(), " lotto-a ", quantity(group, 3)))
                        .join();
        var second =
                service.create(load(before.furnaces().getLast(), "LOTTO-B", quantity(group, 2)))
                        .join();
        assertEquals("LOTTO-A", first.lot());
        assertEquals(59, service.workspace().join().pending().size());
        assertEquals(11, Firing.group(service.workspace().join().pending()).getFirst().count());
        assertEquals(
                original.subList(0, 3),
                service.loaded(first.id()).join().stream().map(Firing.Disc::id).toList());
        assertEquals(
                original.subList(3, 5),
                service.loaded(second.id()).join().stream().map(Firing.Disc::id).toList());
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "UPDATE TON.FORNI SET codice='F1-NUOVO',nome='Nome"
                                    + " nuovo',versione_modifica=versione_modifica+1 WHERE"
                                    + " forno_id="
                                        + first.furnaceId());
                        q.executeUpdate(
                                "UPDATE TON.ARTICOLI SET codice='CODICE-CORRETTO' WHERE"
                                    + " articolo_id="
                                        + fixture.articles.get(14));
                    }
                    return null;
                });
        var stored =
                service.workspace().join().loads().stream()
                        .filter(l -> l.id() == first.id())
                        .findFirst()
                        .orElseThrow();
        assertEquals("F1", stored.furnaceCode());
        assertEquals("Forno uno", stored.furnaceName());
        assertEquals(1, stored.furnaceRevision());
        assertEquals("ZR9814MSTA4", service.loaded(first.id()).join().getFirst().article());
        long specification = saved.plan().formats().getFirst().article().specificationId();
        db.withConnection(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "SELECT"
                                        + " d.specifica_prodotto_id,p.modello_id,p.composizione_id,l.codice,c.forno_id"
                                        + " FROM TON.DISCHI_PRODOTTI d JOIN TON.CARICO_DISCHI a ON"
                                        + " a.disco_id=d.disco_id JOIN TON.CICLI_COTTURA c ON"
                                        + " c.ciclo_id=a.ciclo_id JOIN TON.LOTTI_COTTURA l ON"
                                        + " l.lotto_id=a.lotto_id JOIN TON.CONFIGURAZIONI_PRODOTTO"
                                        + " p ON p.configurazione_id=d.specifica_prodotto_id WHERE"
                                        + " d.articolo_codice=? AND l.codice=?")) {
                        q.setString(1, "ZR9814MSTA4");
                        q.setString(2, "LOTTO-A");
                        try (var r = q.executeQuery()) {
                            assertTrue(r.next());
                            assertEquals(specification, r.getLong(1));
                            assertEquals(fixture.model.revision().id(), r.getLong(2));
                            assertTrue(r.getLong(3) > 0);
                            assertEquals(first.furnaceId(), r.getLong(5));
                        }
                    }
                    return null;
                });
    }

    @Test
    void duplicateLotsInsufficientQuantitiesAndStaleFurnacesRollBackTheWholeLoad() {
        confirm();
        var data = service.workspace().join();
        var group = Firing.group(data.pending()).getFirst();
        var furnace = data.furnaces().getFirst();
        service.create(load(furnace, "LOTTO-UNICO", quantity(group, 1))).join();
        assertThrows(
                CompletionException.class,
                () -> service.create(load(furnace, "LOTTO-UNICO", quantity(group, 1))).join());
        assertThrows(
                CompletionException.class,
                () -> service.create(load(furnace, "ALTRO", quantity(group, 16))).join());
        assertThrows(
                CompletionException.class,
                () ->
                        service.create(
                                        new Firing.Request(
                                                furnace,
                                                "PRIMA",
                                                LocalDate.of(2026, 11, 18),
                                                "",
                                                List.of(quantity(group, 1))))
                                .join());
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "UPDATE TON.FORNI SET"
                                    + " attivo=FALSE,versione_modifica=versione_modifica+1 WHERE"
                                    + " forno_id="
                                        + furnace.id());
                    }
                    return null;
                });
        assertThrows(
                CompletionException.class,
                () -> service.create(load(furnace, "ARCHIVIATO", quantity(group, 1))).join());
        assertEquals(1, count("CICLI_COTTURA"));
        assertEquals(1, count("LOTTI_COTTURA"));
        assertEquals(1, count("CARICO_DISCHI"));
        assertEquals(63, service.workspace().join().pending().size());
    }

    @Test
    void concurrentOperatorsCannotOverAllocateTheSameGroup() {
        confirm();
        var data = service.workspace().join();
        var group = Firing.group(data.pending()).getFirst();
        var one =
                service.create(
                        load(data.furnaces().getFirst(), "CONCORRENTE-A", quantity(group, 16)));
        var two =
                service.create(
                        load(data.furnaces().getLast(), "CONCORRENTE-B", quantity(group, 16)));
        var outcomes =
                List.of(one, two).stream()
                        .map(f -> f.handle((value, error) -> error == null).join())
                        .toList();
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(16, count("CARICO_DISCHI"));
        assertEquals(1, count("CICLI_COTTURA"));
        assertEquals(48, service.workspace().join().pending().size());
    }

    @Test
    void concurrentPartialLoadsAllocateDifferentDiscs() {
        confirm();
        var data = service.workspace().join();
        var group = Firing.group(data.pending()).getFirst();
        var one =
                service.create(load(data.furnaces().getFirst(), "PARZIALE-A", quantity(group, 8)));
        var two = service.create(load(data.furnaces().getLast(), "PARZIALE-B", quantity(group, 8)));
        var first = one.join();
        var second = two.join();
        var ids =
                new HashSet<>(
                        service.loaded(first.id()).join().stream().map(Firing.Disc::id).toList());
        assertTrue(
                Collections.disjoint(
                        ids,
                        service.loaded(second.id()).join().stream().map(Firing.Disc::id).toList()));
        assertEquals(16, count("CARICO_DISCHI"));
        assertEquals(48, service.workspace().join().pending().size());
    }

    @Test
    void upgradesAlreadyPreparedPlansWithoutDuplicatingRegisteredOrLoadedDiscs() {
        var saved = confirm();
        var initial = service.workspace().join();
        var group = Firing.group(initial.pending()).getFirst();
        var load =
                service.create(
                                load(
                                        initial.furnaces().getFirst(),
                                        "PRIMA-UPDATE",
                                        quantity(group, 1)))
                        .join();
        long originalId = service.loaded(load.id()).join().getFirst().id();
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "DELETE FROM TON.DISCHI_PRODOTTI WHERE piano_id="
                                        + saved.id()
                                        + " AND (numero_riga>1 OR progressivo>3)");
                        q.executeUpdate(
                                "UPDATE TON.ARTICOLI SET codice='CODICE-NUOVO' WHERE articolo_id="
                                        + fixture.articles.get(14));
                        q.executeUpdate(
                                "ALTER TABLE TON.PIANI_PRODUZIONE DROP COLUMN dischi_confermati_il"
                                    + " RESTRICT");
                        q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=30");
                    }
                    return null;
                });
        new DatabaseSchema(db).initialize();
        new DatabaseSchema(db).initialize();
        assertEquals(64, count("DISCHI_PRODOTTI"));
        assertEquals(63, service.workspace().join().pending().size());
        assertEquals(originalId, service.loaded(load.id()).join().getFirst().id());
        assertEquals(
                "ZR9814MSTA4",
                Firing.group(service.workspace().join().pending())
                        .getFirst()
                        .reference()
                        .article());
        assertEquals(saved.plan(), fixture.service.load(saved.id()).join().plan());
        assertEquals(2, count("ESECUZIONI_PIANO"));
    }
}

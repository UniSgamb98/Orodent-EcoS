package com.orodent.ecos.ton.features.firing.service;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.ton.features.equipment.model.Equipment;
import com.orodent.ecos.ton.features.equipment.service.EquipmentService;
import com.orodent.ecos.ton.features.firing.model.Firing;
import com.orodent.ecos.ton.features.production.model.ProductionExecution;
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
    final LocalDate produced = LocalDate.of(2026, 11, 20), loaded = LocalDate.of(2026, 11, 21);

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

    ProductionExecution.Workspace prepare() {
        var plan =
                fixture.service
                        .save(null, fixture.workspace(), fixture.request(false), true)
                        .join();
        return fixture.service.execution(plan.id()).join();
    }

    void registerAll(ProductionExecution.Workspace work) {
        fixture.service
                .register(
                        work,
                        new ProductionExecution.Request(
                                produced,
                                work.prepared().plan().quantities(),
                                "Pressati e controllati"))
                .join();
    }

    Firing.Request load(Firing.Furnace furnace, String lot, List<Long> ids) {
        return new Firing.Request(furnace, lot, loaded, "Carico registrato", ids);
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
    void onlyDeclaredOutputEntersTheQueueAndPartialOrStaleRegistrationsCannotCreateExtraDiscs() {
        var draft =
                fixture.service
                        .save(null, fixture.workspace(), fixture.request(false), false)
                        .join();
        assertThrows(CompletionException.class, () -> fixture.service.execution(draft.id()).join());
        var work = prepare();
        assertEquals(0, service.workspace().join().pending().size());
        var request =
                new ProductionExecution.Request(
                        produced, Map.of(fixture.articles.get(14), 2), "Due pezzi prodotti");
        fixture.service.register(work, request).join();
        assertEquals(2, service.workspace().join().pending().size());
        assertEquals(62, fixture.service.execution(work.prepared().id()).join().remaining());
        assertThrows(
                CompletionException.class, () -> fixture.service.register(work, request).join());
        var current = fixture.service.execution(work.prepared().id()).join();
        assertThrows(
                CompletionException.class,
                () ->
                        fixture.service
                                .register(
                                        current,
                                        new ProductionExecution.Request(
                                                produced, Map.of(fixture.articles.get(14), 15), ""))
                                .join());
        assertEquals(2, count("DISCHI_PRODOTTI"));
        assertEquals(1, count("ESECUZIONI_PIANO"));
        assertEquals(
                current.prepared().token(),
                fixture.service.load(current.prepared().id()).join().token());
    }

    @Test
    void
            splitsPhysicalDiscsBetweenFurnacesAndKeepsTheOriginalArticleSpecificationAndFurnaceSnapshot() {
        var work = prepare();
        registerAll(work);
        var before = service.workspace().join();
        assertEquals(64, before.pending().size());
        var firstIds = before.pending().subList(0, 3).stream().map(Firing.Disc::id).toList();
        var secondIds = before.pending().subList(3, 5).stream().map(Firing.Disc::id).toList();
        var first =
                service.create(load(before.furnaces().getFirst(), " lotto-a ", firstIds)).join();
        var second = service.create(load(before.furnaces().getLast(), "LOTTO-B", secondIds)).join();
        assertEquals("LOTTO-A", first.lot());
        assertEquals(59, service.workspace().join().pending().size());
        assertEquals(2, service.workspace().join().loads().size());
        assertEquals(
                firstIds, service.loaded(first.id()).join().stream().map(Firing.Disc::id).toList());
        assertEquals(
                secondIds,
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
        long specification =
                work.prepared().plan().formats().getFirst().article().specificationId();
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
    void duplicateLotsStaleFurnacesAndAlreadyLoadedDiscsRollBackTheEntireLoad() {
        registerAll(prepare());
        var data = service.workspace().join();
        long a = data.pending().get(0).id(), b = data.pending().get(1).id();
        var furnace = data.furnaces().getFirst();
        service.create(load(furnace, "LOTTO-UNICO", List.of(a))).join();
        assertThrows(
                CompletionException.class,
                () -> service.create(load(furnace, "LOTTO-UNICO", List.of(b))).join());
        assertThrows(
                CompletionException.class,
                () -> service.create(load(furnace, "ALTRO", List.of(a, b))).join());
        assertThrows(
                CompletionException.class,
                () ->
                        service.create(
                                        new Firing.Request(
                                                furnace,
                                                "PRIMA",
                                                produced.minusDays(1),
                                                "",
                                                List.of(b)))
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
                () -> service.create(load(furnace, "ARCHIVIATO", List.of(b))).join());
        assertEquals(1, count("CICLI_COTTURA"));
        assertEquals(1, count("LOTTI_COTTURA"));
        assertEquals(1, count("CARICO_DISCHI"));
        assertEquals(63, service.workspace().join().pending().size());
    }

    @Test
    void concurrentOperatorsCannotAssignTheSameDiscTwice() {
        registerAll(prepare());
        var data = service.workspace().join();
        long id = data.pending().getFirst().id();
        var one = service.create(load(data.furnaces().getFirst(), "CONCORRENTE-A", List.of(id)));
        var two = service.create(load(data.furnaces().getLast(), "CONCORRENTE-B", List.of(id)));
        var outcomes =
                List.of(one, two).stream()
                        .map(f -> f.handle((value, error) -> error == null).join())
                        .toList();
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, count("CARICO_DISCHI"));
        assertEquals(1, count("CICLI_COTTURA"));
        assertEquals(63, service.workspace().join().pending().size());
    }
}

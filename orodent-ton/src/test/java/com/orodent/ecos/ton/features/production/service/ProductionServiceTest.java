package com.orodent.ecos.ton.features.production.service;

import static com.orodent.ecos.ton.features.production.model.Production.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.ton.features.diskmodels.service.DiskModelService;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

class ProductionServiceTest {
    ConnectionProvider db;
    ExecutorService workers;
    ProductionFixture fixture;
    String url;

    @BeforeEach
    void open() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:plans_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        workers = Executors.newFixedThreadPool(3);
        fixture = new ProductionFixture(db, workers);
    }

    @AfterEach
    void close() throws Exception {
        workers.shutdown();
        assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    int count(String table) {
        return db.withConnection(
                c -> {
                    try (var s = c.createStatement();
                            var r = s.executeQuery("SELECT COUNT(*) FROM TON." + table)) {
                        r.next();
                        return r.getInt(1);
                    }
                });
    }

    @Test
    void computesExactDosesAndRoundingsBeforePreparingRequestedWork() {
        var w = fixture.workspace();
        var r = fixture.request(true);
        var template = fixture.service.catalogues().join().templates().getFirst();
        var p = ProductionCalculator.calculate(w, r, template);
        assertEquals(64, p.count());
        assertEquals(0, new BigDecimal("31360").compareTo(p.needed()));
        assertEquals(0, new BigDecimal("31516.8").compareTo(p.prepared()));
        assertEquals(4, p.mixtures().size());
        for (var m : p.mixtures())
            assertEquals(
                    0,
                    m.prepared()
                            .compareTo(
                                    m.powders().stream()
                                            .map(PowderWeight::grams)
                                            .reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(
                0,
                new BigDecimal("142.5")
                        .compareTo(p.formats().getFirst().article().doses().getFirst().grams()));
        assertFalse(w.articles().getLast().available());
    }

    @Test
    void savesReopensAndFreezesPlanWithoutRecordingPhysicalStock() {
        var first =
                fixture.service
                        .save(null, fixture.workspace(), fixture.request(true), false)
                        .join();
        assertEquals(State.BOZZA, first.state());
        assertEquals(3, count("PIANO_PRODUZIONE_RIGHE"));
        var prepared =
                fixture.service
                        .save(first, fixture.workspace(), fixture.request(true), true)
                        .join();
        assertEquals(State.PREPARATA, prepared.state());
        assertEquals(first.id(), prepared.id());
        assertEquals(prepared, fixture.service.load(prepared.id()).join());
        assertEquals(0, count("PRODUZIONI"));
        assertEquals(0, count("PRODUZIONE_RIGHE"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        fixture.service.save(
                                prepared, fixture.workspace(), fixture.request(true), true));
        new DiskModelService(db, workers).retire(fixture.model.revision()).join();
        assertEquals(prepared.plan(), fixture.service.load(prepared.id()).join().plan());
        assertThrows(
                CompletionException.class,
                () ->
                        fixture.service
                                .save(null, fixture.workspace(), fixture.request(true), true)
                                .join());
        assertEquals(1, count("PIANI_PRODUZIONE"));
    }

    @Test
    void rejectsStaleDraftAndPreservesCurrentRows() {
        var a =
                fixture.service
                        .save(null, fixture.workspace(), fixture.request(false), false)
                        .join();
        var b = fixture.service.save(a, fixture.workspace(), fixture.request(false), false).join();
        assertThrows(
                CompletionException.class,
                () ->
                        fixture.service
                                .save(a, fixture.workspace(), fixture.request(false), false)
                                .join());
        assertEquals(b, fixture.service.load(a.id()).join());
        assertEquals(3, count("PIANO_PRODUZIONE_RIGHE"));
    }

    @Test
    void rejectsUnavailableArticlesAndEmptyPreparationButKeepsEmptyDraft() {
        var source = fixture.request(false);
        var request =
                new Request(
                        source.date(),
                        source.productId(),
                        source.diameter(),
                        null,
                        "",
                        Map.of(fixture.articles.get(22), 1));
        assertThrows(
                CompletionException.class,
                () -> fixture.service.save(null, fixture.workspace(), request, true).join());
        var empty =
                new Request(
                        source.date(), source.productId(), source.diameter(), null, "", Map.of());
        var draft = fixture.service.save(null, fixture.workspace(), empty, false).join();
        assertEquals(0, draft.plan().count());
        assertThrows(
                CompletionException.class,
                () -> fixture.service.save(draft, fixture.workspace(), empty, true).join());
        assertEquals(State.BOZZA, fixture.service.load(draft.id()).join().state());
    }

    @Test
    void documentEmissionIsIdempotentAndNeverRecreatesAPlan() {
        var p = fixture.service.save(null, fixture.workspace(), fixture.request(true), true).join();
        String first = "<!doctype html><p>Originale fissato</p>";
        assertEquals(first, fixture.service.storeDocument(p.id(), first).join());
        assertEquals(first, fixture.service.storeDocument(p.id(), "<p>Modificato</p>").join());
        assertEquals(first, fixture.service.load(p.id()).join().document());
        assertEquals(1, count("PIANI_DOCUMENTI"));
        assertEquals(1, count("PIANI_PRODUZIONE"));
    }

    @Test
    void distributesResidualTenthsWithoutChangingRecipeOrTotal() {
        var powders =
                List.of(
                        new PowderShare(1, "A", "A", new BigDecimal("33.3333")),
                        new PowderShare(2, "B", "B", new BigDecimal("33.3333")),
                        new PowderShare(3, "C", "C", new BigDecimal("33.3334")));
        var values = ProductionCalculator.distribute(new BigDecimal("0.1"), powders);
        assertEquals(0, new BigDecimal("0.1").compareTo(values.get(2).grams()));
        assertEquals(0, values.getFirst().grams().signum());
        assertEquals(
                0,
                new BigDecimal("0.1")
                        .compareTo(
                                values.stream()
                                        .map(PowderWeight::grams)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
    }
}

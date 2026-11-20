package com.orodent.ecos.ton.features.production.service;

import static com.orodent.ecos.ton.features.production.model.Production.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;
import com.orodent.ecos.ton.features.catalog.service.*;
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
    void resolvesReferenceAndDosesByArticleDiameterAndRejectsAnUnconfiguredFormat() {
        var models = new DiskModelService(db, workers);
        var source = fixture.model.revision();
        var copy = models.copy(source).join().revision();
        var equipment =
                new com.orodent.ecos.ton.features.equipment.service.EquipmentService(db, workers);
        var mould =
                equipment
                        .save(
                                com.orodent.ecos.ton.features.equipment.model.Equipment.Kind.MOULDS,
                                null,
                                new com.orodent.ecos.ton.features.equipment.model.Equipment.Draft(
                                        "S96", "Stampo 96", new BigDecimal("96"), "", true))
                        .join();
        var d = source.data();
        var group =
                new com.orodent.ecos.ton.features.diskmodels.model.DiskModels.DiameterGroup(
                        new BigDecimal("95"),
                        new BigDecimal("20"),
                        new com.orodent.ecos.ton.features.diskmodels.model.DiskModels.Moulds(
                                new com.orodent.ecos.ton.features.diskmodels.model.DiskModels.Mould(
                                        mould.id(),
                                        mould.revision(),
                                        mould.code(),
                                        mould.name(),
                                        mould.dimension()),
                                List.of()));
        var data =
                new com.orodent.ecos.ton.features.diskmodels.model.DiskModels.Draft(
                        d.code(),
                        d.pressure(),
                        d.top(),
                        d.bottom(),
                        d.notes(),
                        d.layers(),
                        d.ranges(),
                        List.of(d.diameters().getFirst(), group));
        var approved = models.save(copy, data, true).join().revision();
        var catalog = new com.orodent.ecos.ton.features.catalog.service.CatalogService(db, workers);
        var specs =
                new com.orodent.ecos.ton.features.catalog.service.SpecificationService(db, workers);
        long comp =
                db.withConnection(
                        c -> {
                            try (var q = c.createStatement();
                                    var r =
                                            q.executeQuery(
                                                    "SELECT composizione_id FROM"
                                                        + " TON.CONFIGURAZIONI_PRODOTTO FETCH FIRST"
                                                        + " ROW ONLY")) {
                                r.next();
                                return r.getLong(1);
                            }
                        });
        var a95 =
                (com.orodent.ecos.ton.features.catalog.model.CatalogModels.Article)
                        catalog.create(
                                        new com.orodent.ecos.ton.features.catalog.model
                                                .CatalogModels.ArticleDraft(
                                                "A95",
                                                "Formato 95",
                                                fixture.product.id(),
                                                new BigDecimal("95"),
                                                new BigDecimal("12"),
                                                "",
                                                true))
                                .join()
                                .entry();
        specs.save(
                        (com.orodent.ecos.ton.features.catalog.model.CatalogModels.Product)
                                catalog.load(
                                                com.orodent.ecos.ton.features.catalog.model
                                                        .CatalogKind.PRODUCTS,
                                                fixture.product.id())
                                        .join()
                                        .entry(),
                        null,
                        new com.orodent.ecos.ton.features.catalog.model.CatalogModels
                                .SpecificationDraft(comp, approved.id(), "COSTI-NOMINALE-1", ""),
                        true)
                .join();
        var updatedProduct =
                (com.orodent.ecos.ton.features.catalog.model.CatalogModels.Product)
                        catalog.load(
                                        com.orodent.ecos.ton.features.catalog.model.CatalogKind
                                                .PRODUCTS,
                                        fixture.product.id())
                                .join()
                                .entry();
        specs.prefer(updatedProduct, specs.page(updatedProduct, 0, "", "").join().rows().getFirst())
                .join();
        var workspace =
                fixture.service
                        .workspace(fixture.product.id(), new BigDecimal("95"), Map.of())
                        .join();
        var article = workspace.articles().getFirst();
        assertTrue(article.available(), article.reason());
        assertEquals(0, new BigDecimal("96").compareTo(article.pressDiameter()));
        assertEquals(0, new BigDecimal("282").compareTo(article.weight()));
        var request =
                new Request(
                        java.time.LocalDate.now(),
                        fixture.product.id(),
                        new BigDecimal("95"),
                        null,
                        "",
                        Map.of(a95.id(), 2));
        var prepared = fixture.service.save(null, workspace, request, true).join();
        assertEquals(0, new BigDecimal("564").compareTo(prepared.plan().needed()));
        assertEquals(prepared, fixture.service.load(prepared.id()).join());
        assertEquals(source.data(), models.load(source.id()).join().revision().data());
        assertEquals(
                0,
                new BigDecimal("352.5")
                        .compareTo(fixture.workspace().articles().getFirst().weight()));
        var a97 =
                (com.orodent.ecos.ton.features.catalog.model.CatalogModels.Article)
                        catalog.create(
                                        new com.orodent.ecos.ton.features.catalog.model
                                                .CatalogModels.ArticleDraft(
                                                "A97",
                                                "Formato 97",
                                                fixture.product.id(),
                                                new BigDecimal("97"),
                                                new BigDecimal("12"),
                                                "",
                                                true))
                                .join()
                                .entry();
        var missing =
                fixture.service
                        .workspace(fixture.product.id(), new BigDecimal("97"), Map.of())
                        .join()
                        .articles()
                        .getFirst();
        assertFalse(missing.available());
        assertTrue(missing.reason().contains("non configurato"));
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
    void allFormatsShareOneProductSpecificationWhilePreparedPlansKeepTheOriginalVersion() {
        var workspace = fixture.workspace();
        long first = workspace.articles().getFirst().specificationId();
        assertTrue(first > 0);
        assertEquals(
                1,
                workspace.articles().stream()
                        .map(ArticleChoice::specificationId)
                        .distinct()
                        .count());
        var prepared = fixture.service.save(null, workspace, fixture.request(false), true).join();
        assertEquals(2, prepared.plan().schemaVersion());
        var catalog = new CatalogService(db, workers);
        var specifications = new SpecificationService(db, workers);
        var product =
                (Product) catalog.load(CatalogKind.PRODUCTS, fixture.product.id()).join().entry();
        var original = specifications.page(product, 0, "", "").join().rows().getFirst();
        specifications.copy(product, original).join();
        var draft = specifications.page(product, 0, "", "").join().rows().getFirst();
        specifications
                .save(
                        product,
                        draft,
                        new SpecificationDraft(
                                original.compositionId(), original.modelId(), "v2", "Next"),
                        true)
                .join();
        var next = specifications.page(product, 0, "", "").join().rows().getFirst();
        specifications.prefer(product, next).join();
        assertTrue(
                fixture.workspace().articles().stream()
                        .allMatch(a -> a.specificationId() == next.id()));
        assertEquals(prepared, fixture.service.load(prepared.id()).join());
        assertTrue(
                prepared.plan().formats().stream()
                        .allMatch(f -> f.article().specificationId() == first));
        assertEquals(0, count("PIANO_PRODUZIONE_RIGHE"));
        assertEquals(3, count("PIANO_PRODOTTO_RIGHE"));
    }

    @Test
    void ambiguousImportedSpecificationsBlockProductionUntilTheUserChoosesTheProductDefault() {
        var catalog = new CatalogService(db, workers);
        var specifications = new SpecificationService(db, workers);
        db.withTransaction(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "UPDATE TON.PRODOTTI SET"
                                        + " configurazione_predefinita_id=NULL,specifiche_da_verificare=TRUE,versione_modifica=versione_modifica+1"
                                        + " WHERE prodotto_id=?")) {
                        q.setLong(1, fixture.product.id());
                        q.executeUpdate();
                    }
                    var product =
                            new com.orodent.ecos.ton.features.catalog.repository.CatalogRepository(
                                            c)
                                    .load(CatalogKind.PRODUCTS, fixture.product.id());
                    new com.orodent.ecos.ton.features.catalog.repository.CatalogHistoryRepository(c)
                            .record(product, "MIGRAZIONE");
                    return null;
                });
        var blocked = fixture.workspace();
        assertTrue(blocked.articles().stream().noneMatch(ArticleChoice::available));
        assertTrue(blocked.articles().getFirst().reason().contains("da verificare"));
        assertThrows(
                CompletionException.class,
                () -> fixture.service.save(null, blocked, fixture.request(false), true).join());
        var product =
                (Product) catalog.load(CatalogKind.PRODUCTS, fixture.product.id()).join().entry();
        var version = specifications.page(product, 0, "", "").join().rows().getFirst();
        var selected = (Product) specifications.prefer(product, version).join().entry();
        assertFalse(selected.specificationConflict());
        assertEquals(version.id(), selected.defaultSpecification());
        assertTrue(fixture.workspace().articles().getFirst().available());
    }

    @Test
    void savesReopensAndFreezesPlanWithoutRecordingPhysicalStock() {
        var first =
                fixture.service
                        .save(null, fixture.workspace(), fixture.request(true), false)
                        .join();
        assertEquals(State.BOZZA, first.state());
        assertEquals(3, count("PIANO_PRODOTTO_RIGHE"));
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
    void legacyPreparedSnapshotsAreReadWithoutResolvingTheirArticleSpecificationIds() {
        var prepared =
                fixture.service
                        .save(null, fixture.workspace(), fixture.request(false), true)
                        .join();
        String legacy =
                new com.google.gson.Gson()
                        .toJson(prepared.plan())
                        .replace("\"schemaVersion\":2", "\"schemaVersion\":1");
        for (var format : prepared.plan().formats()) {
            legacy =
                    legacy.replace(
                            "\"specificationId\":" + format.article().specificationId(),
                            "\"specificationId\":987654321");
        }
        final String snapshot = legacy;
        db.withTransaction(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "UPDATE TON.PIANI_PRODUZIONE SET contenuto_piano=? WHERE"
                                        + " piano_id=?")) {
                        q.setString(1, snapshot);
                        q.setLong(2, prepared.id());
                        q.executeUpdate();
                    }
                    return null;
                });
        new DiskModelService(db, workers).retire(fixture.model.revision()).join();
        var reopened = fixture.service.load(prepared.id()).join();
        assertEquals(State.PREPARATA, reopened.state());
        assertEquals(1, reopened.plan().schemaVersion());
        assertEquals(987654321L, reopened.plan().formats().getFirst().article().specificationId());
        assertEquals(prepared.plan().needed(), reopened.plan().needed());
        assertEquals(prepared.plan().mixtures(), reopened.plan().mixtures());
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
        assertEquals(3, count("PIANO_PRODOTTO_RIGHE"));
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

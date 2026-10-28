package com.orodent.ecos.ton.features.catalog.service;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;
import com.orodent.ecos.ton.features.catalog.repository.*;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class CatalogServiceTest {
    private String url, previousLog;
    private ConnectionProvider db;
    private ExecutorService workers;
    private CatalogService service;
    private SpecificationService specs;
    private final AtomicInteger opened = new AtomicInteger(), onWorker = new AtomicInteger();

    @BeforeEach
    void open() throws Exception {
        previousLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/ton-catalogues.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:catalogue_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        workers = Executors.newFixedThreadPool(2, r -> new Thread(r, "catalogue-worker"));
        ConnectionProvider tracked =
                () -> {
                    opened.incrementAndGet();
                    if (Thread.currentThread().getName().equals("catalogue-worker"))
                        onWorker.incrementAndGet();
                    return db.openConnection();
                };
        service = new CatalogService(tracked, workers);
        specs = new SpecificationService(tracked, workers);
    }

    @AfterEach
    void close() throws Exception {
        workers.shutdown();
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        } finally {
            if (previousLog == null) System.clearProperty("derby.stream.error.file");
            else System.setProperty("derby.stream.error.file", previousLog);
        }
    }

    @Test
    void hierarchyInheritsLineAndColorAndAllIoRunsOnBorrowedWorkers() {
        Line line = line();
        Product product = product(line, "A2");
        Article article = article(product, "ZR9816MSTA2");
        assertEquals(line.id(), article.product().lineId());
        assertEquals("A2", article.product().color());
        assertTrue(article.available());
        assertEquals(
                1, service.history(CatalogKind.ARTICLES, article.id(), 0).join().rows().size());
        assertEquals(
                "CREAZIONE",
                service.history(CatalogKind.LINES, line.id(), 0).join().rows().getFirst().source());
        assertEquals(opened.get(), onWorker.get());
    }

    @Test
    void uniquenessAppliesToLineColorAndArticleFormatWithExactMeasurements() {
        Line l = line();
        Product p = product(l, " a2 ");
        assertEquals("A2", p.color());
        assertThrows(
                CompletionException.class,
                () ->
                        service.create(
                                        new ProductDraft(
                                                "OTHER", "Duplicato", l.id(), "A2", "", true))
                                .join());
        Article a = article(p, "ONE");
        assertThrows(
                CompletionException.class,
                () ->
                        service.create(
                                        new ArticleDraft(
                                                "TWO",
                                                "",
                                                p.id(),
                                                new BigDecimal("98.00"),
                                                new BigDecimal("16.0"),
                                                "",
                                                true))
                                .join());
        assertEquals(1, service.page(CatalogKind.ARTICLES, Filter.empty(), 0).join().total());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ArticleDraft(
                                "BAD",
                                "",
                                p.id(),
                                new BigDecimal("98.001"),
                                BigDecimal.ONE,
                                "",
                                true));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(new ProductDraft("BAD", "", null, null, "", true)));
    }

    @Test
    void savesCompleteHistoryAndNoOpDoesNotCreateFakeRevisions() {
        Line l = line();
        LineDraft changed =
                new LineDraft(
                        l.code(), "Nome corretto", l.technology(), l.material(), "Nota", true);
        Line updated = (Line) service.save(l, changed).join().entry();
        var history = service.history(CatalogKind.LINES, l.id(), 0).join();
        assertEquals(2, history.total());
        assertEquals("Linea", history.rows().get(1).snapshot().name());
        assertEquals("Nota", history.rows().getFirst().snapshot().meta().notes());
        Line again = (Line) service.save(updated, changed).join().entry();
        assertEquals(updated.revision(), again.revision());
        assertEquals(2, service.history(CatalogKind.LINES, l.id(), 0).join().total());
    }

    @Test
    void deletionIsOnlyForUnusedEntriesAndArchivingPreservesChildren() {
        Line l = line();
        Product p = product(l, "A2");
        Article a = article(p, "ONE");
        assertFalse(service.load(CatalogKind.PRODUCTS, p.id()).join().deletable());
        assertThrows(CompletionException.class, () -> service.delete(p).join());
        service.delete(a).join();
        assertTrue(service.load(CatalogKind.PRODUCTS, p.id()).join().deletable());
        service.delete(p).join();
        assertTrue(service.load(CatalogKind.LINES, l.id()).join().deletable());
        Line archived = (Line) service.setActive(l, false).join().entry();
        assertEquals(2, service.history(CatalogKind.LINES, l.id(), 0).join().total());
        assertFalse(service.load(CatalogKind.LINES, l.id()).join().deletable());
        assertThrows(CompletionException.class, () -> service.delete(archived).join());
        assertFalse(
                service.setActive(archived, true).join().entry().meta().notes().contains("fake"));
    }

    @Test
    void parentArchiveRemovesChoicesWithoutErasingExistingChildren() {
        Line l = line();
        Product p = product(l, "A2");
        Article a = article(p, "ONE");
        service.setActive(l, false).join();
        assertFalse(service.load(CatalogKind.ARTICLES, a.id()).join().entry().available());
        assertTrue(service.choices(CatalogKind.PRODUCTS, "", null).join().isEmpty());
        assertThrows(CompletionException.class, () -> article(p, "TWO"));
        assertEquals(1, service.page(CatalogKind.ARTICLES, Filter.empty(), 0).join().total());
        assertFalse(
                service.save(
                                p,
                                new ProductDraft(
                                        p.code(), "Correzione", l.id(), p.color(), "", true))
                        .join()
                        .entry()
                        .available());
    }

    @Test
    void structuralChangesAreBlockedAfterUseAndHistoricalCodesCannotBeReassigned() {
        Line l = line();
        Product p = product(l, "A2");
        Article a = article(p, "OLD");
        Line used = (Line) service.load(CatalogKind.LINES, l.id()).join().entry();
        Product usedP = (Product) service.load(CatalogKind.PRODUCTS, p.id()).join().entry();
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(
                                        used,
                                        new LineDraft(
                                                l.code(),
                                                l.name(),
                                                "Altra tecnologia",
                                                l.material(),
                                                "",
                                                true))
                                .join());
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(
                                        usedP,
                                        new ProductDraft(
                                                p.code(), p.name(), l.id(), "A3", "", true))
                                .join());
        service.save(
                        a,
                        new ArticleDraft(
                                "NEW", a.name(), p.id(), a.diameter(), a.height(), "", true))
                .join();
        assertThrows(
                CompletionException.class,
                () ->
                        service.create(
                                        new ArticleDraft(
                                                "OLD",
                                                "",
                                                p.id(),
                                                new BigDecimal("95"),
                                                a.height(),
                                                "",
                                                true))
                                .join());
    }

    @Test
    void concurrentEditsHaveOneWinnerAndOneCompleteAuditSnapshot() throws Exception {
        Line l = line();
        var start = new CountDownLatch(1);
        CompletableFuture<Detail> one =
                CompletableFuture.supplyAsync(
                        () -> {
                            await(start);
                            return service.save(
                                            l,
                                            new LineDraft(
                                                    l.code(),
                                                    "Prima",
                                                    l.technology(),
                                                    l.material(),
                                                    "A",
                                                    true))
                                    .join();
                        });
        CompletableFuture<Detail> two =
                CompletableFuture.supplyAsync(
                        () -> {
                            await(start);
                            return service.save(
                                            l,
                                            new LineDraft(
                                                    l.code(),
                                                    "Seconda",
                                                    l.technology(),
                                                    l.material(),
                                                    "B",
                                                    true))
                                    .join();
                        });
        start.countDown();
        CompletableFuture.allOf(one.handle((v, e) -> null), two.handle((v, e) -> null))
                .get(10, TimeUnit.SECONDS);
        assertNotEquals(one.isCompletedExceptionally(), two.isCompletedExceptionally());
        assertEquals(2, service.history(CatalogKind.LINES, l.id(), 0).join().total());
        Entry current = service.load(CatalogKind.LINES, l.id()).join().entry();
        assertTrue(
                current.name().equals("Prima") && current.meta().notes().equals("A")
                        || current.name().equals("Seconda") && current.meta().notes().equals("B"));
    }

    @Test
    void aLateAuditFailureRollsBackMetadataAndOptimisticRevision() {
        Line l = line();
        execute(
                "INSERT INTO"
                    + " TON.LINEE_REVISIONI(linea_id,numero_revisione,codice,nome,attivo,origine)"
                    + " VALUES (1,2,'COLLISION','Collisione',TRUE,'MODIFICA')");
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(
                                        l,
                                        new LineDraft(
                                                "RENAMED",
                                                "Da annullare",
                                                l.technology(),
                                                l.material(),
                                                "",
                                                true))
                                .join());
        assertEquals(l, service.load(CatalogKind.LINES, l.id()).join().entry());
    }

    @Test
    void legacyProductsCanBeCompletedWithoutGuessingOrReplacingTheirIdentity() {
        Line l = line();
        execute(
                "INSERT INTO TON.PRODOTTI(codice) VALUES ('LEGACY')",
                "INSERT INTO TON.LINEE_PRODOTTI VALUES (1,1)");
        db.withTransaction(
                c -> {
                    var entry = new CatalogRepository(c).load(CatalogKind.PRODUCTS, 1);
                    new CatalogHistoryRepository(c).record(entry, "MIGRAZIONE");
                    return null;
                });
        Detail original = service.load(CatalogKind.PRODUCTS, 1).join();
        assertFalse(original.entry().complete());
        assertEquals(1, original.previousLines().size());
        Product p =
                (Product)
                        service.save(
                                        original.entry(),
                                        new ProductDraft(
                                                "LEGACY",
                                                "Completato",
                                                l.id(),
                                                "Senza colore",
                                                "",
                                                true))
                                .join()
                                .entry();
        assertEquals(1, p.id());
        assertEquals("SENZA_COLORE", p.color());
        assertTrue(p.complete());
        assertNull(
                ((Product)
                                service.history(CatalogKind.PRODUCTS, 1, 0)
                                        .join()
                                        .rows()
                                        .get(1)
                                        .snapshot())
                        .line());
    }

    @Test
    void filteringPaginationAndLiteralSearchAreEvaluatedByTheDatabase() {
        Line l = line();
        for (int i = 0; i < 52; i++)
            service.create(
                            new ProductDraft(
                                    "P%03d".formatted(i), "Prodotto", l.id(), "X" + i, "", true))
                    .join();
        assertEquals(
                50, service.page(CatalogKind.PRODUCTS, Filter.empty(), 0).join().rows().size());
        assertEquals(
                2, service.page(CatalogKind.PRODUCTS, Filter.empty(), 50).join().rows().size());
        service.create(new ProductDraft("SPECIAL%_\\'", "Speciale", l.id(), "SPECIAL", "", true))
                .join();
        for (String filter : List.of("%", "_", "\\", "'"))
            assertEquals(
                    1,
                    service.page(
                                    CatalogKind.PRODUCTS,
                                    new Filter(filter, false, null, null, null, null),
                                    0)
                            .join()
                            .total());
        Product p =
                (Product)
                        service.page(
                                        CatalogKind.PRODUCTS,
                                        new Filter("SPECIAL", false, null, null, null, null),
                                        0)
                                .join()
                                .rows()
                                .getFirst();
        Article a = article(p, "FILTER");
        assertEquals(
                1,
                service.page(
                                CatalogKind.ARTICLES,
                                new Filter("", false, l.id(), p.id(), a.diameter(), a.height()),
                                0)
                        .join()
                        .total());
        assertEquals(
                0,
                service.page(
                                CatalogKind.ARTICLES,
                                new Filter("", false, l.id(), p.id(), new BigDecimal("95"), null),
                                0)
                        .join()
                        .total());
    }

    @Test
    void approvalFreezesTechnicalVersionsAndCapturesOriginalCatalogueSnapshots() {
        Line l = line();
        Product p = product(l, "A2");
        Article a = article(p, "ONE");
        technical(p.id());
        Detail approved =
                specs.save(a, null, new SpecificationDraft(1, 1, "dosi-v1", "Prima"), true).join();
        Article current = (Article) approved.entry();
        Specification version = specs.page(current, 0, "", "").join().rows().getFirst();
        assertTrue(version.recorded());
        assertTrue(version.preferred());
        assertEquals(1, version.articleRevision());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        specs.save(
                                current,
                                version,
                                new SpecificationDraft(1, 1, "other", ""),
                                false));
        Article renamed =
                (Article)
                        service.save(
                                        current,
                                        new ArticleDraft(
                                                current.code(),
                                                "Descrizione nuova",
                                                p.id(),
                                                a.diameter(),
                                                a.height(),
                                                "",
                                                true))
                                .join()
                                .entry();
        assertEquals(
                "",
                service.history(CatalogKind.ARTICLES, a.id(), 0).join().rows().stream()
                        .filter(x -> x.number() == version.articleRevision())
                        .findFirst()
                        .orElseThrow()
                        .snapshot()
                        .name());
        specs.copy(renamed, version).join();
        var rows = specs.page(renamed, 0, "", "").join().rows();
        assertEquals("BOZZA", rows.getFirst().state());
        assertEquals("Prima", rows.get(1).notes());
        assertThrows(CompletionException.class, () -> specs.copy(renamed, version).join());
        specs.retire(renamed, version).join();
        Article retired = (Article) service.load(CatalogKind.ARTICLES, a.id()).join().entry();
        assertNull(retired.defaultSpecification());
        assertEquals(2, specs.page(retired, 0, "", "").join().total());
    }

    @Test
    void invalidDependenciesPreventApprovalAndRollBackNewVersions() {
        Line l = line();
        Product p = product(l, "A2");
        Article a = article(p, "ONE");
        technical(p.id());
        execute("UPDATE TON.MODELLO_STRATI SET percentuale_altezza=90");
        assertThrows(
                CompletionException.class,
                () -> specs.save(a, null, new SpecificationDraft(1, 1, "v1", ""), true).join());
        assertEquals(0, specs.page(a, 0, "", "").join().total());
        execute(
                "UPDATE TON.MODELLO_STRATI SET percentuale_altezza=100",
                "UPDATE TON.POLVERI SET attivo=FALSE");
        assertThrows(
                CompletionException.class,
                () -> specs.save(a, null, new SpecificationDraft(1, 1, "v1", ""), true).join());
        assertEquals(0, specs.page(a, 0, "", "").join().total());
    }

    @Test
    void concurrentVersionCreationAllocatesOnlyOneDraft() throws Exception {
        Line l = line();
        Product p = product(l, "A2");
        Article a = article(p, "ONE");
        technical(p.id());
        Article current =
                (Article)
                        specs.save(a, null, new SpecificationDraft(1, 1, "v1", ""), true)
                                .join()
                                .entry();
        Specification approved = specs.page(current, 0, "", "").join().rows().getFirst();
        var start = new CountDownLatch(1);
        CompletableFuture<Detail> one =
                CompletableFuture.supplyAsync(
                        () -> {
                            await(start);
                            return specs.copy(current, approved).join();
                        });
        CompletableFuture<Detail> two =
                CompletableFuture.supplyAsync(
                        () -> {
                            await(start);
                            return specs.copy(current, approved).join();
                        });
        start.countDown();
        CompletableFuture.allOf(one.handle((v, e) -> null), two.handle((v, e) -> null))
                .get(10, TimeUnit.SECONDS);
        assertNotEquals(one.isCompletedExceptionally(), two.isCompletedExceptionally());
        var versions = specs.page(current, 0, "", "").join().rows();
        assertEquals(2, versions.size());
        assertEquals(2, versions.getFirst().number());
        assertEquals("BOZZA", versions.getFirst().state());
    }

    @Test
    void commandsCannotUseAnotherArticlesVersionEvenWithAForgedClientSnapshot() {
        Line l = line();
        Product p = product(l, "A2");
        Article one = article(p, "ONE");
        technical(p.id());
        Article approved =
                (Article)
                        specs.save(one, null, new SpecificationDraft(1, 1, "v1", ""), true)
                                .join()
                                .entry();
        Specification v = specs.page(approved, 0, "", "").join().rows().getFirst();
        Article other =
                (Article)
                        service.create(
                                        new ArticleDraft(
                                                "OTHER",
                                                "",
                                                p.id(),
                                                new BigDecimal("95"),
                                                new BigDecimal("16"),
                                                "",
                                                true))
                                .join()
                                .entry();
        Specification forged =
                new Specification(
                        v.id(),
                        other.id(),
                        v.number(),
                        v.revision(),
                        v.state(),
                        v.compositionId(),
                        v.compositionLabel(),
                        v.modelId(),
                        v.modelLabel(),
                        v.calculation(),
                        v.notes(),
                        v.approvedAt(),
                        v.lineRevision(),
                        v.productRevision(),
                        v.articleRevision(),
                        false);
        assertThrows(CompletionException.class, () -> specs.retire(other, forged).join());
        assertThrows(CompletionException.class, () -> specs.copy(other, forged).join());
        assertThrows(CompletionException.class, () -> specs.prefer(other, forged).join());
        assertEquals("APPROVATA", specs.page(approved, 0, "", "").join().rows().getFirst().state());
        assertEquals(0, specs.page(other, 0, "", "").join().total());
    }

    @Test
    void layerNumbersMustMatchAndRetirementIsAllowedAfterArchiving() {
        Line l = line();
        Product p = product(l, "A2");
        Article a = article(p, "ONE");
        technical(p.id());
        execute("UPDATE TON.MODELLO_STRATI SET numero_strato=2");
        assertThrows(
                CompletionException.class,
                () -> specs.save(a, null, new SpecificationDraft(1, 1, "v1", ""), true).join());
        execute("UPDATE TON.MODELLO_STRATI SET numero_strato=1");
        Article approved =
                (Article)
                        specs.save(a, null, new SpecificationDraft(1, 1, "v1", ""), true)
                                .join()
                                .entry();
        Specification v = specs.page(approved, 0, "", "").join().rows().getFirst();
        Article archived = (Article) service.setActive(approved, false).join().entry();
        Article retired = (Article) specs.retire(archived, v).join().entry();
        assertFalse(retired.active());
        assertNull(retired.defaultSpecification());
        assertEquals("RITIRATA", specs.page(retired, 0, "", "").join().rows().getFirst().state());
    }

    @Test
    void legacySpecificationsNeedANewApprovalAndCannotTrustInventedClientReferences() {
        Line l = line();
        Product p = product(l, "A2");
        Article a = article(p, "ONE");
        technical(p.id());
        execute(
                "INSERT INTO"
                    + " TON.CONFIGURAZIONI_ARTICOLO(articolo_id,prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,stato,approvato_il)"
                    + " VALUES ("
                        + a.id()
                        + ","
                        + p.id()
                        + ",1,1,1,'legacy','APPROVATA',CURRENT_TIMESTAMP)");
        Specification old = specs.page(a, 0, "", "").join().rows().getFirst();
        assertFalse(old.recorded());
        Specification invented =
                new Specification(
                        old.id(),
                        old.articleId(),
                        old.number(),
                        old.revision(),
                        old.state(),
                        old.compositionId(),
                        old.compositionLabel(),
                        old.modelId(),
                        old.modelLabel(),
                        old.calculation(),
                        old.notes(),
                        old.approvedAt(),
                        1,
                        1,
                        1,
                        false);
        assertThrows(CompletionException.class, () -> specs.prefer(a, invented).join());
        assertNull(
                ((Article) service.load(CatalogKind.ARTICLES, a.id()).join().entry())
                        .defaultSpecification());
        specs.copy(a, old).join();
        Specification draft = specs.page(a, 0, "", "").join().rows().getFirst();
        Article current =
                (Article)
                        specs.save(a, draft, new SpecificationDraft(1, 1, "v1", "Verificata"), true)
                                .join()
                                .entry();
        Specification approved = specs.page(current, 0, "", "").join().rows().getFirst();
        assertTrue(approved.recorded());
        assertTrue(approved.preferred());
        assertEquals(2, approved.number());
        assertFalse(specs.page(current, 0, "", "").join().rows().get(1).recorded());
    }

    private Line line() {
        return (Line)
                service.create(new LineDraft("ZR-MST", "Linea", "Multistrato", "ZR", "", true))
                        .join()
                        .entry();
    }

    private Product product(Line l, String color) {
        return (Product)
                service.create(
                                new ProductDraft(
                                        "P" + color.strip().toUpperCase(Locale.ROOT),
                                        "Prodotto",
                                        l.id(),
                                        color,
                                        "",
                                        true))
                        .join()
                        .entry();
    }

    private Article article(Product p, String code) {
        return (Article)
                service.create(
                                new ArticleDraft(
                                        code,
                                        "",
                                        p.id(),
                                        new BigDecimal("98"),
                                        new BigDecimal("16"),
                                        "",
                                        true))
                        .join()
                        .entry();
    }

    private void technical(long product) {
        execute(
                "INSERT INTO TON.POLVERI(codice,nome) VALUES ('WHITE','Bianca')",
                "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione,stato,approvato_il)"
                        + " VALUES (1,1,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione,stato,approvato_il)"
                        + " VALUES ("
                        + product
                        + ",1,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.COMPOSIZIONE_STRATI VALUES (1,1,'Unico')",
                "INSERT INTO TON.COMPOSIZIONE_INGREDIENTI VALUES (1,1,1,100)",
                "INSERT INTO"
                    + " TON.MODELLI_DISCO(codice,numero_versione,diametro_pressatura_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm,pressione_kg_cm2,grammi_per_mm,stato,approvato_il)"
                    + " VALUES ('MODEL',1,98,1,1,1000,20,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.MODELLO_STRATI VALUES (1,1,100)");
    }

    private void execute(String... sql) {
        db.withTransaction(
                c -> {
                    for (String s : sql)
                        try (var q = c.createStatement()) {
                            q.executeUpdate(s);
                        }
                    return null;
                });
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

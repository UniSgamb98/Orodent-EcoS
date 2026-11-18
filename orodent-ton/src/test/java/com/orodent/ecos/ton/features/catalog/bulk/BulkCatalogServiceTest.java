package com.orodent.ecos.ton.features.catalog.bulk;

import static com.orodent.ecos.ton.features.catalog.bulk.model.BulkCatalogModels.*;
import static com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.ton.features.catalog.bulk.model.CodeComposition;
import com.orodent.ecos.ton.features.catalog.bulk.service.BulkCatalogService;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.service.CatalogService;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

class BulkCatalogServiceTest {
    String url;
    ConnectionProvider db;
    ExecutorService workers;
    CatalogService single;
    BulkCatalogService bulk;

    @BeforeEach
    void open() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:bulk_" + UUID.randomUUID().toString().replace("-", "");
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
        single = new CatalogService(db, workers);
        bulk = new BulkCatalogService(db, workers);
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

    Line line(String code, String material) {
        return (Line)
                single.create(new LineDraft(code, "Linea " + code, "", material, "", true))
                        .join()
                        .entry();
    }

    Parent snapshot(Entry entry) {
        return bulk.parentsByIds(entry.kind(), List.of(entry.id())).join().getFirst();
    }

    Seed product(int key, Parent line, String code, String color) {
        return new Seed(key, line, code, color, "", "", true);
    }

    Seed article(int key, Parent product, String code, String h) {
        return new Seed(key, product, code, "", "98", h, true);
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
    void createsProductsWithCustomCodesAndPreservesThemDuringSingleEdits() {
        Parent line = snapshot(line("MST", "ZR"));
        var rows = new ArrayList<Seed>();
        for (int i = 0; i < 10; i++) rows.add(product(i, line, "ZR-MST-C" + i, "C" + i));
        Preview preview =
                bulk.preview(new Request(CatalogKind.PRODUCTS, "Nota comune", rows)).join();
        assertEquals(10, preview.creating());
        Created result = bulk.create(preview).join();
        assertEquals(10, result.entries().size());
        assertEquals(10, count("PRODOTTI_REVISIONI"));
        for (Entry entry : result.entries()) {
            Product p = (Product) entry;
            assertEquals(line.id(), p.line().id());
            assertEquals("Nota comune", p.meta().notes());
        }
        Product first = (Product) result.entries().getFirst();
        Product edited =
                (Product)
                        single.save(
                                        first,
                                        ProductDraft.configured(
                                                line.id(), first.color(), "Modificata", true))
                                .join()
                                .entry();
        assertEquals(first.code(), edited.code());
    }

    @Test
    void createsArticlesForSeveralProductsWithTheirOwnMaterialAndSkipsExactExisting() {
        Line zr = line("MST", "ZR"), zs = line("ZST", "ZS");
        var parents = new ArrayList<Parent>();
        for (int i = 1; i <= 3; i++) {
            Line l = i == 3 ? zs : zr;
            parents.add(
                    snapshot(
                            single.create(ProductDraft.configured(l.id(), "A" + i, "", true))
                                    .join()
                                    .entry()));
        }
        Parent first = parents.getFirst();
        single.create(
                        new ArticleDraft(
                                "ZR9812" + first.code(),
                                "Esistente",
                                first.id(),
                                new BigDecimal("98"),
                                new BigDecimal("12"),
                                "Non cambiare",
                                true))
                .join();
        var seeds = new ArrayList<Seed>();
        int key = 0;
        for (Parent p : parents)
            for (int h : List.of(12, 14, 16, 18, 20)) {
                String code =
                        CodeComposition.defaults(CatalogKind.ARTICLES)
                                .code(
                                        new CodeComposition.Values(
                                                p.material(),
                                                p.lineCode(),
                                                p.color(),
                                                p.code(),
                                                new BigDecimal("98"),
                                                BigDecimal.valueOf(h)));
                seeds.add(article(key++, p, code, Integer.toString(h)));
            }
        Preview preview = bulk.preview(new Request(CatalogKind.ARTICLES, "Nuovi", seeds)).join();
        assertEquals(14, preview.creating());
        assertEquals(1, preview.existing());
        Created result = bulk.create(preview).join();
        assertEquals(14, result.entries().size());
        assertEquals(15, count("ARTICOLI"));
        assertEquals(0, count("CONFIGURAZIONI_ARTICOLO"));
        for (Entry e : result.entries()) {
            Article a = (Article) e;
            Parent p =
                    parents.stream()
                            .filter(x -> x.id() == a.product().id())
                            .findFirst()
                            .orElseThrow();
            assertTrue(a.code().startsWith(p.material()));
            assertEquals("Nuovi", a.meta().notes());
        }
        assertEquals(15, count("ARTICOLI_REVISIONI"));
    }

    @Test
    void rejectsDuplicateCodesNaturalKeysInvalidMeasuresAndHonorsExclusions() {
        Parent l = snapshot(line("MST", "ZR"));
        var rows = List.of(product(0, l, "DUP", "A1"), product(1, l, "dup", "A2"));
        assertEquals(2, bulk.preview(new Request(CatalogKind.PRODUCTS, "", rows)).join().invalid());
        rows = List.of(product(0, l, "FIRST", "A1"), new Seed(1, l, "first", "A2", "", "", false));
        Preview good = bulk.preview(new Request(CatalogKind.PRODUCTS, "", rows)).join();
        assertTrue(good.savable());
        bulk.create(good).join();
        assertEquals(1, count("PRODOTTI"));
        Parent p =
                snapshot(
                        single.load(
                                        CatalogKind.PRODUCTS,
                                        bulk.parents(CatalogKind.PRODUCTS, "", null, 0)
                                                .join()
                                                .rows()
                                                .getFirst()
                                                .id())
                                .join()
                                .entry());
        var invalid =
                bulk.preview(
                                new Request(
                                        CatalogKind.ARTICLES,
                                        "",
                                        List.of(
                                                article(0, p, "BAD", "-1"),
                                                article(1, p, "TOO-PRECISE", "12,001"))))
                        .join();
        assertEquals(2, invalid.invalid());
    }

    @Test
    void rollsBackTheWholeGroupIncludingHistoryIfAnInsertFails() {
        Parent p =
                snapshot(
                        single.create(
                                        ProductDraft.configured(
                                                line("MST", "ZR").id(), "A1", "", true))
                                .join()
                                .entry());
        Preview preview =
                bulk.preview(
                                new Request(
                                        CatalogKind.ARTICLES,
                                        "",
                                        List.of(
                                                article(0, p, "OK", "12"),
                                                article(1, p, "FAIL", "14"))))
                        .join();
        assertEquals(2, preview.creating());
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "ALTER TABLE TON.ARTICOLI ADD CONSTRAINT test_bulk_failure"
                                    + " CHECK(codice<>'FAIL')");
                    }
                    return null;
                });
        assertThrows(CompletionException.class, () -> bulk.create(preview).join());
        assertEquals(0, count("ARTICOLI"));
        assertEquals(0, count("ARTICOLI_REVISIONI"));
    }

    @Test
    void revalidatesConcurrentCreationsAndParentChanges() {
        Line line = line("MST", "ZR");
        Parent l = snapshot(line);
        Preview preview =
                bulk.preview(
                                new Request(
                                        CatalogKind.PRODUCTS,
                                        "",
                                        List.of(
                                                product(0, l, "MSTA1", "A1"),
                                                product(1, l, "MSTA2", "A2"))))
                        .join();
        single.create(ProductDraft.configured(line.id(), "A1", "", true)).join();
        assertThrows(CompletionException.class, () -> bulk.create(preview).join());
        assertEquals(1, count("PRODOTTI"));
        Preview pending =
                bulk.preview(
                                new Request(
                                        CatalogKind.PRODUCTS,
                                        "",
                                        List.of(product(0, l, "MSTA2", "A2"))))
                        .join();
        single.save(
                        (Line) single.load(CatalogKind.LINES, line.id()).join().entry(),
                        new LineDraft("MST", "Modificata", "", "ZR", "", true))
                .join();
        assertThrows(CompletionException.class, () -> bulk.create(pending).join());
        assertEquals(1, count("PRODOTTI"));
    }

    @Test
    void preventsHistoricalReuseAndReportsArchivedAndNaturalKeyConflicts() {
        Line line = line("MST", "ZR");
        Parent l = snapshot(line);
        Product old =
                (Product)
                        single.create(new ProductDraft("OLD", "", line.id(), "A1", "", true))
                                .join()
                                .entry();
        single.save(old, new ProductDraft("NEW", "", line.id(), "A1", "", true)).join();
        var preview =
                bulk.preview(
                                new Request(
                                        CatalogKind.PRODUCTS,
                                        "",
                                        List.of(
                                                product(0, l, "OLD", "A2"),
                                                product(1, l, "OTHER", "A1"))))
                        .join();
        assertEquals(2, preview.invalid());
        assertTrue(preview.rows().getFirst().message().contains("storico"));
        Product current = (Product) single.load(CatalogKind.PRODUCTS, old.id()).join().entry();
        single.setActive(current, false).join();
        var same =
                bulk.preview(
                                new Request(
                                        CatalogKind.PRODUCTS,
                                        "",
                                        List.of(product(0, l, "NEW", "A1"))))
                        .join();
        assertEquals(1, same.existing());
        assertTrue(same.rows().getFirst().message().contains("archiviato"));
    }

    @Test
    void concurrentArticleBatchesCommitOnlyOnce() throws Exception {
        Parent p =
                snapshot(
                        single.create(
                                        ProductDraft.configured(
                                                line("MST", "ZR").id(), "A1", "", true))
                                .join()
                                .entry());
        Preview preview =
                bulk.preview(
                                new Request(
                                        CatalogKind.ARTICLES,
                                        "",
                                        List.of(
                                                article(0, p, "FIRST", "12"),
                                                article(1, p, "SECOND", "14"))))
                        .join();
        CompletableFuture<Boolean> first =
                bulk.create(preview).handle((created, error) -> error == null);
        CompletableFuture<Boolean> second =
                bulk.create(preview).handle((created, error) -> error == null);
        assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        assertEquals(2, count("ARTICOLI"));
        assertEquals(2, count("ARTICOLI_REVISIONI"));
    }

    @Test
    void productSearchIncludesColorWhenCustomNamesAndCodesOmitIt() {
        Line l = line("MST", "ZR");
        Product p =
                (Product)
                        single.create(
                                        new ProductDraft(
                                                "CUSTOM",
                                                "Nome commerciale",
                                                l.id(),
                                                "B2",
                                                "",
                                                true))
                                .join()
                                .entry();
        assertEquals(
                List.of(p.id()),
                bulk.parents(CatalogKind.PRODUCTS, "b2", null, 0).join().rows().stream()
                        .map(Parent::id)
                        .toList());
        assertTrue(bulk.parents(CatalogKind.PRODUCTS, "%", null, 0).join().rows().isEmpty());
    }
}

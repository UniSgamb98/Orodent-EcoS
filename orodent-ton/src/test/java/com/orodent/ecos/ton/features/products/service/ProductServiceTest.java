package com.orodent.ecos.ton.features.products.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.ton.features.products.model.ProductConflictException;
import com.orodent.ecos.ton.features.products.model.ProductDraft;
import com.orodent.ecos.ton.features.products.model.TonProduct;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProductServiceTest {
    private String url;
    private String previousLog;
    private ProductService service;
    private final AtomicInteger opened = new AtomicInteger();
    private final AtomicInteger onWorker = new AtomicInteger();
    private ExecutorService workers;

    @BeforeEach
    void open() throws Exception {
        previousLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/ton-products-derby.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:ton_products_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider database = () -> {
            try { return DriverManager.getConnection(url + ";create=true", "APP", "pw"); }
            catch (SQLException failure) { throw new IllegalStateException(failure); }
        };
        new DatabaseSchema(database).initialize();
        workers = Executors.newFixedThreadPool(2, runnable -> new Thread(runnable, "ton-test-worker"));
        ConnectionProvider tracked = () -> {
            opened.incrementAndGet();
            if (Thread.currentThread().getName().equals("ton-test-worker")) onWorker.incrementAndGet();
            return database.openConnection();
        };
        service = new ProductService(tracked, workers);
    }

    @AfterEach
    void close() throws Exception {
        workers.shutdown();
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        try { DriverManager.getConnection(url + ";drop=true", "APP", "pw"); }
        catch (SQLException expected) { assertEquals("08006", expected.getSQLState()); }
        if (previousLog == null) System.clearProperty("derby.stream.error.file");
        else System.setProperty("derby.stream.error.file", previousLog);
    }

    @Test
    void normalizesTechnicalCodesAndExecutesDatabaseWorkOnBorrowedWorkers() {
        TonProduct product = service.create(new ProductDraft("  zr a2 ", "  Zirconia A2  ", true)).join();
        assertTrue(product.id() > 0);
        assertEquals("ZR A2", product.code());
        assertEquals("Zirconia A2", product.description());
        assertEquals(1, product.revision());
        assertNotNull(product.createdAt());
        assertEquals(List.of(product), service.findPage("zirconia", false, 0).join().products());
        assertEquals(opened.get(), onWorker.get());
        assertEquals(2, opened.get());
    }

    @Test
    void rejectsInvalidDraftsBeforeAnyConnectionIsOpened() {
        assertThrows(IllegalArgumentException.class, () -> new ProductDraft("   ", "", true));
        assertThrows(IllegalArgumentException.class, () -> new ProductDraft("A".repeat(51), "", true));
        assertThrows(IllegalArgumentException.class, () -> new ProductDraft("A", "B".repeat(201), true));
        assertEquals(0, opened.get());
    }

    @Test
    void duplicateCodeRollsBackAnEditWithoutLosingThePreviousProduct() {
        TonProduct first = service.create(new ProductDraft("A", "First", true)).join();
        TonProduct second = service.create(new ProductDraft("B", "Second", true)).join();
        CompletionException failure = assertThrows(CompletionException.class,
                () -> service.update(second, new ProductDraft("a", "Overwrite", false)).join());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("codice"));
        assertEquals(List.of(first, second), service.findPage("", true, 0).join().products());
    }

    @Test
    void concurrentEditsHaveOneWinnerAndReportAConflictInsteadOfOverwritingIt() {
        TonProduct initial = service.create(new ProductDraft("ZR A2", "Original", true)).join();
        CompletableFuture<TonProduct> first = service.update(initial, new ProductDraft("ZR A2", "Operator A", true));
        CompletableFuture<TonProduct> second = service.update(initial, new ProductDraft("ZR A2", "Operator B", true));
        List<Throwable> outcomes = java.util.Arrays.asList(first.handle((p, e) -> e).join(), second.handle((p, e) -> e).join());
        assertEquals(1, outcomes.stream().filter(java.util.Objects::nonNull).count());
        Throwable failure = outcomes.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        assertInstanceOf(ProductConflictException.class, failure.getCause());
        TonProduct saved = service.findPage("", false, 0).join().products().getFirst();
        assertEquals(2, saved.revision());
        assertEquals(initial.id(), saved.id());
        assertEquals(initial.createdAt(), saved.createdAt());
        assertTrue(List.of("Operator A", "Operator B").contains(saved.description()));
    }

    @Test
    void archivingAndReactivatingKeepTheStableIdentity() {
        TonProduct created = service.create(new ProductDraft("ZR A2", "Materiale", true)).join();
        TonProduct archived = service.update(created, new ProductDraft(created.code(), created.description(), false)).join();
        assertEquals(0, service.findPage("", false, 0).join().total());
        assertEquals(List.of(archived), service.findPage("", true, 0).join().products());
        TonProduct restored = service.update(archived, new ProductDraft(archived.code(), archived.description(), true)).join();
        assertEquals(created.id(), restored.id());
        assertEquals(3, restored.revision());
        assertEquals(List.of(restored), service.findPage("", false, 0).join().products());
    }

    @Test
    void paginatesDeterministicallyAndTreatsSearchWildcardsAsLiteralCharacters() {
        for (int i = 0; i < 52; i++) service.create(new ProductDraft("P%03d".formatted(i), "Product", true)).join();
        var first = service.findPage("P", false, 0).join();
        var second = service.findPage("P", false, ProductService.PAGE_SIZE).join();
        assertEquals(52, first.total());
        assertEquals(50, first.products().size());
        assertTrue(first.hasNext());
        assertEquals(List.of("P050", "P051"), second.products().stream().map(TonProduct::code).toList());
        assertFalse(second.hasNext());
        TonProduct literal = service.create(new ProductDraft("MIX%_\\A'", "Literal", true)).join();
        for (String filter : List.of("%", "_", "\\", "'"))
            assertEquals(List.of(literal), service.findPage(filter, true, 0).join().products());
    }
}

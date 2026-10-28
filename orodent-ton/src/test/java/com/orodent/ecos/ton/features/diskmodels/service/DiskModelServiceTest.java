package com.orodent.ecos.ton.features.diskmodels.service;

import static com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class DiskModelServiceTest {
    private String url, previousLog;
    private ConnectionProvider db;
    private ExecutorService workers;
    private DiskModelService service;
    private final AtomicInteger opened = new AtomicInteger(), onWorker = new AtomicInteger();

    @BeforeEach
    void open() throws Exception {
        previousLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/ton-models.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:models_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        workers = Executors.newFixedThreadPool(2, r -> new Thread(r, "models-worker"));
        service =
                new DiskModelService(
                        () -> {
                            opened.incrementAndGet();
                            if (Thread.currentThread().getName().equals("models-worker"))
                                onWorker.incrementAndGet();
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
        } finally {
            if (previousLog == null) System.clearProperty("derby.stream.error.file");
            else System.setProperty("derby.stream.error.file", previousLog);
        }
    }

    @Test
    void roundTripsWholeAggregateWithExactDecimalsAndBorrowedWorkers() {
        Draft draft = draft(" m98 ", List.of(n("20"), n("30"), n("30"), n("20")), ranges());
        Revision revision = service.create(draft, true).join().revision();
        assertEquals("M98", revision.data().code());
        assertEquals(State.APPROVATA, revision.state());
        assertNotNull(revision.approved());
        assertEquals(draft, service.load(revision.id()).join().revision().data());
        var page = service.page("m98", Filter.ALL, 0).join();
        assertEquals(1, page.total());
        assertEquals(4, page.rows().getFirst().layers());
        assertEquals(1, page.rows().getFirst().approvedVersion());
        assertEquals(opened.get(), onWorker.get());
        assertThrows(
                IllegalArgumentException.class,
                () -> draft("BAD", List.of(n("33.33333")), List.of()));
    }

    @Test
    void draftsCanBeIncompleteButApprovalRequiresAnExactHundred() {
        Revision revision =
                service.create(
                                draft(
                                        "M",
                                        List.of(n("33.3333"), n("33.3333"), n("33.3333")),
                                        List.of()),
                                false)
                        .join()
                        .revision();
        assertEquals(n("99.9999"), revision.data().total());
        assertNull(revision.approved());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.save(revision, revision.data(), true));
        assertThrows(
                IllegalArgumentException.class,
                () -> draft("M", List.of(n("100.0001")), List.of()));
        Draft complete = draft("M", List.of(n("33.3333"), n("33.3333"), n("33.3334")), List.of());
        Revision approved = service.save(revision, complete, true).join().revision();
        assertEquals(State.APPROVATA, approved.state());
        assertEquals(1, approved.number());
        assertTrue(approved.editVersion() > revision.editVersion());
    }

    @Test
    void rangesAreSortedAdjacentAndNeverOverlap() {
        var ranges = new ArrayList<>(ranges());
        Collections.reverse(ranges);
        Draft sorted = draft("M", List.of(n("100")), ranges);
        assertEquals(0, n("10").compareTo(sorted.ranges().getFirst().from()));
        assertThrows(
                IllegalArgumentException.class,
                () -> draft("M", List.of(n("100")), List.of(range("10", "18"), range("17", "20"))));
        assertThrows(IllegalArgumentException.class, () -> range("18", "18"));
        Revision revision = service.create(sorted, true).join().revision();
        assertEquals(sorted.ranges(), revision.data().ranges());
    }

    @Test
    void approvedAndRetiredContentsStayImmutableAndCopyDoesNotReassignUses() {
        Revision original =
                service.create(draft("M", List.of(n("100")), ranges()), true).join().revision();
        seedUsage(original.id());
        assertEquals(1, service.usages(original.id(), 0).join().total());
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(
                                        original,
                                        draft("M", List.of(n("50"), n("50")), ranges()),
                                        false)
                                .join());
        Revision copy = service.copy(original).join().revision();
        assertEquals(2, copy.number());
        assertEquals(original.data(), copy.data());
        assertEquals(0, service.usages(copy.id(), 0).join().total());
        service.save(copy, draft("M", List.of(n("50"), n("50")), List.of()), true).join();
        assertEquals(original.data(), service.load(original.id()).join().revision().data());
        assertEquals(1, service.usages(original.id(), 0).join().total());
        Revision retired = service.retire(original).join().revision();
        assertEquals(State.RITIRATA, retired.state());
        assertEquals(original.data(), retired.data());
        assertThrows(
                CompletionException.class,
                () -> service.save(retired, retired.data(), false).join());
        assertEquals(1, service.usages(retired.id(), 0).join().rows().getFirst().specification());
    }

    @Test
    void concurrentCopiesProduceExactlyOneDraftAndSequentialVersionNumbers() throws Exception {
        Revision original =
                service.create(draft("M", List.of(n("100")), List.of()), true).join().revision();
        var start = new CountDownLatch(1);
        var a =
                CompletableFuture.supplyAsync(
                        () -> {
                            await(start);
                            return service.copy(original).join();
                        });
        var b =
                CompletableFuture.supplyAsync(
                        () -> {
                            await(start);
                            return service.copy(original).join();
                        });
        start.countDown();
        CompletableFuture.allOf(a.handle((v, e) -> null), b.handle((v, e) -> null))
                .get(15, TimeUnit.SECONDS);
        assertNotEquals(a.isCompletedExceptionally(), b.isCompletedExceptionally());
        var versions = service.versions("M", 0).join();
        assertEquals(2, versions.total());
        assertEquals(2, versions.rows().getFirst().number());
        assertEquals(1, versions.rows().stream().filter(v -> v.state() == State.BOZZA).count());
    }

    @Test
    void staleEditsCannotReplaceChildrenOrEraseOtherOperatorsWork() {
        Revision original =
                service.create(draft("M", List.of(n("100")), List.of()), false).join().revision();
        Revision updated =
                service.save(original, draft("M", List.of(n("40"), n("60")), ranges()), false)
                        .join()
                        .revision();
        CompletionException stale =
                assertThrows(
                        CompletionException.class,
                        () ->
                                service.save(
                                                original,
                                                draft("M", List.of(n("20"), n("80")), List.of()),
                                                false)
                                        .join());
        assertInstanceOf(Conflict.class, stale.getCause());
        assertEquals(updated, service.load(updated.id()).join().revision());
        Revision noOp = service.save(updated, updated.data(), false).join().revision();
        assertEquals(updated.editVersion(), noOp.editVersion());
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(
                                        updated,
                                        draft("OTHER", updated.data().layers(), ranges()),
                                        false)
                                .join());
    }

    @Test
    void lateChildFailureRollsBackParametersAndAllPreviousChildren() {
        Revision original =
                service.create(draft("M", List.of(n("100")), ranges()), false).join().revision();
        execute(
                "ALTER TABLE TON.MODELLO_STRATI ADD CONSTRAINT test_layer_limit CHECK"
                        + " (numero_strato<2)");
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(
                                        original,
                                        draft("M", List.of(n("40"), n("60")), List.of()),
                                        false)
                                .join());
        assertEquals(original, service.load(original.id()).join().revision());
    }

    @Test
    void onlyAnUnusedDraftCanBeDeletedAndCodesAreNotDuplicated() {
        Revision draft =
                service.create(draft("M", List.of(n("100")), ranges()), false).join().revision();
        assertThrows(CompletionException.class, () -> service.create(draft.data(), false).join());
        assertTrue(service.load(draft.id()).join().deletable());
        service.delete(draft).join();
        assertEquals(0, service.page("", Filter.ALL, 0).join().total());
        Revision approved = service.create(draft.data(), true).join().revision();
        assertFalse(service.load(approved.id()).join().deletable());
        assertThrows(CompletionException.class, () -> service.delete(approved).join());
        Revision usedDraft = service.copy(approved).join().revision();
        seedUsage(usedDraft.id());
        assertFalse(service.load(usedDraft.id()).join().deletable());
        assertThrows(CompletionException.class, () -> service.delete(usedDraft).join());
        assertEquals(1, service.usages(usedDraft.id(), 0).join().total());
    }

    @Test
    void paginationFiltersAndSearchMetacharactersAreLiteral() {
        for (int i = 0; i < 52; i++)
            service.create(
                            draft(
                                    "MODEL" + String.format(Locale.ROOT, "%02d", i),
                                    List.of(n("100")),
                                    List.of()),
                            i % 2 == 0)
                    .join();
        service.create(draft("A_%\\", List.of(n("100")), List.of()), false).join();
        assertEquals(50, service.page("", Filter.ALL, 0).join().rows().size());
        assertEquals(3, service.page("", Filter.ALL, 50).join().rows().size());
        assertEquals(26, service.page("", Filter.APPROVED, 0).join().total());
        assertEquals(27, service.page("", Filter.DRAFT, 0).join().total());
        assertEquals(1, service.page("_%\\", Filter.ALL, 0).join().total());
    }

    @Test
    void concurrentReadersSeeOneCompleteDraftWhileAnotherOperatorSaves() throws Exception {
        Revision approved =
                service.create(draft("M", List.of(n("100")), List.of()), true).join().revision();
        Revision current = service.copy(approved).join().revision();
        for (int i = 0; i < 12; i++) {
            Revision before = current;
            Draft next =
                    draft(
                            "M",
                            i % 2 == 0 ? List.of(n("40"), n("60")) : List.of(n("100")),
                            i % 2 == 0 ? ranges() : List.of());
            var start = new CountDownLatch(1);
            var read =
                    CompletableFuture.supplyAsync(
                            () -> {
                                await(start);
                                return service.load(before.id()).join();
                            });
            var write =
                    CompletableFuture.supplyAsync(
                            () -> {
                                await(start);
                                return service.save(before, next, false).join();
                            });
            start.countDown();
            CompletableFuture.allOf(read, write).get(10, TimeUnit.SECONDS);
            Draft seen = read.join().revision().data();
            assertTrue(
                    seen.equals(before.data()) || seen.equals(next),
                    "A reader must never mix parameters and children from different edits");
            current = write.join().revision();
        }
        assertEquals(approved.data(), service.load(approved.id()).join().revision().data());
    }

    @Test
    void olderVersionsRemainAccessibleBeyondTheFirstPage() {
        Revision current =
                service.create(draft("M", List.of(n("100")), List.of()), true).join().revision();
        long first = current.id();
        for (int i = 2; i <= 52; i++) {
            Revision copy = service.copy(current).join().revision();
            current = service.save(copy, copy.data(), true).join().revision();
        }
        var firstPage = service.versions("M", 0).join();
        var older = service.versions("M", 50).join();
        assertEquals(52, firstPage.total());
        assertEquals(50, firstPage.rows().size());
        assertEquals(52, firstPage.rows().getFirst().number());
        assertEquals(2, older.rows().size());
        assertEquals(1, older.rows().getLast().number());
        assertEquals(first, older.rows().getLast().id());
        assertEquals(first, service.load(first).join().revision().id());
        assertEquals(1, service.page("", Filter.ALL, 0).join().total());
    }

    private Draft draft(String code, List<BigDecimal> layers, List<Range> ranges) {
        return new Draft(
                code,
                n("98"),
                n("2300.125"),
                n("20.12345"),
                n("1.2"),
                n("0.5"),
                "Nota tecnica",
                layers,
                ranges);
    }

    private List<Range> ranges() {
        return List.of(range("10", "18"), range("18", "26"));
    }

    private Range range(String from, String to) {
        return new Range(n(from), n(to), n("1.25"), n("0.5"));
    }

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
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

    private void seedUsage(long model) {
        execute(
                "INSERT INTO TON.LINEE(codice,nome) VALUES ('LINE','Linea')",
                "INSERT INTO TON.PRODOTTI(codice,descrizione,linea_id,colore_commerciale) VALUES"
                        + " ('P','Prodotto',1,'A0')",
                "INSERT INTO TON.ARTICOLI(codice,prodotto_id,diametro_mm,altezza_mm) VALUES"
                        + " ('ARTICLE',1,98,16)",
                "INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione) VALUES (1,1)",
                "INSERT INTO"
                    + " TON.CONFIGURAZIONI_ARTICOLO(articolo_id,prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo)"
                    + " VALUES (1,1,1,1,"
                        + model
                        + ",'test')");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

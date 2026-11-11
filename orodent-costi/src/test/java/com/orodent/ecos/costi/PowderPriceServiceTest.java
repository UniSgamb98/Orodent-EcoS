package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.costi.model.PowderPrices.*;
import com.orodent.ecos.costi.service.PowderPriceService;
import com.orodent.ecos.database.DatabaseSchema;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class PowderPriceServiceTest {
    private ConnectionProvider db;
    private PowderPriceService service;
    private ExecutorService workers;
    private String url, oldLog;
    private final AtomicInteger opened = new AtomicInteger(), background = new AtomicInteger();

    @BeforeEach
    void open() throws Exception {
        oldLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/costi-tests.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:costi_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        execute(
                "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES ('WHITE','Bianca','BIANCA')",
                "INSERT INTO TON.POLVERI(codice,nome,colore,attivo) VALUES"
                        + " ('PINK','Rosa','ROSA',FALSE)");
        workers = Executors.newFixedThreadPool(2, r -> new Thread(r, "costs-worker"));
        service =
                new PowderPriceService(
                        () -> {
                            opened.incrementAndGet();
                            if (Thread.currentThread().getName().equals("costs-worker"))
                                background.incrementAndGet();
                            return db.openConnection();
                        },
                        workers);
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
        if (oldLog == null) System.clearProperty("derby.stream.error.file");
        else System.setProperty("derby.stream.error.file", oldLog);
    }

    private static BigDecimal n(String text) {
        return new BigDecimal(text);
    }

    private static Draft simple(String price) {
        return new Draft(Mode.SEMPLICE, LocalDate.of(2026, 11, 9), n(price), null, List.of());
    }

    @Test
    void colorSelectionIsAppliedBeforePaginationAndRetainsUnspecifiedPowders() {
        for (int i = 0; i < 40; i++)
            execute(
                    "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES ('W"
                            + i
                            + "','Bianca','BIANCA')");
        execute("INSERT INTO TON.POLVERI(codice,nome) VALUES ('UNSPECIFIED','Da classificare')");
        assertEquals(42, service.page(java.util.Set.of("BIANCA"), false, 0).join().total());
        assertEquals(2, service.page(java.util.Set.of("BIANCA"), false, 40).join().rows().size());
        var pink = service.page(java.util.Set.of("ROSA"), true, 0).join();
        assertEquals(2, pink.total());
        assertEquals("PINK", pink.rows().getFirst().powder().code());
        assertNull(pink.rows().getLast().powder().color());
        assertEquals(1, service.page(java.util.Set.of("ROSA"), false, 0).join().total());
        assertEquals(0, service.page(java.util.Set.of(), true, 0).join().total());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.page(java.util.Set.of("BLACK"), true, 0));
    }

    @Test
    void usesOneTonIdentityRatherThanListingItsTechnicalRevisions() {
        execute(
                "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione) VALUES (1,1)",
                "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione) VALUES (1,2)");
        var page = service.page("", Filter.TUTTE, false, 0).join();
        assertEquals(1, page.total());
        assertEquals(1, page.rows().size());
        assertNull(page.rows().getFirst().mode());
        assertNull(page.rows().getFirst().unitPrice());
        assertNull(page.rows().getFirst().quantity());
        assertEquals(2, service.page("", Filter.TUTTE, true, 0).join().total());
        assertEquals(0, service.load(1).join().version());
        assertEquals(opened.get(), background.get());
    }

    @Test
    void retainsBothModesAndImmutableHistoryWithoutTouchingTon() {
        var lots =
                List.of(new Lot("L2026", n("100"), n("80")), new Lot("L2026", n("50"), n("100")));
        var first =
                service.save(
                                1,
                                0,
                                new Draft(
                                        Mode.AVANZATA,
                                        LocalDate.of(2026, 11, 9),
                                        n("92"),
                                        null,
                                        lots))
                        .join();
        assertEquals(1, first.version());
        assertEquals(lots, service.load(1).join().draft().lots());
        var second =
                service.save(
                                1,
                                1,
                                new Draft(
                                        Mode.SEMPLICE,
                                        first.draft().referenceDate(),
                                        first.draft().simplePrice(),
                                        null,
                                        lots))
                        .join();
        assertEquals(2, second.version());
        assertNull(second.draft().quantity());
        assertEquals(
                0,
                n("92").compareTo(
                                service.page("", Filter.SEMPLICE, false, 0)
                                        .join()
                                        .rows()
                                        .getFirst()
                                        .unitPrice()));
        assertEquals(1, service.page("", Filter.AVANZATA, false, 0).join().total());
        assertEquals(2, scalar("SELECT COUNT(*) FROM COSTI.POLVERI_PREZZI"));
        assertEquals(4, scalar("SELECT COUNT(*) FROM COSTI.POLVERI_LOTTI"));
        assertEquals(1, scalar("SELECT versione_modifica FROM TON.POLVERI WHERE polvere_id=1"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM TON.POLVERI_REVISIONI"));
        assertThrows(CompletionException.class, () -> service.save(1, 1, simple("50")).join());
        assertEquals(2, service.load(1).join().version());
    }

    @Test
    void concurrentFirstSavesCannotOverwriteEachOther() {
        var a = service.save(1, 0, simple("80"));
        var b = service.save(1, 0, simple("100"));
        int success = 0, conflict = 0;
        for (var save : List.of(a, b)) {
            try {
                save.join();
                success++;
            } catch (CompletionException failure) {
                assertInstanceOf(Conflict.class, failure.getCause());
                conflict++;
            }
        }
        assertEquals(1, success);
        assertEquals(1, conflict);
        assertEquals(1, scalar("SELECT COUNT(*) FROM COSTI.POLVERI_PREZZI"));
    }

    @Test
    void rollsBackSnapshotIfAnyLotFailsAndMissingPricesStayUnknown() {
        execute(
                "ALTER TABLE COSTI.POLVERI_LOTTI ADD CONSTRAINT ck_test_quantity CHECK"
                        + " (quantita_kg<100)");
        var invalidForDatabase =
                new Draft(
                        Mode.AVANZATA,
                        LocalDate.now(),
                        null,
                        null,
                        List.of(new Lot("A", n("10"), n("80")), new Lot("B", n("100"), n("90"))));
        assertThrows(
                CompletionException.class, () -> service.save(1, 0, invalidForDatabase).join());
        assertEquals(0, scalar("SELECT COUNT(*) FROM COSTI.POLVERI_PREZZI"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM COSTI.POLVERI_LOTTI"));
        service.save(
                        1,
                        0,
                        new Draft(
                                Mode.AVANZATA,
                                LocalDate.now(),
                                null,
                                null,
                                List.of(new Lot("Esaurito", BigDecimal.ZERO, n("80")))))
                .join();
        assertNull(service.load(1).join().draft().unitPrice());
        assertEquals(1, service.page("", Filter.MANCANTI, false, 0).join().total());
    }

    @Test
    void filtersLiterallyAndPaginatesWithoutLoadingTheWholeCatalogue() {
        execute("INSERT INTO TON.POLVERI(codice,nome) VALUES ('LITERAL_%','Da cercare')");
        db.withTransaction(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO TON.POLVERI(codice,nome) VALUES (?,?)")) {
                        for (int i = 0; i < 45; i++) {
                            q.setString(1, "TEST" + i);
                            q.setString(2, "Polvere " + i);
                            q.addBatch();
                        }
                        q.executeBatch();
                    }
                    return null;
                });
        assertEquals(1, service.page("_%", Filter.TUTTE, false, 0).join().total());
        var first = service.page("", Filter.TUTTE, false, 0).join();
        var second = service.page("", Filter.TUTTE, false, 40).join();
        assertEquals(47, first.total());
        assertEquals(40, first.rows().size());
        assertTrue(first.hasNext());
        assertEquals(7, second.rows().size());
        assertFalse(second.hasNext());
        assertEquals(
                47,
                java.util.stream.Stream.concat(first.rows().stream(), second.rows().stream())
                        .map(r -> r.powder().id())
                        .distinct()
                        .count());
    }

    private void execute(String... sql) {
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        for (String statement : sql) q.executeUpdate(statement);
                    }
                    return null;
                });
    }

    private int scalar(String sql) {
        return db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery(sql)) {
                        r.next();
                        return r.getInt(1);
                    }
                });
    }
}

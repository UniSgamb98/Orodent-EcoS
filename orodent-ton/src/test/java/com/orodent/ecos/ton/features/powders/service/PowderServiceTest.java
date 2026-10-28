package com.orodent.ecos.ton.features.powders.service;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.ton.features.powders.model.PowderModel.*;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class PowderServiceTest {
    private String url, previousLog;
    private ConnectionProvider database;
    private ExecutorService workers;
    private PowderService service;
    private final AtomicInteger opened = new AtomicInteger(), onWorker = new AtomicInteger();

    @BeforeEach
    void open() throws Exception {
        previousLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/ton-powders-derby.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:powders_" + UUID.randomUUID().toString().replace("-", "");
        database =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(database).initialize();
        workers = Executors.newFixedThreadPool(2, r -> new Thread(r, "powder-test-worker"));
        ConnectionProvider tracked =
                () -> {
                    opened.incrementAndGet();
                    if (Thread.currentThread().getName().equals("powder-test-worker"))
                        onWorker.incrementAndGet();
                    return database.openConnection();
                };
        service = new PowderService(tracked, workers);
    }

    @AfterEach
    void close() throws Exception {
        workers.shutdown();
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException expected) {
            assertEquals("08006", expected.getSQLState());
        }
        if (previousLog == null) System.clearProperty("derby.stream.error.file");
        else System.setProperty("derby.stream.error.file", previousLog);
    }

    @Test
    void savesExactWeightsAndIndependentSixYOnBorrowedWorkers() {
        var detail = service.create(identity(" p001 "), data("5.150", 6), false).join();
        assertEquals("P001", detail.powder().code());
        assertEquals(State.BOZZA, detail.latest().state());
        assertEquals(6, detail.latest().technical().yttriaClass());
        weight("5.150", oxide(detail.latest(), "Y2O3"));
        var loaded = service.load(detail.powder().id()).join();
        assertEquals(detail, loaded);
        assertEquals(opened.get(), onWorker.get());
        assertEquals(2, opened.get());
        assertEquals(1, service.findPage("polvere", false, 0).join().total());
    }

    @Test
    void validatesDecimalsDuplicatesAndYWithoutOpeningConnections() {
        assertThrows(
                IllegalArgumentException.class, () -> new Oxide("Y2O3", new BigDecimal("5.15001")));
        assertThrows(IllegalArgumentException.class, () -> new Oxide("Y2O3", BigDecimal.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TechnicalDraft(null, null, 0, "", List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TechnicalDraft(
                                null,
                                null,
                                6,
                                "",
                                List.of(
                                        new Oxide("Y₂O₃", BigDecimal.ONE),
                                        new Oxide("y2o3", BigDecimal.TEN))));
        assertEquals(12, new TechnicalDraft(null, null, 12, "", List.of()).yttriaClass());
        assertEquals(0, opened.get());
    }

    @Test
    void approvalFreezesTechnicalDataButAllowsIdentityCorrections() {
        var draft = service.create(identity("A"), data("5.150", 6), false).join();
        var approved =
                service.save(
                                draft,
                                draft.latest(),
                                draft.powder().identity(),
                                draft.latest().technical(),
                                true)
                        .join();
        assertEquals(State.APPROVATA, approved.latest().state());
        assertNotNull(approved.latest().approvedAt());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.save(
                                approved,
                                approved.latest(),
                                identity("A"),
                                data("9.350", 3),
                                false));
        var renamed =
                service.saveIdentity(approved, new IdentityDraft("A", "Nome corretto", 5, true))
                        .join();
        assertEquals(approved.latest(), renamed.latest());
        assertEquals("Nome corretto", renamed.powder().name());
        weight("5.150", oxide(service.load(renamed.powder().id()).join().latest(), "Y2O3"));
    }

    @Test
    void draftsCanBeIncompleteAndPartialAnalysesAreNeverNormalized() {
        var empty = new TechnicalDraft(null, null, null, "", List.of());
        var detail = service.create(identity("A"), empty, false).join();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.save(
                                detail, detail.latest(), detail.powder().identity(), empty, true));
        var tooMuch =
                new TechnicalDraft(
                        null,
                        null,
                        6,
                        "",
                        List.of(
                                new Oxide("ZrO2", new BigDecimal("99")),
                                new Oxide("Y2O3", new BigDecimal("5.150"))));
        var saved =
                service.save(detail, detail.latest(), detail.powder().identity(), tooMuch, false)
                        .join();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.save(
                                saved, saved.latest(), saved.powder().identity(), tooMuch, true));
        assertEquals(saved, service.load(saved.powder().id()).join());
        var partial =
                new TechnicalDraft(
                        null,
                        null,
                        6,
                        "Analisi parziale",
                        List.of(new Oxide("Y2O3", new BigDecimal("5.150"))));
        var approved =
                service.save(saved, saved.latest(), saved.powder().identity(), partial, true)
                        .join();
        weight("5.150", approved.latest().technical().total());
        assertEquals(1, approved.latest().technical().oxides().size());
    }

    @Test
    void creatingAVersionCopiesValuesAndLeavesApprovedVersionsUntouched() {
        var first = service.create(identity("A"), data("5.150", 3), true).join();
        var copied = service.newVersion(first, first.latest()).join();
        assertEquals(2, copied.latest().number());
        assertEquals(State.BOZZA, copied.latest().state());
        assertEquals(first.latest().technical(), copied.latest().technical());
        assertEquals(first.latest(), copied.revision(first.latest().id()));
        var changed =
                service.save(
                                copied,
                                copied.latest(),
                                copied.powder().identity(),
                                data("9.350", 6),
                                false)
                        .join();
        weight("5.150", oxide(changed.revision(first.latest().id()), "Y2O3"));
        weight("9.350", oxide(changed.latest(), "Y2O3"));
        CompletionException failure =
                assertThrows(
                        CompletionException.class,
                        () ->
                                service.newVersion(changed, changed.revision(first.latest().id()))
                                        .join());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals(2, service.load(changed.powder().id()).join().revisions().size());
    }

    @Test
    void concurrentVersionCreationHasExactlyOneWinner() {
        var initial = service.create(identity("A"), data("5.150", 3), true).join();
        var a = service.newVersion(initial, initial.latest());
        var b = service.newVersion(initial, initial.latest());
        var errors = Arrays.asList(a.handle((v, e) -> e).join(), b.handle((v, e) -> e).join());
        assertEquals(1, errors.stream().filter(Objects::nonNull).count());
        assertInstanceOf(
                Conflict.class,
                errors.stream().filter(Objects::nonNull).findFirst().orElseThrow().getCause());
        var saved = service.load(initial.powder().id()).join();
        assertEquals(2, saved.revisions().size());
        assertEquals(2, saved.latest().number());
    }

    @Test
    void concurrentDraftEditsNeverMixTheirOxidesOrOverwriteTheWinner() {
        var initial = service.create(identity("A"), data("5.150", 3), false).join();
        var a =
                service.save(
                        initial,
                        initial.latest(),
                        initial.powder().identity(),
                        data("8.900", 6),
                        false);
        var b =
                service.save(
                        initial,
                        initial.latest(),
                        initial.powder().identity(),
                        data("9.300", 5),
                        false);
        var errors = Arrays.asList(a.handle((v, e) -> e).join(), b.handle((v, e) -> e).join());
        assertEquals(1, errors.stream().filter(Objects::nonNull).count());
        assertInstanceOf(
                Conflict.class,
                errors.stream().filter(Objects::nonNull).findFirst().orElseThrow().getCause());
        var value = service.load(initial.powder().id()).join().latest().technical();
        assertTrue(value.equals(data("8.900", 6)) || value.equals(data("9.300", 5)));
    }

    @Test
    void duplicateCodesRollBackIdentityAndTechnicalChanges() {
        service.create(identity("A"), data("5.150", 3), false).join();
        var second = service.create(identity("B"), data("9.350", 6), false).join();
        var failure =
                assertThrows(
                        CompletionException.class,
                        () ->
                                service.save(
                                                second,
                                                second.latest(),
                                                identity("a"),
                                                data("8.900", 5),
                                                false)
                                        .join());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals(second, service.load(second.powder().id()).join());
        assertEquals(2, service.findPage("", true, 0).join().total());
    }

    @Test
    void aLateOxideFailureRollsBackBothTheParentAndRevisionUpdate() {
        var initial = service.create(identity("A"), data("5.150", 3), false).join();
        long id = initial.latest().id();
        database.withTransaction(
                c -> {
                    try (var s = c.createStatement()) {
                        s.executeUpdate(
                                "CREATE TABLE APP.KEEP_OXIDE(revision_id BIGINT,oxide"
                                    + " VARCHAR(30),FOREIGN KEY(revision_id,oxide) REFERENCES"
                                    + " TON.POLVERE_OSSIDI(polvere_revisione_id,ossido_codice))");
                        s.executeUpdate("INSERT INTO APP.KEEP_OXIDE VALUES (" + id + ",'Y2O3')");
                    }
                    return null;
                });
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(
                                        initial,
                                        initial.latest(),
                                        new IdentityDraft("A", "Da annullare", 0, true),
                                        data("9.350", 6),
                                        false)
                                .join());
        assertEquals(initial, service.load(initial.powder().id()).join());
    }

    @Test
    void retirementKeepsTheOxideAndExistingRecipeReference() {
        var initial = service.create(identity("A"), data("5.150", 3), true).join();
        database.withTransaction(
                c -> {
                    try (var s = c.createStatement()) {
                        s.executeUpdate("INSERT INTO TON.PRODOTTI(codice) VALUES ('ZR A2')");
                        s.executeUpdate(
                                "INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione) VALUES"
                                    + " (1,1)");
                        s.executeUpdate(
                                "INSERT INTO TON.COMPOSIZIONE_STRATI(composizione_id,numero_strato)"
                                    + " VALUES (1,1)");
                        s.executeUpdate(
                                "INSERT INTO TON.COMPOSIZIONE_INGREDIENTI VALUES (1,1,"
                                        + initial.latest().id()
                                        + ",100)");
                    }
                    return null;
                });
        var retired = service.retire(initial, initial.latest()).join();
        assertEquals(State.RITIRATA, retired.latest().state());
        assertEquals(initial.latest().technical(), retired.latest().technical());
        database.withConnection(
                c -> {
                    try (var s = c.createStatement();
                            var rows =
                                    s.executeQuery(
                                            "SELECT COUNT(*) FROM TON.COMPOSIZIONE_INGREDIENTI")) {
                        rows.next();
                        assertEquals(1, rows.getInt(1));
                    }
                    return null;
                });
        assertEquals(
                State.BOZZA, service.newVersion(retired, retired.latest()).join().latest().state());
    }

    @Test
    void archiveAndReactivateKeepIdentityAndHistoricalVersions() {
        var initial = service.create(identity("A"), data("5.150", 3), false).join();
        var archived =
                service.saveIdentity(initial, new IdentityDraft("A", "Polvere A", 0, false)).join();
        assertEquals(0, service.findPage("", false, 0).join().total());
        assertEquals(1, service.findPage("", true, 0).join().total());
        var active = service.saveIdentity(archived, identity("A")).join();
        assertEquals(initial.powder().id(), active.powder().id());
        assertEquals(initial.latest(), active.latest());
    }

    @Test
    void listPaginationAndLiteralSearchKeepTheApprovedAndDraftReferencesSeparate() {
        for (int i = 0; i < 52; i++)
            service.create(identity("P%03d".formatted(i)), data("5.150", 6), false).join();
        var first = service.findPage("", false, 0).join();
        var next = service.findPage("", false, 50).join();
        assertEquals(52, first.total());
        assertEquals(50, first.rows().size());
        assertTrue(first.hasNext());
        assertEquals(2, next.rows().size());
        var literal = service.create(identity("MIX%_\\A'"), data("5.150", 3), true).join();
        var draft = service.newVersion(literal, literal.latest()).join();
        for (String filter : List.of("%", "_", "\\", "'")) {
            var rows = service.findPage(filter, true, 0).join().rows();
            assertEquals(1, rows.size());
            assertEquals(1, rows.getFirst().approvedVersion());
            assertEquals(2, rows.getFirst().draftVersion());
            assertEquals(3, rows.getFirst().yttriaClass());
        }
        assertEquals(2, draft.latest().number());
    }

    private static IdentityDraft identity(String code) {
        return new IdentityDraft(code, "Polvere " + code.strip(), 0, true);
    }

    private static TechnicalDraft data(String yttria, int yClass) {
        BigDecimal y = new BigDecimal(yttria);
        return new TechnicalDraft(
                new BigDecimal("1200"),
                null,
                yClass,
                "",
                List.of(
                        new Oxide("ZrO2", new BigDecimal("100").subtract(y)),
                        new Oxide("Y₂O₃", y)));
    }

    private static BigDecimal oxide(Revision revision, String code) {
        return revision.technical().oxides().stream()
                .filter(o -> o.code().equals(code))
                .findFirst()
                .orElseThrow()
                .weightPercent();
    }

    private static void weight(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}

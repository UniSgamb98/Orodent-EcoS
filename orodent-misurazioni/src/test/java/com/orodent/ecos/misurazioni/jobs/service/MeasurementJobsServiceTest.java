package com.orodent.ecos.misurazioni.jobs.service;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.*;
import com.orodent.ecos.misurazioni.model.MeasurementBatch.Measurement;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

class MeasurementJobsServiceTest {
    ConnectionProvider db;
    String url;
    ExecutorService workers;
    MeasurementJobsService service;

    @BeforeEach
    void setup() {
        url = "jdbc:derby:memory:jobs_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        MeasurementJobsFixture.seed(db);
        workers = Executors.newFixedThreadPool(2);
        service = new MeasurementJobsService(db, workers, MeasurementJobsFixture.CLOCK);
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

    Detail first() {
        return service.load(service.list().join().getFirst().key()).join();
    }

    List<Measurement> changed(Detail d, int row, String micro, String scale) {
        var values = new ArrayList<>(d.job().readings().stream().map(Reading::value).toList());
        values.set(row, new Measurement(micro, scale));
        return values;
    }

    @Test
    void onlyFinishedLoadsAreProposedAndDifferentArticlesAndProductionsStayDistinct() {
        var jobs = service.list().join();
        assertEquals(3, jobs.size());
        assertEquals(2, jobs.stream().map(Job::product).distinct().count());
        assertEquals(7, jobs.stream().mapToLong(j -> j.readings().size()).sum());
        assertEquals("Da iniziare", jobs.getFirst().status());
        assertTrue(first().ruleLabel().contains("v1"));
        assertEquals(1450, first().job().finalTemperature().intValueExact());
        assertThrows(CompletionException.class, () -> service.load(new Key(2, 2, 1)).join());
        var nextDay =
                new MeasurementJobsService(
                        db,
                        workers,
                        java.time.Clock.offset(
                                MeasurementJobsFixture.CLOCK, java.time.Duration.ofDays(1)));
        assertEquals(
                1460,
                nextDay.load(new Key(2, 2, 1)).join().job().finalTemperature().intValueExact());
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "UPDATE TON.CICLI_COTTURA SET temperatura_finale_c=NULL WHERE"
                                        + " ciclo_id=1");
                    }
                    return null;
                });
        assertNull(first().job().finalTemperature());
    }

    @Test
    void partialMeasurementsSurviveReopeningAndCorrectionsKeepTheirPreviousRevisions() {
        var first = first();
        var operator = new Operator(UUID.randomUUID().toString(), "Operatore X");
        var saved = service.save(first, changed(first, 0, "98,100\t14,200", ""), operator).join();
        var reopened =
                new MeasurementJobsService(db, workers, MeasurementJobsFixture.CLOCK)
                        .load(first.job().key())
                        .join();
        assertEquals(saved, reopened);
        assertEquals("98,100\t14,200", reopened.job().readings().getFirst().value().micrometer());
        assertEquals("In corso", reopened.job().status());
        assertEquals(0, reopened.job().done());
        saved =
                service.save(reopened, changed(reopened, 0, "98,100\t14,200", "330,20"), operator)
                        .join();
        assertEquals(1, saved.job().done());
        var cleared = service.save(saved, changed(saved, 0, "98,100\t14,200", ""), operator).join();
        assertEquals(0, cleared.job().done());
        assertEquals(3, cleared.job().readings().getFirst().revision());
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT"
                                                + " numero_revisione,micrometro,bilancia,calcolo_versione_id,operatore_id"
                                                + " FROM TON.DISCO_MISURE_REVISIONI ORDER BY"
                                                + " numero_revisione")) {
                        assertTrue(r.next());
                        assertEquals(1, r.getInt(1));
                        assertEquals("", r.getString(3));
                        assertEquals(1, r.getLong(4));
                        assertEquals(operator.id(), r.getString(5));
                        assertTrue(r.next());
                        assertEquals("330,20", r.getString(3));
                        assertTrue(r.next());
                        assertEquals("", r.getString(3));
                        assertFalse(r.next());
                    }
                    return null;
                });
    }

    @Test
    void completionAndClearingOnlyCountTheCurrentReadingsOfEachPhysicalDisc() {
        var first = first();
        var values =
                first.job().readings().stream().map(r -> new Measurement("98\t14", "330")).toList();
        var done = service.save(first, values, null).join();
        assertTrue(done.job().complete());
        assertEquals("Completato", done.job().status());
        var resumed = service.save(done, changed(done, 1, "98\t14", ""), null).join();
        assertEquals(1, resumed.job().remaining());
        assertFalse(resumed.job().complete());
        var empty =
                service.save(
                                resumed,
                                resumed.job().readings().stream()
                                        .map(r -> new Measurement("", ""))
                                        .toList(),
                                null)
                        .join();
        assertEquals(3, empty.job().remaining());
        assertFalse(empty.job().touched());
        assertEquals(3, service.list().join().size());
    }

    @Test
    void simultaneousCorrectionsCannotSilentlyOverwriteTheSameDisc() {
        var baseline = first();
        var one = service.save(baseline, changed(baseline, 0, "98\t14", "331"), null);
        var two = service.save(baseline, changed(baseline, 0, "99\t15", "332"), null);
        var successes =
                List.of(one, two).stream().map(f -> f.handle((v, e) -> e == null).join()).toList();
        assertEquals(1, successes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, first().job().readings().getFirst().revision());
        var other = service.save(baseline, changed(baseline, 1, "97\t13", "333"), null).join();
        assertEquals(2, other.job().done());
    }

    @Test
    void aFailedBatchSaveRollsBackAllReadingsAndPreservesTheProductionTrace() {
        var first = first();
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "ALTER TABLE TON.DISCO_MISURE_REVISIONI ADD CONSTRAINT"
                                        + " reject_second_measure CHECK(disco_id<>2)");
                    }
                    return null;
                });
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(
                                        first,
                                        first.job().readings().stream()
                                                .map(r -> new Measurement("98\t14", "330"))
                                                .toList(),
                                        null)
                                .join());
        assertFalse(first().job().touched());
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "ALTER TABLE TON.DISCO_MISURE_REVISIONI DROP CONSTRAINT"
                                        + " reject_second_measure");
                    }
                    return null;
                });
        service.save(first, changed(first, 0, "98\t14", "330"), null).join();
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT"
                                                + " c.temperatura_finale_c,c.programma_id,p.modello_id,p.composizione_id,m.calcolo_versione_id"
                                                + " FROM TON.DISCO_MISURE_REVISIONI m JOIN"
                                                + " TON.DISCHI_PRODOTTI d ON d.disco_id=m.disco_id"
                                                + " JOIN TON.CONFIGURAZIONI_PRODOTTO p ON"
                                                + " p.configurazione_id=d.specifica_prodotto_id"
                                                + " JOIN TON.CARICO_DISCHI a ON"
                                                + " a.disco_id=d.disco_id JOIN TON.CICLI_COTTURA c"
                                                + " ON c.ciclo_id=a.ciclo_id")) {
                        assertTrue(r.next());
                        assertEquals(1450, r.getInt(1));
                        assertEquals(1, r.getLong(2));
                        assertEquals(1, r.getLong(3));
                        assertEquals(1, r.getLong(4));
                        assertEquals(1, r.getLong(5));
                    }
                    return null;
                });
    }

    @Test
    void upgradingLabelsKeepsPreviouslyMeasuredDiscsAndLeavesLegacySpecsUnassigned() {
        service.save(first(), changed(first(), 0, "98\t14", "330"), null).join();
        db.withTransaction(
                c -> {
                    MeasurementJobsFixture.beforeV34(c);
                    return null;
                });
        new DatabaseSchema(db).initialize();
        new DatabaseSchema(db).initialize();
        assertEquals(1, first().job().done());
        assertEquals("98\t14", first().job().readings().getFirst().value().micrometer());
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var rs =
                                    q.executeQuery(
                                            "SELECT etichetta_modello_id FROM"
                                                    + " TON.CONFIGURAZIONI_PRODOTTO WHERE"
                                                    + " configurazione_id=1")) {
                        rs.next();
                        assertNull(rs.getObject(1));
                    }
                    return null;
                });
    }

    @Test
    void upgradingFromThePreviousSchemaKeepsAllLoadAndDiscIdentities() {
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        MeasurementJobsFixture.beforeV34(c);
                        q.executeUpdate("DROP TABLE TON.DISCO_MISURE_REVISIONI");
                        q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=33");
                    }
                    return null;
                });
        new DatabaseSchema(db).initialize();
        new DatabaseSchema(db).initialize();
        assertEquals(3, service.list().join().size());
        assertEquals(1, first().job().readings().getFirst().discId());
        service.save(first(), changed(first(), 0, "98\t14", "330"), null).join();
        new DatabaseSchema(db).initialize();
        assertEquals(1, first().job().done());
        db.withConnection(
                c -> {
                    try (var q = c.createStatement()) {
                        assertThrows(
                                SQLException.class,
                                () ->
                                        q.executeUpdate(
                                                "INSERT INTO"
                                                    + " TON.DISCO_MISURE_REVISIONI(disco_id,numero_revisione,micrometro,bilancia)"
                                                    + " VALUES (999,1,'','')"));
                        assertThrows(
                                SQLException.class,
                                () ->
                                        q.executeUpdate(
                                                "INSERT INTO"
                                                    + " TON.DISCO_MISURE_REVISIONI(disco_id,numero_revisione,micrometro,bilancia)"
                                                    + " VALUES (1,0,'','')"));
                    }
                    return null;
                });
    }
}

package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.scenarios.*;
import com.orodent.ecos.costi.scenarios.Scenarios.*;
import com.orodent.ecos.database.DatabaseSchema;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletionException;

class ScenarioTest {
    ConnectionProvider db;
    ScenarioService service;
    String url, oldLog;
    final ScenarioEngine engine = new ScenarioEngine();

    BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    void same(String value, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, n(value).compareTo(actual));
    }

    @BeforeEach
    void open() throws Exception {
        oldLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/scenario-tests.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:scenario_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        ProductCostFixture.seed(db);
        ConsumptionTest.seed(db);
        ProductCostFixture.execute(
                db,
                "INSERT INTO"
                    + " COSTI.PRODOTTI_PARAMETRI(prodotto_id,numero_versione,trasformazione_eur)"
                    + " VALUES (1,1,12)");
        service = new ScenarioService(db, Runnable::run);
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException ignored) {
        }
        if (oldLog == null) System.clearProperty("derby.stream.error.file");
        else System.setProperty("derby.stream.error.file", oldLog);
    }

    Draft fresh() {
        return Draft.create(
                service.capture(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8)).join(),
                "Scenario prova");
    }

    Draft price(Draft d, PriceMode mode, String value) {
        return d.state(
                d.name(),
                d.notes(),
                d.archived(),
                d.selection(),
                d.products(),
                Map.of(1L, new PriceChange(mode, n(value))),
                d.recipes(),
                d.models(),
                d.transforms());
    }

    @Test
    void equalAndVolumeWeightedMeansDifferAcrossFormats() {
        ProductCostFixture.execute(
                db,
                "INSERT INTO"
                    + " TON.CONFIGURAZIONI_ARTICOLO(articolo_id,prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,stato,approvato_il)"
                    + " VALUES (2,1,1,1,1,'TON-ORIGINALE','APPROVATA',CURRENT_TIMESTAMP)",
                "UPDATE TON.ARTICOLI SET configurazione_predefinita_id=3 WHERE articolo_id=2",
                "INSERT INTO"
                    + " VENDITE.VENDITE_DETTAGLIO(sorgente,documento_id,numero_documento,numero_riga,data_vendita,codice_cliente,codice_prodotto,quantita,tipo_operazione)"
                    + " VALUES"
                    + " ('TEST','S2','S2',1,DATE('2026-11-04'),'C1','MSTA2-98-14',60,'VENDITA')");
        var analysis = engine.analyse(price(fresh(), PriceMode.PERCENT, "10"));
        Group equal = engine.groups(analysis, Weight.ARTICLES, true, null).getFirst();
        same("54.3105", equal.before());
        same("57.61695", equal.after());
        assertEquals(2, equal.comparable());
        Group sales = engine.groups(analysis, Weight.SALES, true, null).getFirst();
        same("57.1245", sales.before());
        same("60.65205", sales.after());
        same("80", sales.weight());
        assertEquals(2, sales.weighted());
        Group production = engine.groups(analysis, Weight.PRODUCTION, true, null).getFirst();
        same("48.6825", production.before());
        assertEquals(1, production.weighted());
    }

    @Test
    void concurrentSavesCannotOverwriteAndRefreshConflictsRequireResolution() throws Exception {
        Draft saved = service.save(fresh()).join();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var concurrent = new ScenarioService(db, pool);
            var a = concurrent.save(price(saved, PriceMode.PERCENT, "1"));
            var b = concurrent.save(price(saved, PriceMode.PERCENT, "2"));
            int ok = 0, conflicts = 0;
            for (var save : List.of(a, b)) {
                try {
                    save.join();
                    ok++;
                } catch (CompletionException e) {
                    assertInstanceOf(Scenarios.Conflict.class, e.getCause());
                    conflicts++;
                }
            }
            assertEquals(1, ok);
            assertEquals(1, conflicts);
            assertEquals(2, service.history(saved.id()).join().size());
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS));
        }
        Model model = saved.base().catalogue().models().get(1L);
        Draft modified =
                saved.state(
                        saved.name(),
                        saved.notes(),
                        false,
                        saved.selection(),
                        saved.products(),
                        Map.of(),
                        Map.of(),
                        Map.of(1L, model),
                        Map.of());
        ProductCostFixture.execute(
                db,
                "UPDATE TON.MODELLI_DISCO SET sovramateriale_superiore_mm=2 WHERE modello_id=1");
        var changed =
                engine.refresh(
                        modified,
                        service.capture(saved.base().from(), saved.base().through()).join());
        assertFalse(changed.conflicts().isEmpty());
        assertThrows(IllegalStateException.class, changed::accepted);
        same("1", modified.base().catalogue().models().get(1L).top());
    }

    @Test
    void persistedSnapshotsAndRevisionsAreImmutableAndConflictProtected() {
        Draft draft = price(fresh(), PriceMode.PERCENT, "10");
        var analysis = engine.analyse(draft);
        same("120.8", analysis.rows().getFirst().after().nominalMixPrice());
        Draft first = service.save(draft).join();
        assertEquals(1, first.version());
        Draft loaded = service.load(service.list(false).join().getFirst()).join();
        assertEquals(first, loaded);
        ProductCostFixture.execute(
                db, "UPDATE COSTI.POLVERI_PREZZI SET prezzo_semplice=500 WHERE prezzo_id=2");
        same("120.8", engine.analyse(loaded).rows().getFirst().after().nominalMixPrice());
        var second = service.save(price(loaded, PriceMode.PRICE, "110")).join();
        assertEquals(2, second.version());
        assertEquals(2, service.history(first.id()).join().size());
        assertInstanceOf(
                Scenarios.Conflict.class,
                assertThrows(CompletionException.class, () -> service.save(loaded).join())
                        .getCause());
        var old = service.load(service.history(first.id()).join().getLast()).join();
        assertEquals(first, old);
        int bases =
                db.withConnection(
                        c -> {
                            var r =
                                    c.createStatement()
                                            .executeQuery(
                                                    "SELECT COUNT(*) FROM COSTI.SCENARI_BASI");
                            r.next();
                            return r.getInt(1);
                        });
        assertEquals(1, bases);
        var archived =
                service.save(
                                second.state(
                                        second.name(),
                                        second.notes(),
                                        true,
                                        second.selection(),
                                        second.products(),
                                        second.prices(),
                                        second.recipes(),
                                        second.models(),
                                        second.transforms()))
                        .join();
        assertTrue(service.list(false).join().isEmpty());
        assertEquals(1, service.list(true).join().size());
        var copy = service.save(archived.duplicate()).join();
        assertNotEquals(first.id(), copy.id());
        assertFalse(copy.archived());
    }

    @Test
    void modelAndRecipeChangesReuseRealMassRulesAndNeverModifyTon() {
        Draft d = fresh();
        Model base = d.base().catalogue().models().get(1L);
        Model less =
                new Model(
                        base.id(),
                        base.code(),
                        base.version(),
                        "SCENARIO",
                        base.gramsPerMm(),
                        n("0"),
                        n("0"),
                        base.layers(),
                        List.of());
        Draft changed =
                d.state(
                        d.name(),
                        d.notes(),
                        false,
                        d.selection(),
                        d.products(),
                        Map.of(),
                        Map.of(),
                        Map.of(1L, less),
                        Map.of());
        var row = engine.analyse(changed).rows().getFirst();
        same("326.625", row.before().grams());
        same("251.25", row.after().grams());
        same("112", row.after().nominalMixPrice());
        same("40.14", row.after().total());
        var recipe =
                new Recipe(
                        1,
                        1,
                        "SCENARIO",
                        Map.of(
                                1,
                                List.of(new Ingredient(1, "WHITE", 1, n("100"))),
                                2,
                                List.of(new Ingredient(1, "WHITE", 1, n("100")))));
        ScenarioEngine.validateRecipe(recipe);
        changed =
                changed.state(
                        changed.name(),
                        changed.notes(),
                        false,
                        changed.selection(),
                        changed.products(),
                        Map.of(),
                        Map.of(1L, recipe),
                        Map.of(1L, less),
                        Map.of());
        same("100", engine.analyse(changed).rows().getFirst().after().nominalMixPrice());
        same(
                "1",
                db.withConnection(
                        c -> {
                            var r =
                                    c.createStatement()
                                            .executeQuery(
                                                    "SELECT sovramateriale_superiore_mm FROM"
                                                        + " TON.MODELLI_DISCO WHERE modello_id=1");
                            r.next();
                            return r.getBigDecimal(1);
                        }));
        Model incompatible =
                new Model(
                        1,
                        "FOUR",
                        1,
                        "SCENARIO",
                        n("25"),
                        n("1"),
                        n("2"),
                        Map.of(1, n("25"), 2, n("25"), 3, n("25"), 4, n("25")),
                        List.of());
        ScenarioEngine.validateModel(incompatible);
        Draft invalid =
                d.state(
                        d.name(),
                        d.notes(),
                        false,
                        d.selection(),
                        d.products(),
                        Map.of(),
                        Map.of(),
                        Map.of(1L, incompatible),
                        Map.of());
        assertNull(engine.analyse(invalid).rows().getFirst().after().total());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScenarioEngine.validateModel(
                                new Model(
                                        1,
                                        "BAD",
                                        1,
                                        "SCENARIO",
                                        n("25"),
                                        n("1"),
                                        n("1"),
                                        base.layers(),
                                        List.of(
                                                new Range(n("10"), n("20"), n("1"), n("1")),
                                                new Range(n("15"), n("25"), n("1"), n("1"))))));
    }

    @Test
    void weightedGroupsOnlyUsePairsAndExplicitCapturedVolumes() {
        Draft d = price(fresh(), PriceMode.PERCENT, "10");
        var analysis = engine.analyse(d);
        Group group = engine.groups(analysis, Weight.ARTICLES, true, null).getFirst();
        assertEquals(2, group.articles());
        assertEquals(1, group.comparable());
        assertEquals(1, group.weighted());
        same("1", group.weight());
        same("20", engine.groups(analysis, Weight.SALES, true, null).getFirst().weight());
        same("10", engine.groups(analysis, Weight.PRODUCTION, true, null).getFirst().weight());
        var next = service.capture(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).join();
        Draft empty =
                new Draft(
                        d.id(),
                        d.version(),
                        d.name(),
                        d.notes(),
                        false,
                        next,
                        d.selection(),
                        d.products(),
                        d.prices(),
                        d.recipes(),
                        d.models(),
                        d.transforms());
        assertNull(
                engine.groups(engine.analyse(empty), Weight.SALES, true, null).getFirst().before());
    }

    @Test
    void refreshIsExplicitAndRetainsRelativeVersusAbsoluteOverrides() {
        Draft relative = price(fresh(), PriceMode.PERCENT, "10");
        ProductCostFixture.execute(
                db, "UPDATE COSTI.POLVERI_PREZZI SET prezzo_semplice=200 WHERE prezzo_id=2");
        Base next = service.capture(relative.base().from(), relative.base().through()).join();
        var refresh = engine.refresh(relative, next);
        assertFalse(refresh.differences().isEmpty());
        assertTrue(refresh.conflicts().isEmpty());
        same("220", engine.analyse(refresh.draft()).after().prices().get(1L).price());
        same("110", engine.analyse(relative).after().prices().get(1L).price());
        Draft absolute = price(relative, PriceMode.PRICE, "150");
        same(
                "150",
                engine.analyse(engine.refresh(absolute, next).draft())
                        .after()
                        .prices()
                        .get(1L)
                        .price());
    }

    @Test
    void incompatibleCodecFormatIsRejected() {
        ScenarioCodec codec = new ScenarioCodec();
        var d = fresh();
        assertEquals(d.base(), codec.base(codec.base(d.base())));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.base(codec.base(d.base()).replace("\"format\":1", "\"format\":999")));
    }
}

package com.orodent.ecos.ton.features.compositions.service;

import static com.orodent.ecos.ton.features.compositions.model.Compositions.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class CompositionServiceTest {
    private String url, previousLog;
    private ConnectionProvider db;
    private ExecutorService workers;
    private CompositionService service;
    private final AtomicInteger opened = new AtomicInteger(), onWorker = new AtomicInteger();

    @BeforeEach
    void open() throws Exception {
        previousLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/ton-compositions.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:recipes_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException failure) {
                        throw new IllegalStateException(failure);
                    }
                };
        new DatabaseSchema(db).initialize();
        workers = Executors.newFixedThreadPool(2, r -> new Thread(r, "recipe-worker"));
        service =
                new CompositionService(
                        () -> {
                            opened.incrementAndGet();
                            if (Thread.currentThread().getName().equals("recipe-worker"))
                                onWorker.incrementAndGet();
                            return db.openConnection();
                        },
                        workers);
        execute(
                "INSERT INTO TON.LINEE(codice,nome) VALUES ('MST','Thor')",
                "INSERT INTO TON.PRODOTTI(codice,descrizione,linea_id,colore_commerciale) VALUES"
                        + " ('MSTA2','Thor A2',1,'A2'),('MSTA3','Thor A3',1,'A3')",
                "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES"
                    + " ('BI-3','Bianca','BIANCA'),('GI-5','Gialla','GIALLA'),('RO-5','Rosa','ROSA')",
                "INSERT INTO"
                    + " TON.POLVERI_REVISIONI(polvere_id,numero_versione,classe_ittria_y,stato,approvato_il)"
                    + " VALUES"
                    + " (1,1,3,'APPROVATA',CURRENT_TIMESTAMP),(1,2,3,'APPROVATA',CURRENT_TIMESTAMP),(2,1,5,'APPROVATA',CURRENT_TIMESTAMP),(3,1,5,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione) VALUES (1,3)",
                "INSERT INTO TON.POLVERE_OSSIDI VALUES"
                    + " (1,'Y2O3',5.150),(1,'ZRO2',93.850),(1,'HFO2',1),(2,'Y2O3',5.200),(3,'Y2O3',9.350),(3,'ZRO2',90.650)");
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
    void storesVersionedRecipeAndComputesNominalChemistryWithoutInventingMissingData() {
        Detail detail = service.create(recipe(1), true).join();
        assertEquals(State.APPROVATA, detail.revision().state());
        assertEquals("MSTA2", detail.product().code());
        assertEquals("MST", detail.product().lineCode());
        assertEquals(recipe(1), service.load(detail.revision().id()).join().revision().data());
        assertEquals(Set.of(1L, 3L), detail.powders().keySet());
        var y =
                CompositionChemistry.balance(
                        detail.revision().data().layers().getFirst(), "Y2O3", detail.powders());
        assertEquals(0, n("6.200").compareTo(y.weightPercent()));
        assertEquals(0, n("100").compareTo(y.coverage()));
        var missing =
                CompositionChemistry.balance(
                        detail.revision().data().layers().getFirst(), "HFO2", detail.powders());
        assertNull(missing.weightPercent());
        assertEquals(0, n("75").compareTo(missing.coverage()));
        assertEquals(opened.get(), onWorker.get());
    }

    @Test
    void draftCanBeIncompleteButEveryApprovedLayerNeedsExactTotalAndConsecutiveNumbers() {
        Draft partial =
                new Draft(
                        1, "", List.of(new Layer(1, "", List.of(new Ingredient(1, n("99.9999"))))));
        Revision draft = service.create(partial, false).join().revision();
        assertThrows(IllegalArgumentException.class, () -> service.save(draft, partial, true));
        assertThrows(IllegalArgumentException.class, () -> new Ingredient(1, n("0.00001")));
        assertThrows(IllegalArgumentException.class, () -> new Ingredient(1, n("100.0001")));
        Draft gap =
                new Draft(1, "", List.of(new Layer(2, "", List.of(new Ingredient(1, n("100"))))));
        assertThrows(IllegalArgumentException.class, () -> service.save(draft, gap, true));
        var empty =
                service.save(
                                draft,
                                new Draft(1, "", List.of(new Layer(1, "Strato vuoto", List.of()))),
                                false)
                        .join();
        assertEquals(0, empty.revision().data().layers().getFirst().ingredients().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.save(empty.revision(), empty.revision().data(), true));
    }

    @Test
    void rejectsTwoRevisionsOfOnePowderAndApprovesOnlyCurrentActiveApprovedDependencies() {
        Draft duplicate =
                new Draft(
                        1,
                        "",
                        List.of(
                                new Layer(
                                        1,
                                        "",
                                        List.of(
                                                new Ingredient(1, n("50")),
                                                new Ingredient(2, n("50"))))));
        assertThrows(CompletionException.class, () -> service.create(duplicate, false).join());
        assertEquals(0, service.page("", Filter.ALL, true, 0).join().total());
        Draft unapproved =
                new Draft(1, "", List.of(new Layer(1, "", List.of(new Ingredient(5, n("100"))))));
        assertThrows(CompletionException.class, () -> service.create(unapproved, true).join());
        Revision draft = service.create(recipe(1), false).join().revision();
        execute("UPDATE TON.POLVERI_REVISIONI SET stato='RITIRATA' WHERE polvere_revisione_id=1");
        assertThrows(
                CompletionException.class, () -> service.save(draft, draft.data(), true).join());
        assertEquals(State.BOZZA, service.load(draft.id()).join().revision().state());
        execute(
                "UPDATE TON.POLVERI_REVISIONI SET stato='APPROVATA' WHERE polvere_revisione_id=1",
                "UPDATE TON.POLVERI SET attivo=FALSE WHERE polvere_id=2");
        assertThrows(
                CompletionException.class, () -> service.save(draft, draft.data(), true).join());
        assertTrue(service.powders("").join().stream().noneMatch(p -> p.id() == 3 || p.id() == 5));
    }

    @Test
    void copyingKeepsExactPowderVersionsAndDoesNotRewriteExistingArticleReferences() {
        Detail approved = service.create(recipe(1), true).join();
        seedUsage(approved.revision().id());
        Revision copy = service.copy(approved.revision()).join().revision();
        assertEquals(2, copy.number());
        assertEquals(approved.revision().data(), copy.data());
        assertThrows(
                CompletionException.class,
                () -> service.save(approved.revision(), approved.revision().data(), false).join());
        Detail updated =
                service.save(
                                copy,
                                new Draft(
                                        1,
                                        "Nuova polvere",
                                        List.of(
                                                new Layer(
                                                        1,
                                                        "",
                                                        List.of(
                                                                new Ingredient(2, n("75")),
                                                                new Ingredient(3, n("25")))))),
                                true)
                        .join();
        assertEquals(
                0,
                n("6.2375")
                        .compareTo(
                                CompositionChemistry.balance(
                                                updated.revision().data().layers().getFirst(),
                                                "Y2O3",
                                                updated.powders())
                                        .weightPercent()));
        assertEquals(1, service.usages(approved.revision().id(), 0).join().total());
        assertEquals(0, service.usages(updated.revision().id(), 0).join().total());
        assertEquals(approved.revision(), service.load(approved.revision().id()).join().revision());
        Revision retired = service.retire(approved.revision()).join().revision();
        assertEquals(State.RITIRATA, retired.state());
        assertEquals(approved.revision().data(), retired.data());
        assertEquals(1, service.usages(retired.id(), 0).join().total());
    }

    @Test
    void concurrentCopiesCreateOnlyOneDraft() throws Exception {
        Revision approved = service.create(recipe(1), true).join().revision();
        var latch = new CountDownLatch(1);
        var one =
                CompletableFuture.supplyAsync(
                        () -> {
                            await(latch);
                            return service.copy(approved).join();
                        });
        var two =
                CompletableFuture.supplyAsync(
                        () -> {
                            await(latch);
                            return service.copy(approved).join();
                        });
        latch.countDown();
        CompletableFuture.allOf(one.handle((v, e) -> null), two.handle((v, e) -> null))
                .get(15, TimeUnit.SECONDS);
        assertNotEquals(one.isCompletedExceptionally(), two.isCompletedExceptionally());
        var versions = service.versions(1, 0).join();
        assertEquals(2, versions.total());
        assertEquals(1, versions.rows().stream().filter(v -> v.state() == State.BOZZA).count());
    }

    @Test
    void staleOrForgedClientSnapshotsCannotReplaceOtherRecipes() {
        Revision original = service.create(recipe(1), false).join().revision();
        Revision updated =
                service.save(original, new Draft(1, "Corretta", original.data().layers()), false)
                        .join()
                        .revision();
        assertThrows(
                CompletionException.class,
                () -> service.save(original, original.data(), false).join());
        assertEquals(updated, service.load(updated.id()).join().revision());
        Revision forged =
                new Revision(
                        updated.id(),
                        updated.number(),
                        updated.state(),
                        updated.editVersion(),
                        updated.updated(),
                        recipe(2));
        assertThrows(
                CompletionException.class, () -> service.save(forged, recipe(2), false).join());
        Revision same = service.save(updated, updated.data(), false).join().revision();
        assertEquals(updated.editVersion(), same.editVersion());
    }

    @Test
    void aLateIngredientFailureRollsBackNotesAndAllLayers() {
        Revision original = service.create(recipe(1), false).join().revision();
        execute(
                "ALTER TABLE TON.COMPOSIZIONE_INGREDIENTI ADD CONSTRAINT test_max_mass CHECK"
                        + " (percentuale_massa<90)");
        Draft invalid =
                new Draft(
                        1,
                        "Non salvata",
                        List.of(new Layer(1, "Diverso", List.of(new Ingredient(1, n("100"))))));
        assertThrows(
                CompletionException.class, () -> service.save(original, invalid, false).join());
        assertEquals(original, service.load(original.id()).join().revision());
    }

    @Test
    void deletionsArchivesSearchAndModelStructureAreCheckedAgainstRealRows() {
        Revision draft = service.create(recipe(1), false).join().revision();
        assertTrue(service.load(draft.id()).join().deletable());
        service.delete(draft).join();
        assertEquals(0, service.page("", Filter.ALL, false, 0).join().total());
        Detail approved = service.create(recipe(1), true).join();
        assertThrows(CompletionException.class, () -> service.delete(approved.revision()).join());
        assertEquals(1, service.page("thor a2", Filter.APPROVED, false, 0).join().total());
        assertEquals(0, service.page("%", Filter.ALL, false, 0).join().total());
        execute("UPDATE TON.PRODOTTI SET attivo=FALSE WHERE prodotto_id=1");
        assertEquals(0, service.page("", Filter.ALL, false, 0).join().total());
        assertEquals(1, service.page("", Filter.ALL, true, 0).join().total());
        assertThrows(CompletionException.class, () -> service.copy(approved.revision()).join());
        seedModel("ONE", 1);
        seedModel("TWO", 2);
        assertEquals(2, service.models(List.of(1), "", false, 0).join().total());
        assertEquals(1, service.models(List.of(1), "", true, 0).join().total());
        assertEquals(0, service.models(List.of(2), "", true, 0).join().total());
    }

    @Test
    void concurrentReadsNeverMixNotesAndIngredientsFromDifferentEdits() throws Exception {
        Revision current = service.create(recipe(1), false).join().revision();
        for (int i = 0; i < 6; i++) {
            Revision before = current;
            Draft next =
                    new Draft(
                            1,
                            "Revisione " + i,
                            List.of(
                                    new Layer(
                                            1,
                                            "Solo bianca",
                                            List.of(
                                                    new Ingredient(
                                                            i % 2 == 0 ? 2 : 1, n("100"))))));
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
            assertTrue(
                    read.join().revision().data().equals(before.data())
                            || read.join().revision().data().equals(next));
            current = write.join().revision();
        }
    }

    @Test
    void versionsArePagedPerProductWithoutHidingPinnedOldPowderReferences() {
        Revision first = service.create(recipe(1), true).join().revision();
        service.create(recipe(2), false).join();
        Revision current = first;
        for (int i = 2; i <= 52; i++) {
            Revision copy = service.copy(current).join().revision();
            current = service.save(copy, copy.data(), true).join().revision();
        }
        var recent = service.versions(1, 0).join();
        var older = service.versions(1, 50).join();
        assertEquals(52, recent.total());
        assertEquals(50, recent.rows().size());
        assertEquals(52, recent.rows().getFirst().number());
        assertEquals(2, older.rows().size());
        assertEquals(first.id(), older.rows().getLast().id());
        assertEquals(2, service.page("", Filter.ALL, false, 0).join().total());
        assertEquals(1, service.page("", Filter.DRAFT, false, 0).join().total());
        assertEquals(1, service.page("", Filter.APPROVED, false, 0).join().total());
        assertEquals(Set.of(1L, 3L), service.load(first.id()).join().powders().keySet());
    }

    @Test
    void powderChoicesShowOnlyLatestApprovedRevisionOfEachActivePowder() {
        var choices = service.powderPage("", 0).join();
        assertEquals(3, choices.total());
        assertEquals(List.of(2L, 3L, 4L), choices.rows().stream().map(PowderRef::id).toList());
        assertEquals(2, service.powders("BI-3").join().getFirst().number());
        execute(
                "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione,stato,approvato_il)"
                        + " VALUES (1,4,'RITIRATA',CURRENT_TIMESTAMP)",
                "UPDATE TON.POLVERI SET attivo=FALSE WHERE polvere_id=2",
                "UPDATE TON.POLVERI_REVISIONI SET stato='BOZZA',approvato_il=NULL WHERE"
                    + " polvere_id=3");
        var available = service.powderPage("", 0).join();
        assertEquals(1, available.total());
        assertEquals(2, available.rows().getFirst().number());
    }

    @Test
    void powderLookupsAreBoundedWhileSelectedHistoricalRevisionsRemainReadable() {
        Revision first = service.create(recipe(1), true).join().revision();
        db.withTransaction(
                c -> {
                    for (int i = 4; i <= 58; i++) {
                        long id;
                        try (var q =
                                c.prepareStatement(
                                        "INSERT INTO"
                                            + " TON.POLVERI_REVISIONI(polvere_id,numero_versione,stato,approvato_il)"
                                            + " VALUES (1,?,'APPROVATA',CURRENT_TIMESTAMP)",
                                        Statement.RETURN_GENERATED_KEYS)) {
                            q.setInt(1, i);
                            q.executeUpdate();
                            try (var r = q.getGeneratedKeys()) {
                                r.next();
                                id = r.getLong(1);
                            }
                        }
                        try (var q =
                                c.prepareStatement(
                                        "INSERT INTO TON.POLVERE_OSSIDI VALUES (?,'Y2O3',5.2)")) {
                            q.setLong(1, id);
                            q.executeUpdate();
                        }
                    }
                    return null;
                });
        var choices = service.powders("BI-3").join();
        assertEquals(1, choices.size());
        assertEquals(58, choices.getFirst().number());
        db.withTransaction(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES (?,"
                                            + " 'Bianca', 'BIANCA')")) {
                        for (int i = 1; i <= 55; i++) {
                            q.setString(1, "PAGE-" + i);
                            q.addBatch();
                        }
                        q.executeBatch();
                    }
                    try (var q = c.createStatement()) {
                        for (int version = 1; version <= 2; version++)
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.POLVERI_REVISIONI(polvere_id,numero_versione,stato,approvato_il)"
                                        + " SELECT polvere_id,"
                                            + version
                                            + ",'APPROVATA',CURRENT_TIMESTAMP FROM TON.POLVERI"
                                            + " WHERE codice LIKE 'PAGE-%'");
                    }
                    return null;
                });
        var recent = service.powderPage("", 0).join();
        var next = service.powderPage("", 50).join();
        assertEquals(58, recent.total());
        assertEquals(50, recent.rows().size());
        assertEquals(8, next.rows().size());
        var all = new HashSet<Long>();
        for (var page : List.of(recent, next))
            for (var powder : page.rows()) assertTrue(all.add(powder.powderId()));
        Detail historical = service.load(first.id()).join();
        assertTrue(historical.powders().containsKey(1L));
        assertEquals(first.data(), historical.revision().data());
    }

    private Draft recipe(long product) {
        return new Draft(
                product,
                "Nota",
                List.of(
                        new Layer(
                                1,
                                "Superiore",
                                List.of(new Ingredient(1, n("75")), new Ingredient(3, n("25"))))));
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

    private void seedModel(String code, int count) {
        execute(
                "INSERT INTO"
                    + " TON.MODELLI_DISCO(codice,numero_versione,diametro_pressatura_mm,pressione_kg_cm2,grammi_per_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm,stato,approvato_il)"
                    + " VALUES ('"
                        + code
                        + "',1,98,1000,20,1,1,'APPROVATA',CURRENT_TIMESTAMP)");
        db.withTransaction(
                c -> {
                    long id;
                    try (var q =
                            c.prepareStatement(
                                    "SELECT modello_id FROM TON.MODELLI_DISCO WHERE codice=?")) {
                        q.setString(1, code);
                        try (var r = q.executeQuery()) {
                            r.next();
                            id = r.getLong(1);
                        }
                    }
                    try (var q =
                            c.prepareStatement("INSERT INTO TON.MODELLO_STRATI VALUES (?,?,?)")) {
                        for (int i = 1; i <= count; i++) {
                            q.setLong(1, id);
                            q.setInt(2, i);
                            q.setBigDecimal(3, n(count == 1 ? "100" : "50"));
                            q.executeUpdate();
                        }
                    }
                    return null;
                });
    }

    private void seedUsage(long comp) {
        seedModel("MODEL", 1);
        execute(
                "INSERT INTO TON.ARTICOLI(codice,prodotto_id,diametro_mm,altezza_mm) VALUES"
                        + " ('ARTICLE',1,98,16)",
                "INSERT INTO"
                    + " TON.CONFIGURAZIONI_ARTICOLO(articolo_id,prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo)"
                    + " VALUES (1,1,1,"
                        + comp
                        + ",1,'test')");
    }

    private static void await(CountDownLatch l) {
        try {
            l.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

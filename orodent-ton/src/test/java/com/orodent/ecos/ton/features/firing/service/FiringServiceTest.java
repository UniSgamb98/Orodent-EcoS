package com.orodent.ecos.ton.features.firing.service;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.ProducedDiscs;
import com.orodent.ecos.ton.features.equipment.model.Equipment;
import com.orodent.ecos.ton.features.equipment.service.EquipmentService;
import com.orodent.ecos.ton.features.firing.model.Firing;
import com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms;
import com.orodent.ecos.ton.features.firingprograms.service.FiringProgramService;
import com.orodent.ecos.ton.features.production.model.Production;
import com.orodent.ecos.ton.features.production.service.ProductionFixture;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

class FiringServiceTest {
    ConnectionProvider db;
    String url;
    ExecutorService workers;
    ProductionFixture fixture;
    FiringService service;
    EquipmentService equipment;
    FiringProgramService programs;
    FiringPrograms.Program program;
    final LocalDate loaded = LocalDate.of(2026, 11, 21);

    @BeforeEach
    void setup() {
        url = "jdbc:derby:memory:firing_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException failure) {
                        throw new IllegalStateException(failure);
                    }
                };
        new DatabaseSchema(db).initialize();
        workers = Executors.newFixedThreadPool(3);
        fixture = new ProductionFixture(db, workers);
        service = new FiringService(db, workers);
        equipment = new EquipmentService(db, workers);
        programs = new FiringProgramService(db, workers);
        programs.save(
                        null,
                        null,
                        new FiringPrograms.Draft(
                                "STD",
                                "Standard",
                                "",
                                true,
                                List.of(
                                        new FiringPrograms.Step(new BigDecimal("500"), 90, 5),
                                        new FiringPrograms.Step(null, 120, 60))),
                        true)
                .join();
        program = service.workspace().join().programs().getFirst();
        equipment
                .save(
                        Equipment.Kind.FURNACES,
                        null,
                        new Equipment.Draft("F1", "Forno uno", null, "", true))
                .join();
        equipment
                .save(
                        Equipment.Kind.FURNACES,
                        null,
                        new Equipment.Draft("F2", "Forno due", null, "", true))
                .join();
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
    }

    Production.Saved confirm() {
        return fixture.service.save(null, fixture.workspace(), fixture.request(false), true).join();
    }

    Firing.Quantity quantity(Firing.Group group, int count) {
        return new Firing.Quantity(group.key(), count);
    }

    Firing.Request load(Firing.Furnace furnace, String lot, Firing.Quantity... quantities) {
        return new Firing.Request(
                furnace,
                lot,
                loaded,
                "Carico registrato",
                List.of(quantities),
                program,
                new BigDecimal("1450"));
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
    void confirmationCreatesAllDiscsOnceAndOutputFailureRollsBackTheDraftAsWell() {
        var draft =
                fixture.service
                        .save(null, fixture.workspace(), fixture.request(false), false)
                        .join();
        assertEquals(0, count("DISCHI_PRODOTTI"));
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "ALTER TABLE TON.DISCHI_PRODOTTI ADD CONSTRAINT reject_test_output"
                                        + " CHECK (articolo_codice<>'ZR9814MSTA4')");
                    }
                    return null;
                });
        assertThrows(
                CompletionException.class,
                () ->
                        fixture.service
                                .save(draft, fixture.workspace(), fixture.request(false), true)
                                .join());
        assertEquals(Production.State.BOZZA, fixture.service.load(draft.id()).join().state());
        assertEquals(0, count("DISCHI_PRODOTTI"));
        assertEquals(0, count("ESECUZIONI_PIANO"));
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "ALTER TABLE TON.DISCHI_PRODOTTI DROP CONSTRAINT"
                                        + " reject_test_output");
                    }
                    return null;
                });
        var saved =
                fixture.service
                        .save(draft, fixture.workspace(), fixture.request(false), true)
                        .join();
        assertEquals(64, service.workspace().join().pending().size());
        assertEquals(3, Firing.group(service.workspace().join().pending()).size());
        db.withTransaction(
                c -> {
                    ProducedDiscs.confirm(c, saved.id());
                    return null;
                });
        assertEquals(64, count("DISCHI_PRODOTTI"));
        assertEquals(1, count("ESECUZIONI_PIANO"));
        assertThrows(
                CompletionException.class,
                () ->
                        fixture.service
                                .save(draft, fixture.workspace(), fixture.request(false), true)
                                .join());
        assertEquals(saved.plan(), fixture.service.load(saved.id()).join().plan());
        confirm();
        var groups = Firing.group(service.workspace().join().pending());
        assertEquals(6, groups.size());
        assertEquals(
                2,
                groups.stream().filter(g -> g.reference().article().equals("ZR9814MSTA4")).count());
    }

    @Test
    void quantitiesSplitAGroupBetweenFurnacesAndPreserveIndividualIdentitiesAndSnapshots() {
        var saved = confirm();
        var before = service.workspace().join();
        var group = Firing.group(before.pending()).getFirst();
        assertEquals(16, group.count());
        var original =
                before.pending().stream()
                        .filter(
                                d ->
                                        d.planId() == group.key().planId()
                                                && d.row() == group.key().row())
                        .map(Firing.Disc::id)
                        .toList();
        var first =
                service.create(load(before.furnaces().getFirst(), " lotto-a ", quantity(group, 3)))
                        .join();
        var second =
                service.create(load(before.furnaces().getLast(), "LOTTO-B", quantity(group, 2)))
                        .join();
        assertEquals("LOTTO-A", first.lot());
        assertEquals(59, service.workspace().join().pending().size());
        assertEquals(11, Firing.group(service.workspace().join().pending()).getFirst().count());
        assertEquals(
                original.subList(0, 3),
                service.loaded(first.id()).join().stream().map(Firing.Disc::id).toList());
        assertEquals(
                original.subList(3, 5),
                service.loaded(second.id()).join().stream().map(Firing.Disc::id).toList());
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "UPDATE TON.FORNI SET codice='F1-NUOVO',nome='Nome"
                                        + " nuovo',versione_modifica=versione_modifica+1 WHERE"
                                        + " forno_id="
                                        + first.furnaceId());
                        q.executeUpdate(
                                "UPDATE TON.ARTICOLI SET codice='CODICE-CORRETTO' WHERE"
                                        + " articolo_id="
                                        + fixture.articles.get(14));
                    }
                    return null;
                });
        var stored =
                service.workspace().join().loads().stream()
                        .filter(l -> l.id() == first.id())
                        .findFirst()
                        .orElseThrow();
        assertEquals("F1", stored.furnaceCode());
        assertEquals("Forno uno", stored.furnaceName());
        assertEquals(1, stored.furnaceRevision());
        assertEquals("ZR9814MSTA4", service.loaded(first.id()).join().getFirst().article());
        long specification = saved.plan().formats().getFirst().article().specificationId();
        db.withConnection(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "SELECT"
                                        + " d.specifica_prodotto_id,p.modello_id,p.composizione_id,l.codice,c.forno_id,c.temperatura_finale_c,c.programma_id,m.numero_versione,cp.numero_versione"
                                        + " FROM TON.DISCHI_PRODOTTI d JOIN TON.CARICO_DISCHI a ON"
                                        + " a.disco_id=d.disco_id JOIN TON.CICLI_COTTURA c ON"
                                        + " c.ciclo_id=a.ciclo_id JOIN TON.LOTTI_COTTURA l ON"
                                        + " l.lotto_id=a.lotto_id JOIN TON.CONFIGURAZIONI_PRODOTTO"
                                        + " p ON p.configurazione_id=d.specifica_prodotto_id JOIN"
                                        + " TON.MODELLI_DISCO m ON m.modello_id=p.modello_id JOIN"
                                        + " TON.COMPOSIZIONI cp ON"
                                        + " cp.composizione_id=p.composizione_id WHERE"
                                        + " d.articolo_codice=? AND l.codice=?")) {
                        q.setString(1, "ZR9814MSTA4");
                        q.setString(2, "LOTTO-A");
                        try (var r = q.executeQuery()) {
                            assertTrue(r.next());
                            assertEquals(specification, r.getLong(1));
                            assertEquals(fixture.model.revision().id(), r.getLong(2));
                            assertTrue(r.getLong(3) > 0);
                            assertEquals(first.furnaceId(), r.getLong(5));
                            assertEquals(0, new BigDecimal("1450").compareTo(r.getBigDecimal(6)));
                            assertEquals(program.id(), r.getLong(7));
                            assertEquals(saved.plan().reference().compositionId(), r.getLong(3));
                            assertEquals(saved.plan().reference().modelVersion(), r.getInt(8));
                            assertEquals(
                                    saved.plan().reference().compositionVersion(), r.getInt(9));
                        }
                    }
                    return null;
                });
    }

    @Test
    void
            loadsResolveTheExactVersionAndRememberProgramsPerFurnaceByRegistrationRatherThanLoadDate() {
        confirm();
        var data = service.workspace().join();
        var furnace = data.furnaces().getFirst();
        var other = data.furnaces().getLast();
        var group = Firing.group(data.pending()).getFirst();
        assertTrue(data.lastPrograms().isEmpty());
        var first =
                service.create(
                                new Firing.Request(
                                        furnace,
                                        "TARGET-1",
                                        loaded,
                                        "",
                                        List.of(quantity(group, 1)),
                                        program,
                                        new BigDecimal("1440")))
                        .join();
        assertEquals(program.id(), first.program().id());
        assertNull(first.previousProgram());
        assertFalse(first.programChanged());
        assertEquals(
                0, new BigDecimal("1440").compareTo(first.resolvedSteps().getLast().temperature()));
        assertTrue(first.program().steps().getLast().usesFinalTemperature());
        var original =
                programs.load(programs.list("STD", false).join().getFirst().id(), program.id())
                        .join();
        var copy = programs.copy(original.entry(), original.selected()).join();
        programs.save(
                        copy.entry(),
                        copy.selected(),
                        new FiringPrograms.Draft(
                                "STD",
                                "Nuova",
                                "",
                                true,
                                List.of(new FiringPrograms.Step(null, 30, 60))),
                        true)
                .join();
        assertEquals(
                program.id(), service.workspace().join().lastPrograms().get(furnace.id()).id());
        programs.save(
                        null,
                        null,
                        new FiringPrograms.Draft(
                                "ALTRO",
                                "Alternativo",
                                "",
                                true,
                                List.of(new FiringPrograms.Step(null, 45, 90))),
                        true)
                .join();
        var alternative =
                service.workspace().join().programs().stream()
                        .filter(p -> p.code().equals("ALTRO"))
                        .findFirst()
                        .orElseThrow();
        service.create(
                        new Firing.Request(
                                other,
                                "ALTRO-FORNO",
                                loaded,
                                "",
                                List.of(quantity(group, 1)),
                                alternative,
                                new BigDecimal("1490")))
                .join();
        assertEquals(
                program.id(), service.workspace().join().lastPrograms().get(furnace.id()).id());
        var changed =
                service.create(
                                new Firing.Request(
                                        furnace,
                                        "RETRODATATO",
                                        group.reference().producedAt(),
                                        "",
                                        List.of(quantity(group, 1)),
                                        alternative,
                                        new BigDecimal("1470")))
                        .join();
        assertTrue(changed.programChanged());
        assertEquals(program.id(), changed.previousProgram().id());
        var reloaded = new FiringService(db, workers).workspace().join();
        assertEquals(alternative.id(), reloaded.lastPrograms().get(furnace.id()).id());
        assertEquals(alternative.id(), reloaded.lastPrograms().get(other.id()).id());
        var historical =
                reloaded.loads().stream()
                        .filter(l -> l.id() == first.id())
                        .findFirst()
                        .orElseThrow();
        assertEquals(120, historical.program().steps().getLast().rampMinutes());
        assertEquals(
                0,
                new BigDecimal("1440")
                        .compareTo(historical.resolvedSteps().getLast().temperature()));
        assertThrows(
                CompletionException.class,
                () ->
                        service.create(
                                        new Firing.Request(
                                                furnace,
                                                "RETRODATATO",
                                                loaded,
                                                "",
                                                List.of(quantity(group, 1)),
                                                program,
                                                new BigDecimal("1600")))
                                .join());
        assertEquals(
                alternative.id(), service.workspace().join().lastPrograms().get(furnace.id()).id());
        var draft =
                programs.save(
                                null,
                                null,
                                new FiringPrograms.Draft(
                                        "BOZZA",
                                        "Bozza",
                                        "",
                                        true,
                                        List.of(new FiringPrograms.Step(null, 30, 30))),
                                false)
                        .join();
        var fake =
                new FiringPrograms.Program(
                        draft.selected().id(),
                        "FALSO",
                        "Falso",
                        1,
                        1,
                        List.of(new FiringPrograms.Step(null, 10, 10)));
        assertThrows(
                CompletionException.class,
                () ->
                        service.create(
                                        new Firing.Request(
                                                furnace,
                                                "NON-APPROVATA",
                                                loaded,
                                                "",
                                                List.of(quantity(group, 1)),
                                                fake,
                                                new BigDecimal("1500")))
                                .join());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Firing.Request(
                                furnace,
                                "SENZA-PROGRAMMA",
                                loaded,
                                "",
                                List.of(quantity(group, 1)),
                                null,
                                new BigDecimal("1500")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Firing.Request(
                                furnace,
                                "SENZA-TEMPERATURA",
                                loaded,
                                "",
                                List.of(quantity(group, 1)),
                                program,
                                null));
        var altDetail =
                programs.load(
                                programs.list("ALTRO", false).join().getFirst().id(),
                                alternative.id())
                        .join();
        var archival = programs.copy(altDetail.entry(), altDetail.selected()).join();
        programs.save(
                        archival.entry(),
                        archival.selected(),
                        new FiringPrograms.Draft(
                                "ALTRO", "Archiviato", "", false, alternative.steps()),
                        false)
                .join();
        assertTrue(
                service.workspace().join().programs().stream()
                        .noneMatch(p -> p.code().equals("ALTRO")));
        assertEquals(
                alternative.id(), service.workspace().join().lastPrograms().get(furnace.id()).id());
        assertThrows(
                CompletionException.class,
                () ->
                        service.create(
                                        new Firing.Request(
                                                furnace,
                                                "ARCHIVIATO",
                                                loaded,
                                                "",
                                                List.of(quantity(group, 1)),
                                                alternative,
                                                new BigDecimal("1450")))
                                .join());
    }

    @Test
    void concurrentLoadsOnOneFurnacePreserveTheActualPreviousProgram() {
        confirm();
        var data = service.workspace().join();
        var furnace = data.furnaces().getFirst();
        var group = Firing.group(data.pending()).getFirst();
        programs.save(
                        null,
                        null,
                        new FiringPrograms.Draft(
                                "B", "B", "", true, List.of(new FiringPrograms.Step(null, 30, 60))),
                        true)
                .join();
        var alternative =
                service.workspace().join().programs().stream()
                        .filter(p -> p.code().equals("B"))
                        .findFirst()
                        .orElseThrow();
        var one =
                service.create(
                        new Firing.Request(
                                furnace,
                                "SERIE-A",
                                loaded,
                                "",
                                List.of(quantity(group, 1)),
                                program,
                                new BigDecimal("1400")));
        var two =
                service.create(
                        new Firing.Request(
                                furnace,
                                "SERIE-B",
                                loaded,
                                "",
                                List.of(quantity(group, 1)),
                                alternative,
                                new BigDecimal("1450")));
        one.join();
        two.join();
        var loads = service.workspace().join().loads();
        assertEquals(1, loads.stream().filter(l -> l.previousProgram() == null).count());
        var first =
                loads.stream().filter(l -> l.previousProgram() == null).findFirst().orElseThrow();
        var last =
                loads.stream().filter(l -> l.previousProgram() != null).findFirst().orElseThrow();
        assertEquals(first.program().id(), last.previousProgram().id());
        assertTrue(last.programChanged());
        assertEquals(
                last.program().id(),
                service.workspace().join().lastPrograms().get(furnace.id()).id());
    }

    @Test
    void duplicateLotsInsufficientQuantitiesAndStaleFurnacesRollBackTheWholeLoad() {
        confirm();
        var data = service.workspace().join();
        var group = Firing.group(data.pending()).getFirst();
        var furnace = data.furnaces().getFirst();
        service.create(load(furnace, "LOTTO-UNICO", quantity(group, 1))).join();
        assertThrows(
                CompletionException.class,
                () -> service.create(load(furnace, "LOTTO-UNICO", quantity(group, 1))).join());
        assertThrows(
                CompletionException.class,
                () -> service.create(load(furnace, "ALTRO", quantity(group, 16))).join());
        assertThrows(
                CompletionException.class,
                () ->
                        service.create(
                                        new Firing.Request(
                                                furnace,
                                                "PRIMA",
                                                LocalDate.of(2026, 11, 18),
                                                "",
                                                List.of(quantity(group, 1)),
                                                program,
                                                new BigDecimal("1450")))
                                .join());
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "UPDATE TON.FORNI SET"
                                    + " attivo=FALSE,versione_modifica=versione_modifica+1 WHERE"
                                    + " forno_id="
                                        + furnace.id());
                    }
                    return null;
                });
        assertThrows(
                CompletionException.class,
                () -> service.create(load(furnace, "ARCHIVIATO", quantity(group, 1))).join());
        assertEquals(1, count("CICLI_COTTURA"));
        assertEquals(1, count("LOTTI_COTTURA"));
        assertEquals(1, count("CARICO_DISCHI"));
        assertEquals(63, service.workspace().join().pending().size());
    }

    @Test
    void concurrentOperatorsCannotOverAllocateTheSameGroup() {
        confirm();
        var data = service.workspace().join();
        var group = Firing.group(data.pending()).getFirst();
        var one =
                service.create(
                        load(data.furnaces().getFirst(), "CONCORRENTE-A", quantity(group, 16)));
        var two =
                service.create(
                        load(data.furnaces().getLast(), "CONCORRENTE-B", quantity(group, 16)));
        var outcomes =
                List.of(one, two).stream()
                        .map(f -> f.handle((value, error) -> error == null).join())
                        .toList();
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(16, count("CARICO_DISCHI"));
        assertEquals(1, count("CICLI_COTTURA"));
        assertEquals(48, service.workspace().join().pending().size());
    }

    @Test
    void concurrentPartialLoadsAllocateDifferentDiscs() {
        confirm();
        var data = service.workspace().join();
        var group = Firing.group(data.pending()).getFirst();
        var one =
                service.create(load(data.furnaces().getFirst(), "PARZIALE-A", quantity(group, 8)));
        var two = service.create(load(data.furnaces().getLast(), "PARZIALE-B", quantity(group, 8)));
        var first = one.join();
        var second = two.join();
        var ids =
                new HashSet<>(
                        service.loaded(first.id()).join().stream().map(Firing.Disc::id).toList());
        assertTrue(
                Collections.disjoint(
                        ids,
                        service.loaded(second.id()).join().stream().map(Firing.Disc::id).toList()));
        assertEquals(16, count("CARICO_DISCHI"));
        assertEquals(48, service.workspace().join().pending().size());
    }

    @Test
    void upgradesAlreadyPreparedPlansWithoutDuplicatingRegisteredOrLoadedDiscs() {
        var saved = confirm();
        var initial = service.workspace().join();
        var group = Firing.group(initial.pending()).getFirst();
        var load =
                service.create(
                                load(
                                        initial.furnaces().getFirst(),
                                        "PRIMA-UPDATE",
                                        quantity(group, 1)))
                        .join();
        long originalId = service.loaded(load.id()).join().getFirst().id();
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "DELETE FROM TON.DISCHI_PRODOTTI WHERE piano_id="
                                        + saved.id()
                                        + " AND (numero_riga>1 OR progressivo>3)");
                        q.executeUpdate(
                                "UPDATE TON.ARTICOLI SET codice='CODICE-NUOVO' WHERE articolo_id="
                                        + fixture.articles.get(14));
                        q.executeUpdate(
                                "ALTER TABLE TON.PIANI_PRODUZIONE DROP COLUMN dischi_confermati_il"
                                        + " RESTRICT");
                        // Recreate a genuine pre-V30 fixture, including the subsequent program
                        // migrations.
                        for (String sql :
                                List.of(
                                        "DROP TABLE TON.ETICHETTA_DISCHI",
                                        "DROP TABLE TON.ETICHETTE_EMESSE",
                                        "ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO DROP CONSTRAINT"
                                                + " fk_ton_cfg_etichetta",
                                        "ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO DROP COLUMN"
                                                + " etichetta_modello_id RESTRICT",
                                        "ALTER TABLE TON.ARTICOLI DROP CONSTRAINT uq_ton_art_gtin",
                                        "ALTER TABLE TON.ARTICOLI DROP CONSTRAINT ck_ton_art_gtin",
                                        "ALTER TABLE TON.ARTICOLI DROP COLUMN gtin RESTRICT",
                                        "ALTER TABLE TON.ARTICOLI_REVISIONI DROP COLUMN gtin"
                                                + " RESTRICT",
                                        "ALTER TABLE TON.MODELLI_DOCUMENTO DROP CONSTRAINT"
                                                + " ck_ton_docm_tipo",
                                        "ALTER TABLE TON.MODELLI_DOCUMENTO ADD CONSTRAINT"
                                                + " ck_ton_docm_tipo CHECK (tipo IN"
                                                + " ('PRODUZIONE','COTTURA'))",
                                        "DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=34",
                                        "DROP TABLE TON.DISCO_MISURE_REVISIONI",
                                        "ALTER TABLE TON.CICLI_COTTURA DROP CONSTRAINT"
                                                + " fk_ton_cic_prog_precedente",
                                        "ALTER TABLE TON.CICLI_COTTURA DROP CONSTRAINT"
                                                + " ck_ton_cic_target",
                                        "ALTER TABLE TON.CICLI_COTTURA DROP CONSTRAINT"
                                                + " ck_ton_cic_prog_precedente",
                                        "ALTER TABLE TON.CICLI_COTTURA DROP COLUMN"
                                                + " programma_precedente_id RESTRICT",
                                        "ALTER TABLE TON.CICLI_COTTURA DROP COLUMN"
                                                + " temperatura_finale_c RESTRICT",
                                        "UPDATE TON.PROGRAMMA_PASSI SET"
                                                + " temperatura_obiettivo_c=1450 WHERE"
                                                + " temperatura_obiettivo_c IS NULL",
                                        "ALTER TABLE TON.PROGRAMMA_PASSI ALTER COLUMN"
                                                + " temperatura_obiettivo_c NOT NULL",
                                        "ALTER TABLE TON.PROGRAMMI_COTTURA DROP CONSTRAINT"
                                                + " fk_ton_programma_famiglia",
                                        "ALTER TABLE TON.PROGRAMMI_COTTURA DROP CONSTRAINT"
                                                + " uq_ton_prog_revisione",
                                        "ALTER TABLE TON.PROGRAMMI_COTTURA DROP CONSTRAINT"
                                                + " ck_ton_prog_num_rev",
                                        "ALTER TABLE TON.PROGRAMMI_COTTURA DROP COLUMN famiglia_id"
                                                + " RESTRICT",
                                        "ALTER TABLE TON.PROGRAMMI_COTTURA DROP COLUMN"
                                                + " numero_revisione RESTRICT",
                                        "ALTER TABLE TON.PROGRAMMI_COTTURA DROP COLUMN attivo"
                                                + " RESTRICT",
                                        "ALTER TABLE TON.PROGRAMMI_COTTURA ADD CONSTRAINT"
                                                + " uq_ton_prog_versione"
                                                + " UNIQUE(codice,numero_versione)",
                                        "DROP TABLE TON.PROGRAMMI_FAMIGLIE",
                                        "DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version>=30"))
                            q.executeUpdate(sql);
                    }
                    return null;
                });
        new DatabaseSchema(db).initialize();
        new DatabaseSchema(db).initialize();
        assertEquals(64, count("DISCHI_PRODOTTI"));
        assertEquals(63, service.workspace().join().pending().size());
        assertEquals(originalId, service.loaded(load.id()).join().getFirst().id());
        assertEquals(
                "ZR9814MSTA4",
                Firing.group(service.workspace().join().pending())
                        .getFirst()
                        .reference()
                        .article());
        assertEquals(saved.plan(), fixture.service.load(saved.id()).join().plan());
        assertEquals(2, count("ESECUZIONI_PIANO"));
    }
}

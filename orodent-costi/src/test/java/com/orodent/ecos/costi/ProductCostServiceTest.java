package com.orodent.ecos.costi;

import static com.orodent.ecos.costi.ProductCostCalculatorTest.n;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.core.csv.*;
import com.orodent.ecos.costi.products.csv.ProductCostCsv;
import com.orodent.ecos.costi.products.model.PowderPricing;
import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.products.service.*;
import com.orodent.ecos.database.DatabaseSchema;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class ProductCostServiceTest {
    @TempDir Path output;
    private ConnectionProvider db;
    private ExecutorService workers;
    private ProductCostService service;
    private String url, oldLog;
    private final AtomicInteger onWorker = new AtomicInteger(), opened = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        oldLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/product-costs-service.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:product_costs_" + UUID.randomUUID().toString().replace("-", "");
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
        workers = Executors.newFixedThreadPool(2, r -> new Thread(r, "product-costs-worker"));
        service =
                new ProductCostService(
                        () -> {
                            opened.incrementAndGet();
                            if (Thread.currentThread().getName().equals("product-costs-worker"))
                                onWorker.incrementAndGet();
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

    private Parameters parameters(int version) {
        return new Parameters(version, n("12"), Map.of());
    }

    private List<Result> calculate(Catalogue catalogue) {
        return service.calculate(catalogue, List.of(1L), Map.of(1L, parameters(0)), false, false)
                .join();
    }

    @Test
    void valuationUsesSnapshotLotsAndOutstandingOrdersAndCsvDescribesTheSelectedPolicy() {
        var date = java.time.LocalDate.now().plusDays(10);
        ProductCostFixture.execute(
                db,
                "UPDATE COSTI.POLVERI_PREZZI SET"
                        + " modello_dati=2,prezzo_riferimento=100,quantita_totale=100 WHERE"
                        + " prezzo_id=2",
                "INSERT INTO COSTI.POLVERI_LOTTI(prezzo_id,posizione,lotto,quantita_kg,prezzo_kg)"
                        + " VALUES (2,1,'WHITE-LOT',80,80)",
                "INSERT INTO COSTI.ORDINI_POLVERI(fornitore,numero_ordine,posizione) VALUES"
                        + " ('F','ORD',1),('F','ORD',2)",
                "INSERT INTO"
                    + " COSTI.ORDINI_POLVERI_REVISIONI(ordine_id,numero_versione,polvere_id,codice_sorgente,descrizione,data_ordine,consegna_prevista,quantita_ordinata,quantita_consegnata,quantita_residua,prezzo_kg,escluso,origine)"
                    + " VALUES (1,1,1,'WHITE','',CURRENT_DATE,DATE('"
                        + date
                        + "'),150,50,100,116,FALSE,'CSV'),(2,1,1,'WHITE','',CURRENT_DATE,DATE('"
                        + date
                        + "'),1000,0,1000,1,TRUE,'CSV')");
        Catalogue raw = service.load().join();
        assertEquals(1, raw.stocks().get(1L).receipts().size());
        var advanced =
                PowderPricing.apply(
                        raw, new PowderPricing.Selection(PowderPricing.Mode.AVANZATA, false));
        assertEquals(0, n("84").compareTo(advanced.prices().get(1L).price()));
        var result = calculate(advanced).getFirst();
        assertEquals(0, n("97.92").compareTo(result.nominalMixPrice()));
        assertTrue(result.materials().getFirst().price().estimated());
        var incoming =
                PowderPricing.apply(
                        advanced, new PowderPricing.Selection(PowderPricing.Mode.AVANZATA, true));
        assertEquals(0, n("100").compareTo(incoming.prices().get(1L).price()));
        var rows = calculate(incoming);
        var mapper = ProductCostCsv.summary(incoming.capturedAt());
        assertTrue(
                mapper.map(rows.getFirst())
                        .get(mapper.headers().indexOf("Fonte prezzi"))
                        .toString()
                        .contains("ordini"));
        var details = ProductCostCsv.detail(incoming.capturedAt());
        var detail = ProductCostCsv.details(rows).getFirst();
        assertTrue(
                details.map(detail)
                        .get(details.headers().indexOf("Dettaglio valutazione"))
                        .toString()
                        .contains("100 kg × 116 €/kg"));
        assertEquals(0, n("100").compareTo(raw.prices().get(1L).price()));
    }

    @Test
    void readsLatestEconomicIdentityPricesButPinnedTechnicalRevisionsOnWorker() {
        Catalogue catalogue = service.load().join();
        assertEquals(2, catalogue.products().size());
        assertEquals(0, n("200").compareTo(catalogue.prices().get(2L).price()));
        Result first = calculate(catalogue).getFirst();
        assertEquals(1, first.specification().version());
        assertEquals(1, first.materials().getFirst().ingredient().technicalVersion());
        assertEquals(2, first.materials().getFirst().price().version());
        assertEquals(0, n("48.6825").compareTo(first.total()));
        assertNull(calculate(catalogue).get(1).total());
        assertEquals(opened.get(), onWorker.get());
    }

    @Test
    void savesVersionedCommonAndArticleCostsWithOptimisticConflict() {
        Parameters first =
                service.save(1, new Parameters(0, n("12"), Map.of(1L, n("0"), 2L, n("18")))).join();
        assertEquals(1, first.version());
        Parameters second = service.save(1, new Parameters(1, n("15"), Map.of(1L, n("0")))).join();
        assertEquals(2, second.version());
        Catalogue reloaded = service.load().join();
        assertEquals(second, reloaded.parameters(1));
        var results = service.calculate(reloaded, List.of(1L), Map.of(), false, false).join();
        assertEquals(0, n("36.6825").compareTo(results.getFirst().total()));
        assertEquals(2, scalar("SELECT COUNT(*) FROM COSTI.PRODOTTI_PARAMETRI"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM COSTI.ARTICOLI_TRASFORMAZIONE"));
        assertInstanceOf(
                Conflict.class,
                assertThrows(CompletionException.class, () -> service.save(1, parameters(0)).join())
                        .getCause());
        assertEquals(1, scalar("SELECT versione_modifica FROM TON.ARTICOLI WHERE articolo_id=1"));
        assertEquals(1, scalar("SELECT versione_modifica FROM TON.PRODOTTI WHERE prodotto_id=1"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM TON.PRODOTTI_REVISIONI"));
        assertEquals(
                2,
                scalar("SELECT MAX(numero_versione) FROM COSTI.POLVERI_PREZZI WHERE polvere_id=1"));
    }

    @RepeatedTest(5)
    void preventsCrossProductOverridesAndConcurrentFirstSaves() {
        assertThrows(
                CompletionException.class,
                () -> service.save(1, new Parameters(0, n("12"), Map.of(3L, n("5")))).join());
        assertEquals(0, scalar("SELECT COUNT(*) FROM COSTI.PRODOTTI_PARAMETRI"));
        var left = service.save(1, parameters(0));
        var right = service.save(1, parameters(0));
        int successes = 0, conflicts = 0;
        for (var result : List.of(left, right)) {
            try {
                result.join();
                successes++;
            } catch (CompletionException e) {
                assertInstanceOf(
                        Conflict.class,
                        e.getCause(),
                        () -> String.valueOf(e.getCause().getCause()));
                conflicts++;
            }
        }
        assertEquals(1, successes);
        assertEquals(1, conflicts);
        assertEquals(1, scalar("SELECT COUNT(*) FROM COSTI.PRODOTTI_PARAMETRI"));
    }

    @Test
    void snapshotAndCsvRemainStableAfterNewPricesAndCoverEveryProductBeyondFirstPage() {
        Catalogue before = service.load().join();
        ProductCostFixture.execute(
                db,
                "INSERT INTO"
                    + " COSTI.POLVERI_PREZZI(polvere_id,numero_versione,data_riferimento,modalita,prezzo_semplice)"
                    + " VALUES (1,3,DATE('2026-11-10'),'SEMPLICE',200)");
        Result old = calculate(before).getFirst(),
                updated = calculate(service.load().join()).getFirst();
        assertEquals(0, n("48.6825").compareTo(old.total()));
        assertTrue(updated.total().compareTo(old.total()) > 0);
        var extra = new ArrayList<String>();
        for (int i = 0; i < 45; i++)
            extra.add("INSERT INTO TON.PRODOTTI(codice) VALUES ('EXTRA" + i + "')");
        ProductCostFixture.execute(db, extra.toArray(String[]::new));
        Catalogue all = service.load().join();
        var rows =
                service.calculate(
                                all,
                                all.products().stream().map(Product::id).toList(),
                                Map.of(1L, parameters(0)),
                                false,
                                true)
                        .join();
        assertEquals(47, rows.stream().map(r -> r.product().id()).distinct().count());
        assertTrue(rows.stream().anyMatch(r -> !r.product().active()));
        Path summary = output.resolve("all.csv");
        var result =
                new CsvExporter<>(new CsvWriter(), ProductCostCsv.summary(all.capturedAt()))
                        .exportFile(summary, rows, CsvWriteOptions.excelItalian());
        assertEquals(rows.size(), result.exportedRows());
        CsvDocument parsed = new CsvReader().read(summary, CsvReadOptions.semicolonSeparated());
        assertEquals(rows.size(), parsed.rows().size());
        var unknown =
                parsed.rows().stream()
                        .filter(r -> r.requiredValue("Articolo").equals("MSTA2-98-14"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("", unknown.values().get("Costo totale (EUR/articolo)"));
        Path detail = output.resolve("detail.csv");
        var materials = ProductCostCsv.details(List.of(old));
        new CsvExporter<>(new CsvWriter(), ProductCostCsv.detail(before.capturedAt()))
                .exportFile(detail, materials, CsvWriteOptions.excelItalian());
        var lines = new CsvReader().read(detail, CsvReadOptions.semicolonSeparated());
        assertEquals(3, lines.rows().size());
        assertEquals(
                0,
                n("6.03")
                        .compareTo(
                                new BigDecimal(
                                        lines.rows()
                                                .getFirst()
                                                .values()
                                                .get("Costo riga (EUR)")
                                                .replace(',', '.'))));
    }

    @Test
    void usesArticleDimensionsPinnedByTheApprovedConfiguration() {
        ProductCostFixture.execute(
                db,
                "INSERT INTO"
                    + " TON.LINEE_REVISIONI(linea_id,numero_revisione,codice,nome,attivo,origine)"
                    + " VALUES (1,1,'MST','Thor',TRUE,'CREAZIONE')",
                "INSERT INTO"
                    + " TON.PRODOTTI_REVISIONI(prodotto_id,numero_revisione,codice,linea_id,linea_codice,colore_commerciale,attivo,origine)"
                    + " VALUES (1,1,'MSTA2',1,'MST','A2',TRUE,'CREAZIONE')",
                "INSERT INTO"
                    + " TON.ARTICOLI_REVISIONI(articolo_id,numero_revisione,codice,prodotto_id,prodotto_codice,diametro_mm,altezza_mm,attivo,origine)"
                    + " VALUES (1,1,'MSTA2-98-10',1,'MSTA2',98,10,TRUE,'CREAZIONE')",
                "UPDATE TON.CONFIGURAZIONI_ARTICOLO SET"
                        + " linea_id=1,linea_revisione=1,prodotto_revisione=1,articolo_revisione=1"
                        + " WHERE configurazione_id=1",
                "UPDATE TON.ARTICOLI SET altezza_mm=18 WHERE articolo_id=1");
        Result result =
                calculate(service.load().join()).stream()
                        .filter(r -> r.article().id() == 1)
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, n("10").compareTo(result.specification().height()));
        assertEquals(0, n("18").compareTo(result.article().height()));
        assertEquals(0, n("48.6825").compareTo(result.total()));
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

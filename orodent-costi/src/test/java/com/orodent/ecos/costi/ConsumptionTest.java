package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.core.csv.*;
import com.orodent.ecos.costi.consumption.*;
import com.orodent.ecos.costi.consumption.Consumption.*;
import com.orodent.ecos.database.DatabaseSchema;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;

class ConsumptionTest {
    @TempDir Path directory;
    private ConnectionProvider db;
    private String url, oldLog;

    static BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    @BeforeEach
    void setup() throws Exception {
        oldLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/consumption.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:consumption_" + UUID.randomUUID().toString().replace("-", "");
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
        seed(db);
    }

    static void seed(ConnectionProvider db) {
        ProductCostFixture.execute(
                db,
                "UPDATE COSTI.POLVERI_PREZZI SET quantita_semplice=100 WHERE polvere_id=1",
                "INSERT INTO TON.PRODUZIONI(numero,linea_id,data_produzione,stato,confermata_il)"
                        + " VALUES ('P1',1,DATE('2026-11-03'),'CONFERMATA',CURRENT_TIMESTAMP)",
                "INSERT INTO"
                    + " TON.PRODUZIONE_RIGHE(produzione_id,numero_riga,configurazione_id,quantita_prodotta)"
                    + " VALUES (1,1,1,10)",
                "INSERT INTO"
                    + " VENDITE.VENDITE_DETTAGLIO(sorgente,documento_id,numero_documento,numero_riga,data_vendita,codice_cliente,codice_prodotto,quantita,tipo_operazione)"
                    + " VALUES"
                    + " ('TEST','S1','S1',1,DATE('2026-11-04'),'C1','MSTA2-98-10',20,'VENDITA')");
    }

    private Request request() {
        return new Request(
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8),
                LocalDate.of(2026, 11, 9),
                30,
                CalendarMode.CALENDARIO);
    }

    private Analysis load() {
        return new ConsumptionService(db, Runnable::run).load(request()).join();
    }

    private void value(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, n(expected).compareTo(actual));
    }

    @AfterEach
    void close() throws Exception {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
        if (oldLog == null) System.clearProperty("derby.stream.error.file");
        else System.setProperty("derby.stream.error.file", oldLog);
    }

    @Test
    void optionalReceiptsUseRemainingQuantityAndExcludeOverdueUndatedAndExcludedRows() {
        ProductCostFixture.execute(
                db,
                "INSERT INTO COSTI.ORDINI_POLVERI(fornitore,numero_ordine,posizione) VALUES"
                    + " ('F','A',1),('F','B',1),('F','C',1),('F','D',1)",
                "INSERT INTO"
                    + " COSTI.ORDINI_POLVERI_REVISIONI(ordine_id,numero_versione,polvere_id,codice_sorgente,descrizione,data_ordine,consegna_prevista,quantita_ordinata,quantita_consegnata,quantita_residua,prezzo_kg,escluso,origine)"
                    + " VALUES"
                    + " (1,1,1,'WHITE','',DATE('2026-11-01'),DATE('2026-11-12'),100,40,60,55,FALSE,'CSV'),(2,1,1,'WHITE','',DATE('2026-11-01'),DATE('2026-11-08'),100,0,100,55,FALSE,'CSV'),(3,1,1,'WHITE','',DATE('2026-11-01'),NULL,100,0,100,55,FALSE,'CSV'),(4,1,1,'WHITE','',DATE('2026-11-01'),DATE('2026-11-12'),100,0,100,55,TRUE,'CSV')");
        var base = load();
        var r = request();
        var included =
                new ConsumptionService(db, Runnable::run)
                        .load(
                                new Request(
                                        r.from(),
                                        r.through(),
                                        r.today(),
                                        r.horizon(),
                                        r.calendar(),
                                        true))
                        .join();
        assertEquals(3, included.snapshot().arrivals().size());
        for (var series : included.series()) {
            var original =
                    base.series().stream()
                            .filter(
                                    o ->
                                            o.powder().id() == series.powder().id()
                                                    && o.basis() == series.basis())
                            .findFirst()
                            .orElseThrow();
            assertTrue(original.arrivals().isEmpty());
            if (series.powder().id() == 1) {
                assertEquals(1, series.arrivals().size());
                value("60", series.arrivals().get(LocalDate.of(2026, 11, 12)));
                value("0", Consumption.receipts(series, LocalDate.of(2026, 11, 11)));
                value("60", Consumption.receipts(series, LocalDate.of(2026, 11, 12)));
            }
        }
        assertTrue(included.warnings().stream().anyMatch(w -> w.contains("scaduti")));
    }

    @Test
    void separatePowdersAndSourcesUseAllDaysAndPinnedProductionConfiguration() {
        Analysis a = load();
        assertEquals(4, a.series().size());
        Series white =
                a.series().stream()
                        .filter(s -> s.powder().id() == 1 && s.basis() == Basis.PRODUZIONI)
                        .findFirst()
                        .orElseThrow();
        value("2.86425", white.daily().get(LocalDate.of(2026, 11, 3)));
        value("0.35803125", white.rate());
        Series sales =
                a.series().stream()
                        .filter(s -> s.powder().id() == 1 && s.basis() == Basis.VENDITE)
                        .findFirst()
                        .orElseThrow();
        value("0.7160625", sales.rate());
        assertTrue(white.complete());
        value("100", Consumption.stockOn(white, request().today(), request().calendar()));
        assertNull(
                Consumption.stockOn(white, request().today().minusDays(1), request().calendar()));
        ProductCostFixture.execute(
                db,
                "UPDATE TON.CONFIGURAZIONI_ARTICOLO SET stato='RITIRATA' WHERE"
                        + " configurazione_id=1");
        Analysis retired = load();
        assertTrue(
                retired.series().stream()
                        .filter(s -> s.basis() == Basis.PRODUZIONI)
                        .allMatch(Series::complete));
        assertTrue(
                retired.series().stream()
                        .filter(s -> s.basis() == Basis.VENDITE)
                        .allMatch(s -> !s.complete()));
    }

    @Test
    void simulationUsesMassEvenWithoutPricesAndNeverChangesStock() {
        Analysis a = load();
        ProductCostFixture.execute(
                db, "DELETE FROM COSTI.POLVERI_LOTTI", "DELETE FROM COSTI.POLVERI_PREZZI");
        Map<Long, BigDecimal> needed =
                new ConsumptionEngine().plan(a.snapshot(), List.of(new PlanLine(1, 1, 20)));
        value("5.7285", needed.get(1L));
        value("0.804", needed.get(2L));
        Series white = a.series().getFirst();
        Balance b =
                Consumption.balance(
                        white,
                        request().today(),
                        request().calendar(),
                        needed.get(white.powder().id()));
        assertNotNull(b.residual());
        assertTrue(b.afterDays().compareTo(b.beforeDays()) < 0);
        value(
                "100",
                a.snapshot().powders().stream()
                        .filter(p -> p.id() == 1)
                        .findFirst()
                        .orElseThrow()
                        .stock());
        assertThrows(IllegalArgumentException.class, () -> new PlanLine(1, 1, 0));
    }

    @Test
    void minimumDaysVersioningAndWeightedLotQuantitiesAreIndependentFromPrice() {
        ConsumptionService service = new ConsumptionService(db, Runnable::run);
        Settings saved = service.save(1, new Settings(0, Unit.GIORNI, n("15"))).join();
        assertEquals(1, saved.version());
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> service.save(1, new Settings(0, Unit.KG, n("5"))).join());
        Analysis a = load();
        Series white =
                a.series().stream()
                        .filter(s -> s.powder().id() == 1 && s.basis() == Basis.PRODUZIONI)
                        .findFirst()
                        .orElseThrow();
        Balance b = Consumption.balance(white, request().today(), request().calendar(), n("99"));
        value("5.37046875", b.minimumKg());
        value("4.37046875", b.buy());
        value("0", b.daysToMinimum());
        value(
                "150",
                a.snapshot().powders().stream()
                        .filter(p -> p.id() == 2)
                        .findFirst()
                        .orElseThrow()
                        .stock());
        new CsvExporter<>(new CsvWriter(), ConsumptionCsv.balances(a, "TEST"))
                .exportFile(
                        directory.resolve("balances.csv"),
                        List.of(b),
                        CsvWriteOptions.excelItalian());
        var parsed =
                new CsvReader()
                        .read(
                                directory.resolve("balances.csv"),
                                CsvReadOptions.semicolonSeparated());
        assertEquals("WHITE", parsed.rows().getFirst().requiredValue("Polvere"));
    }

    @Test
    void unknownArticlesInvalidateOnlySalesAndReturnsDoNotCreatePowder() {
        ProductCostFixture.execute(
                db,
                "INSERT INTO"
                    + " VENDITE.VENDITE_DETTAGLIO(sorgente,documento_id,numero_documento,numero_riga,data_vendita,codice_cliente,codice_prodotto,quantita,tipo_operazione)"
                    + " VALUES"
                    + " ('TEST','S2','S2',1,DATE('2026-11-04'),'C1','UNMAPPED',1,'VENDITA')");
        Analysis a = load();
        assertTrue(
                a.series().stream()
                        .filter(s -> s.basis() == Basis.VENDITE)
                        .allMatch(s -> s.rate() == null));
        assertTrue(
                a.series().stream()
                        .filter(s -> s.basis() == Basis.PRODUZIONI)
                        .allMatch(Series::complete));
        assertTrue(a.warnings().stream().anyMatch(w -> w.contains("UNMAPPED")));
    }

    @Test
    void workingCalendarAndMissingDataNeverImplyInfiniteCoverage() {
        assertEquals(
                5,
                Consumption.days(
                        LocalDate.of(2026, 11, 2),
                        LocalDate.of(2026, 11, 8),
                        CalendarMode.LUNEDI_VENERDI));
        assertNull(Consumption.divide(BigDecimal.ONE, BigDecimal.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Request(
                                request().from(),
                                request().today(),
                                request().today(),
                                30,
                                CalendarMode.CALENDARIO));
        Analysis a = load();
        var empty =
                new Snapshot(
                        a.snapshot().catalogue(),
                        a.snapshot().powders(),
                        List.of(),
                        List.of(),
                        a.snapshot().approved());
        assertTrue(
                new ConsumptionEngine()
                        .analyse(empty, request()).series().stream()
                                .allMatch(s -> s.rate() == null));
    }
}

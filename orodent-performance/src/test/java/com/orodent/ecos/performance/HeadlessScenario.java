package com.orodent.ecos.performance;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.statistiche.features.sales.analysis.service.SalesAnalysisService;
import com.orodent.ecos.statistiche.features.sales.dashboard.service.SalesDashboardService;
import com.orodent.ecos.statistiche.features.sales.products.model.ProductGrouping;
import com.orodent.ecos.statistiche.features.sales.products.service.ProductAnalysisService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Separate JVM: the embedded engine and query caches cannot leak across measurement runs. */
public final class HeadlessScenario {
    public static void main(String[] arguments) throws Exception {
        Path run = Path.of(arguments[0]).toAbsolutePath();
        PerformanceSettings settings = PerformanceSettings.load(run);
        Path database = PerformanceSettings.database(run);
        if (Files.exists(database)) throw new IllegalStateException("La prova richiede un nuovo database generato.");
        Files.createDirectories(database.getParent());
        System.setProperty("derby.stream.error.file", run.resolve("headless-derby.log").toString());
        String url = "jdbc:derby:" + database;
        var workers = Executors.newFixedThreadPool(3);
        ConnectionProvider connections = () -> {
            try { return DriverManager.getConnection(url, "APP", "pw"); }
            catch (SQLException failure) { throw new IllegalStateException(failure); }
        };
        SqlMeasurements sql = new SqlMeasurements(connections);
        try (Samples samples = new Samples(run.resolve("headless-samples.csv"))) {
            long started = System.nanoTime();
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            try (var ignored = DriverManager.getConnection(url + ";create=true", "APP", "pw")) { }
            new DatabaseSchema(connections).initialize();
            samples.record("database.create_schema", 0, System.nanoTime() - started);
            started = System.nanoTime();
            SyntheticDataset.populate(connections, settings, run);
            samples.record("dataset.generate", 0, System.nanoTime() - started);
            var dashboard = new SalesDashboardService(sql, workers);
            var analysis = new SalesAnalysisService(sql, workers);
            var products = new ProductAnalysisService(sql, workers);
            Properties dataset = new Properties();
            try (var input = Files.newInputStream(run.resolve("dataset.properties"))) { dataset.load(input); }
            BigDecimal expected = new BigDecimal(dataset.getProperty("expected.lastYear.netRevenue"));
            int year = settings.lastYear();
            int timeout = settings.timeoutSeconds();
            var operations = new LinkedHashMap<String, Callable<?>>();
            operations.put("dashboard", () -> {
                var data = dashboard.load(year, null, null).get(timeout, TimeUnit.SECONDS);
                check(data.selectedYear() == year && data.summary().netRevenue().compareTo(expected) == 0,
                        "Fatturato diverso da quello del dataset generato");
                return data;
            });
            operations.put("customers.overview", () -> {
                var data = analysis.loadCustomerOverview(year).get(timeout, TimeUnit.SECONDS);
                check(!data.customers().items().isEmpty(), "Analisi clienti vuota sul dataset generato");
                return data;
            });
            operations.put("customers.detail", () -> {
                var data = analysis.loadCustomerDetail("C00001", year, 2).get(timeout, TimeUnit.SECONDS);
                check(data.detail().code().equals("C00001"), "Dettaglio cliente non corrispondente");
                return data;
            });
            operations.put("products.overview", () -> {
                var data = products.load(year, ProductGrouping.FAMILY).get(timeout, TimeUnit.SECONDS);
                check(!data.snapshot().sales().isEmpty(), "Analisi prodotti vuota sul dataset generato");
                return data;
            });
            operations.put("discounts", () -> analysis.loadDiscounts(year).get(timeout, TimeUnit.SECONDS));
            operations.put("returns", () -> analysis.loadReturns(year).get(timeout, TimeUnit.SECONDS));
            operations.put("archive", () -> {
                var data = analysis.loadArchive(year, "").get(timeout, TimeUnit.SECONDS);
                check(!data.items().isEmpty() && data.items().size() <= 500, "Archivio non valido");
                return data;
            });
            for (var operation : operations.entrySet()) {
                measure(operation.getKey() + ".first", 0, operation.getValue(), sql, samples);
                measure(operation.getKey() + ".warmup", 0, operation.getValue(), sql, samples);
                for (int i = 1; i <= settings.iterations(); i++) measure(operation.getKey(), i, operation.getValue(), sql, samples);
            }
        } finally {
            workers.shutdownNow();
            check(workers.awaitTermination(10, TimeUnit.SECONDS), "Worker delle misurazioni non arrestati");
            sql.write(run.resolve("sql.csv"));
            check(sql.openConnections() == 0, "Connessioni aperte al termine della prova");
            try { DriverManager.getConnection(url + ";shutdown=true", "APP", "pw"); }
            catch (SQLException stopped) { if (!"08006".equals(stopped.getSQLState())) throw stopped; }
        }
    }

    private static void measure(String name, int iteration, Callable<?> operation, SqlMeasurements sql, Samples samples) throws Exception {
        sql.scenario(name);
        long started = System.nanoTime();
        check(operation.call() != null, "Risultato assente: " + name);
        samples.record(name, iteration, System.nanoTime() - started);
        check(sql.openConnections() == 0, "Connessione non rilasciata: " + name);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

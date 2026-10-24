package com.orodent.ecos.performance;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.service.VenditeCsvImportService;

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Fresh JVM and actual MainApp; the launcher and native EXE are outside these timings. */
public final class UiScenario {
    public static void main(String[] arguments) throws Exception {
        Path run = Path.of(arguments[0]).toAbsolutePath();
        PerformanceSettings settings = PerformanceSettings.load(run);
        configure(run);
        Properties notes = new Properties();
        ArrayList<WeakReference<javafx.scene.Parent>> discarded = new ArrayList<>();
        try (Samples samples = new Samples(run.resolve("ui-samples.csv"))) {
            FxSession ui = new FxSession(settings.timeoutSeconds());
            long started = System.nanoTime();
            try {
                ui.start();
                samples.record("ui.startup_to_home", 0, System.nanoTime() - started);
                FxHeartbeat heartbeat = new FxHeartbeat();
                try (heartbeat) {
                    heartbeat.phase("dashboard.first");
                    started = System.nanoTime();
                    ui.button("open-statistiche-button");
                    ui.ready("statistiche-dashboard");
                    samples.record("ui.dashboard.first", 0, System.nanoTime() - started);
                    String[][] routes = {{"customers", "stats-customers-view"}, {"products", "stats-products-view"},
                            {"discounts", "stats-discounts-view"}, {"returns", "stats-returns-view"}, {"archive", "stats-archive-view"}};
                    for (String[] route : routes) {
                        heartbeat.phase(route[0]);
                        started = System.nanoTime();
                        ui.menu("stats-" + route[0]);
                        ui.ready(route[1]);
                        samples.record("ui." + route[0], 0, System.nanoTime() - started);
                    }
                    ui.leaveModule();
                    heartbeat.phase("gc_request");
                    collect(ui);
                    samples.record("ui.home_after_gc_request", 0, 0);
                    for (int i = 1; i <= settings.cycles(); i++) {
                        heartbeat.phase("dashboard.repeat");
                        started = System.nanoTime();
                        ui.button("open-statistiche-button");
                        ui.ready("statistiche-dashboard");
                        samples.record("ui.dashboard.repeat", i, System.nanoTime() - started);
                        started = System.nanoTime();
                        discarded.add(ui.leaveModule());
                        ui.pulse();
                        samples.record("ui.return_home", i, System.nanoTime() - started);
                        heartbeat.phase("gc_request");
                        collect(ui);
                        samples.record("ui.home_after_gc_request", i, 0);
                    }
                    notes.setProperty("discarded.module.roots", Integer.toString(discarded.size()));
                    notes.setProperty("roots.not.collected.after.gc.requests", Long.toString(discarded.stream().filter(reference -> reference.get() != null).count()));
                    heartbeat.phase("leave_while_loading");
                    for (int i = 0; i < 3; i++) {
                        ui.button("open-statistiche-button");
                        ui.leaveModule();
                    }
                    long cpu = workerCpu();
                    Thread.sleep(500);
                    long after = workerCpu();
                    notes.setProperty("worker.cpu.ms.in.500ms.after.early.leave", cpu < 0 || after < 0 ? "unavailable"
                            : Double.toString(Math.max(0, after - cpu) / 1_000_000.0));
                    samples.record("ui.after_early_leave", 0, 0);
                    heartbeat.phase("csv_import");
                    importWhileNavigating(run, settings, ui, samples, notes);
                    heartbeat.phase("idle");
                    ui.pulse();
                } finally {
                    heartbeat.write(run.resolve("ui-heartbeat.csv"));
                }
            } finally {
                started = System.nanoTime();
                ui.close();
                samples.record("ui.shutdown", 0, System.nanoTime() - started);
                long remaining = Thread.getAllStackTraces().keySet().stream()
                        .filter(Thread::isAlive).filter(thread -> thread.getName().startsWith("ecos-worker-")
                                || thread.getName().startsWith("ecos-import-")).count();
                HeadlessScenario.check(remaining == 0, "Worker EcoS ancora attivi dopo la chiusura");
                notes.setProperty("ecos.workers.after.shutdown", Long.toString(remaining));
                try (var output = Files.newOutputStream(run.resolve("ui-notes.properties"))) { notes.store(output, "UI observations, not leak assertions"); }
            }
        }
    }

    private static void configure(Path run) throws Exception {
        HeadlessScenario.check(Files.isRegularFile(PerformanceSettings.database(run).resolve("service.properties")), "Database di prova assente");
        Path home = PerformanceSettings.database(run).getParent();
        Path config = run.resolve("generated-application.properties");
        Files.writeString(config, "updates.enabled=false\n");
        System.setProperty("orodent.ecos.config", config.toString());
        System.setProperty("ton.database.home", home.toString());
        try (ServerSocket tcp = new ServerSocket(0); DatagramSocket udp = new DatagramSocket(0)) {
            System.setProperty("ton.database.port", Integer.toString(tcp.getLocalPort()));
            System.setProperty("ton.discovery.port", Integer.toString(udp.getLocalPort()));
        }
        System.setProperty("ton.discovery.timeout.ms", "1500");
        System.setProperty("derby.stream.error.file", run.resolve("ui-derby.log").toString());
    }

    private static void collect(FxSession ui) throws Exception {
        System.gc();
        Thread.sleep(150);
        ui.pulse();
    }

    private static long workerCpu() {
        var bean = ManagementFactory.getThreadMXBean();
        if (!bean.isThreadCpuTimeSupported() || !bean.isThreadCpuTimeEnabled()) return -1;
        long total = 0;
        for (var thread : bean.getThreadInfo(bean.getAllThreadIds())) {
            if (thread != null && thread.getThreadName().startsWith("ecos-worker-")) total += Math.max(0, bean.getThreadCpuTime(thread.getThreadId()));
        }
        return total;
    }

    private static void importWhileNavigating(Path run, PerformanceSettings settings, FxSession ui,
                                              Samples samples, Properties notes) throws Exception {
        var imports = Executors.newSingleThreadExecutor();
        ConnectionProvider connections = () -> {
            try { return DriverManager.getConnection("jdbc:derby:" + PerformanceSettings.database(run), "APP", "pw"); }
            catch (SQLException failure) { throw new IllegalStateException(failure); }
        };
        try {
            long started = System.nanoTime();
            var imported = new VenditeCsvImportService(connections, imports).importFile(run.resolve("generated-import.csv"));
            boolean overlap = ui.buttonWhile("open-ton-button", () -> !imported.isDone());
            notes.setProperty("import.navigation.overlap.observed", Boolean.toString(overlap));
            ui.ready("ton-view");
            ui.button("ton-home-button");
            samples.record("ui.navigation_during_import", 0, System.nanoTime() - started);
            var result = imported.get(settings.timeoutSeconds(), TimeUnit.SECONDS);
            HeadlessScenario.check(result.imported() && result.insertedRows() == settings.importRows(), "Importazione di prova incompleta: " + result);
            samples.record("ui.csv_import", 0, System.nanoTime() - started);
        } finally {
            imports.shutdownNow();
            HeadlessScenario.check(imports.awaitTermination(10, TimeUnit.SECONDS), "Importazione non arrestata");
        }
    }
}

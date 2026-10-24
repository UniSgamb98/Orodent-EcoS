package com.orodent.ecos.performance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Human-readable summary alongside raw CSV files; failed runs remain explicitly failed. */
final class PerformanceReport {
    record Summary(double minimum, double median, double p95, double maximum) { }

    static Summary summarize(List<Double> measurements) {
        if (measurements.isEmpty()) throw new IllegalArgumentException("Nessuna misura");
        List<Double> sorted = measurements.stream().sorted().toList();
        int middle = sorted.size() / 2;
        double median = sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
        return new Summary(sorted.getFirst(), median, sorted.get((int) Math.ceil(sorted.size() * .95) - 1), sorted.getLast());
    }

    static void write(Path run) throws Exception {
        Properties environment = properties(run.resolve("environment.properties"));
        Properties settings = properties(run.resolve("settings.properties"));
        StringBuilder report = new StringBuilder("# Misurazioni Orodent-EcoS\n\n");
        report.append("Esito: **").append(environment.getProperty("status")).append("**. Dataset sintetico, database dedicato.\n\n");
        report.append("Ambiente: ").append(environment.getProperty("os.name")).append(" ")
                .append(environment.getProperty("os.arch")).append("; Java ").append(environment.getProperty("java.version"))
                .append("; processori disponibili: ").append(environment.getProperty("processors"))
                .append("; heap massimo per JVM di misura: ").append(settings.getProperty("performance.heapMb")).append(" MiB.\n\n");
        report.append("Dataset: ").append(settings.getProperty("performance.sales")).append(" righe di vendita, ")
                .append(settings.getProperty("performance.customers")).append(" clienti, ")
                .append(settings.getProperty("performance.products")).append(" codici prodotto, ")
                .append(settings.getProperty("performance.years")).append(" anni fino al ")
                .append(settings.getProperty("performance.lastYear")).append("; seed ")
                .append(settings.getProperty("performance.seed")).append(".\n\n");
        if (environment.containsKey("failure")) report.append("La prova è incompleta: controllare `headless.log` e `ui.log`. Le misure parziali non costituiscono un confronto completo.\n\n");
        report.append("## Tempi\n\n| Operazione | Campioni | Min ms | Mediana ms | p95 ms | Max ms |\n| --- | ---: | ---: | ---: | ---: | ---: |\n");
        Map<String, List<Double>> timings = new LinkedHashMap<>();
        for (String filename : List.of("headless-samples.csv", "ui-samples.csv")) {
            for (String line : rows(run.resolve(filename))) {
                String[] columns = line.split(",");
                if (columns[0].equals("ui.home_after_gc_request") || columns[0].equals("ui.after_early_leave")) continue;
                timings.computeIfAbsent(columns[0], ignored -> new ArrayList<>()).add(Double.parseDouble(columns[2]));
            }
        }
        appendSummaries(report, timings);
        report.append("\n`first` è la prima chiamata di quel servizio; il database è già stato creato e popolato. Non è una lettura a disco freddo. "
                + "`warmup` è separato dai campioni ripetuti. Il p95 usa il rango superiore: con pochi campioni coincide spesso col massimo.\n\n");
        List<String> uiSamples = rows(run.resolve("ui-samples.csv"));
        if (!uiSamples.isEmpty()) {
            report.append("## Memoria alla Home dopo i cicli\n\n| Ciclo | Heap usato MiB | Thread JVM | Worker EcoS | CPU worker cumulativa ms |\n| --- | ---: | ---: | ---: | ---: |\n");
            for (String row : uiSamples) {
                String[] columns = row.split(",");
                if (columns[0].equals("ui.home_after_gc_request")) report.append(String.format(Locale.ROOT, "| %s | %.2f | %s | %s | %s |%n",
                        columns[1], Long.parseLong(columns[3]) / 1048576.0, columns[4], columns[5], columns[6]));
            }
            report.append("\n## Ritardo dei callback JavaFX\n\n| Fase | Campioni | Min ms | Mediana ms | p95 ms | Max ms |\n| --- | ---: | ---: | ---: | ---: | ---: |\n");
            Map<String, List<Double>> heartbeat = new LinkedHashMap<>();
            for (String row : rows(run.resolve("ui-heartbeat.csv"))) {
                String[] columns = row.split(",");
                heartbeat.computeIfAbsent(columns[0], ignored -> new ArrayList<>()).add(Double.parseDouble(columns[1]));
            }
            appendSummaries(report, heartbeat);
            report.append("\n## Osservazioni di chiusura e importazione\n\n");
            Properties notes = properties(run.resolve("ui-notes.properties"));
            for (String key : notes.stringPropertyNames().stream().sorted().toList()) report.append("- `").append(key).append("`: ").append(notes.getProperty(key)).append("\n");
        } else report.append("La prova grafica non è stata eseguita o non ha completato l'avvio. Abilitarla con `-Pperformance,performance-ui` su un sistema con display.\n");
        report.append("\n## Lettura dei risultati\n\n"
                + "- I tempi dei servizi includono lettura dei risultati e calcoli Java. `sql.csv` misura soltanto `execute*`, esclude fetch e calcoli e aggiunge un piccolo costo di strumentazione.\n"
                + "- Le aperture UI includono caricamento dati, controlli e due pulse JavaFX. Il polling è di 50 ms: non sono microbenchmark. `ui.startup_to_home` parte prima di JavaFX nel processo di prova, esclude bootstrap JVM, EXE e launcher.\n"
                + "- Le JVM di misura usano il classpath dei test. Il driver grafico controlla errori e CSS durante il polling; questo aggiunge lavoro e non riproduce la risoluzione JPMS del pacchetto distribuito.\n"
                + "- La memoria riportata è heap JVM, non memoria totale/RSS. Il GC è richiesto e può essere ignorato; riferimenti deboli non raccolti e variazioni di heap sono indizi, non prove definitive di leak.\n"
                + "- I worker condivisi possono rimanere vivi quando si torna alla Home. La CPU nei 500 ms dopo un'uscita anticipata osserva attività ancora in corso, senza attribuirne automaticamente la causa. Dopo la chiusura dell'app si richiedono zero worker EcoS.\n"
                + "- La prova CSV usa il servizio reale su un executor dedicato di misura, mentre si naviga in TON. Non automatizza il file chooser. Se manca sovrapposizione osservata, non dimostra reattività sotto importazione.\n"
                + "- Confrontare più esecuzioni con stessi dati, JDK, heap e macchina. I dati uniformi generati non sostituiscono un archivio reale e non descrivono tutti i carichi dell'ufficio. Nessuna soglia temporale universale determina l'esito PASSED.\n\n"
                + "File: `settings.properties`, `environment.properties`, `dataset.properties`, `headless-samples.csv`, `sql.csv`; con UI anche `ui-samples.csv`, `ui-heartbeat.csv` e `ui-notes.properties`.\n");
        Files.writeString(run.resolve("report.md"), report);
    }

    private static void appendSummaries(StringBuilder report, Map<String, List<Double>> measurements) {
        measurements.forEach((name, values) -> {
            Summary summary = summarize(values);
            report.append(String.format(Locale.ROOT, "| %s | %d | %.3f | %.3f | %.3f | %.3f |%n", name, values.size(),
                    summary.minimum(), summary.median(), summary.p95(), summary.maximum()));
        });
    }

    private static List<String> rows(Path path) throws IOException {
        return Files.exists(path) ? Files.readAllLines(path).stream().skip(1).filter(line -> !line.isBlank()).toList() : List.of();
    }

    private static Properties properties(Path path) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(path)) try (var input = Files.newInputStream(path)) { properties.load(input); }
        return properties;
    }
}

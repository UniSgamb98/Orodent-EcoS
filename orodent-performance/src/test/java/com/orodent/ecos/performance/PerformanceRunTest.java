package com.orodent.ecos.performance;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Runs only in the opt-in reactor module, never in ordinary builds or releases. */
class PerformanceRunTest {
    @Test
    void measureGeneratedWorkloadInSeparateJvms() throws Exception {
        Properties settings = new Properties();
        System.getProperties().stringPropertyNames().stream().filter(name -> name.startsWith("performance."))
                .forEach(name -> settings.setProperty(name, System.getProperty(name)));
        PerformanceSettings configuration = PerformanceSettings.read(settings);
        Path output = Files.createDirectories(Path.of(settings.getProperty("performance.outputDirectory")).toAbsolutePath());
        String timestamp = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path run = Files.createTempDirectory(output, "run-" + timestamp + "-");
        try (var stream = Files.newOutputStream(run.resolve("settings.properties"))) { settings.store(stream, "Performance configuration"); }
        Properties environment = new Properties();
        for (String name : List.of("java.version", "java.vm.name", "os.name", "os.version", "os.arch")) {
            environment.setProperty(name, System.getProperty(name));
        }
        environment.setProperty("processors", Integer.toString(Runtime.getRuntime().availableProcessors()));
        environment.setProperty("started.utc", Instant.now().toString());
        environment.setProperty("child.heap.mb", Integer.toString(configuration.heapMb()));
        environment.setProperty("status", "FAILED");
        Exception failure = null;
        try {
            runChild(HeadlessScenario.class, run, configuration);
            if (configuration.ui()) runChild(UiScenario.class, run, configuration);
            environment.setProperty("status", "PASSED");
        } catch (Exception exception) {
            failure = exception;
            environment.setProperty("failure", exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage());
        } finally {
            try (var stream = Files.newOutputStream(run.resolve("environment.properties"))) { environment.store(stream, "Measurement environment"); }
            try { PerformanceReport.write(run); }
            catch (Exception reportFailure) {
                if (failure != null) failure.addSuppressed(reportFailure);
                else failure = reportFailure;
            }
            System.out.println("Report prestazioni: " + run.resolve("report.md"));
        }
        if (failure != null) throw failure;
    }

    private static void runChild(Class<?> main, Path run, PerformanceSettings settings) throws Exception {
        String executable = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
        String raw = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        String classpath = Arrays.stream(raw.split(Pattern.quote(File.pathSeparator)))
                .map(path -> Path.of(path).toAbsolutePath().toString()).collect(Collectors.joining(File.pathSeparator));
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xms64m", "-Xmx" + settings.heapMb() + "m", "-XX:+ExitOnOutOfMemoryError",
                "-cp", classpath, main.getName(), run.toString()));
        Path log = run.resolve(main == HeadlessScenario.class ? "headless.log" : "ui.log");
        Process process = new ProcessBuilder(command).directory(run.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if (!process.waitFor(Math.max(120, settings.timeoutSeconds() * 4L), TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout della prova " + main.getSimpleName() + "; log: " + log);
            }
            if (process.exitValue() != 0) {
                List<String> lines = Files.readAllLines(log);
                throw new IllegalStateException("Prova " + main.getSimpleName() + " fallita:\n"
                        + String.join("\n", lines.subList(Math.max(0, lines.size() - 50), lines.size())));
            }
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }
}

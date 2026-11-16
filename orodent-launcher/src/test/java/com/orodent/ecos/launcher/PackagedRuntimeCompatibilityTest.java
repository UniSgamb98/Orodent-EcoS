package com.orodent.ecos.launcher;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Checks the portable JDK module set against the complete application distribution. */
class PackagedRuntimeCompatibilityTest {
    @TempDir Path temporary;

    @Test
    void portableRuntimeResolvesTheCompleteApplicationIncludingModuleProviders() throws Exception {
        String roots =
                Files.readAllLines(
                                Path.of("../packaging/java-runtime-modules.txt"),
                                StandardCharsets.UTF_8)
                        .stream()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(Collectors.joining(","));
        assertFalse(roots.isEmpty());
        Path runtime = temporary.resolve("runtime");
        Path linkerLog = temporary.resolve("jlink.log");
        try (var log =
                new PrintWriter(Files.newBufferedWriter(linkerLog, StandardCharsets.UTF_8))) {
            assertEquals(
                    0,
                    java.util.spi.ToolProvider.findFirst("jlink")
                            .orElseThrow()
                            .run(
                                    log,
                                    log,
                                    "--add-modules",
                                    roots,
                                    "--output",
                                    runtime.toString(),
                                    "--strip-debug",
                                    "--no-header-files",
                                    "--no-man-pages"),
                    () -> read(linkerLog));
        }
        String executable =
                System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                        ? "java.exe"
                        : "java";
        Path validationLog = temporary.resolve("module-validation.log");
        Process process =
                new ProcessBuilder(
                                runtime.resolve("bin").resolve(executable).toString(),
                                "-Dstdout.encoding=UTF-8",
                                "-Dstderr.encoding=UTF-8",
                                "--dry-run",
                                "--module-path",
                                Path.of("target/distribution/app/lib").toAbsolutePath().toString(),
                                "--module",
                                "com.orodent.ecos.app/com.orodent.ecos.app.MainApp")
                        .directory(temporary.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(validationLog.toFile())
                        .start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), () -> read(validationLog));
            assertEquals(0, process.exitValue(), () -> read(validationLog));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }

    private static String read(Path log) {
        try {
            return Files.readString(log, StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            return failure.toString();
        }
    }
}

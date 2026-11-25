package com.orodent.ecos.ton.features.firingprograms.view;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

@Tag("javafx")
class FiringProgramsUiTest {
    @Test
    void editsAnOrderedThermalCycleAndPreservesApprovedVersions() throws Exception {
        String cp =
                System.getProperty(
                                "surefire.test.class.path", System.getProperty("java.class.path"))
                        + java.io.File.pathSeparator
                        + System.getProperty("jdk.module.path", "");
        Path log = Path.of("target/firing-programs-ui-ui.log");
        Process process =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                "-Dderby.stream.error.file=target/firing-programs-ui-derby.log",
                                "-cp",
                                cp,
                                FiringProgramsUiProbe.class.getName())
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
        try {
            assertTrue(
                    process.waitFor(150, TimeUnit.SECONDS),
                    "Firing programs UI timed out; see " + log);
            assertEquals(
                    0,
                    process.exitValue(),
                    () -> {
                        try {
                            return new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            return e.toString();
                        }
                    });
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            }
        }
    }
}

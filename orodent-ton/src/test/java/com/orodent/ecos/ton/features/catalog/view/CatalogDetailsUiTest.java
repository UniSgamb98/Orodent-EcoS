package com.orodent.ecos.ton.features.catalog.view;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

@Tag("javafx")
class CatalogDetailsUiTest {
    @Test
    void completeListsAndDoubleClicksKeepDetailsReachable() throws Exception {
        String cp =
                System.getProperty(
                                "surefire.test.class.path", System.getProperty("java.class.path"))
                        + java.io.File.pathSeparator
                        + System.getProperty("jdk.module.path", "");
        Path log = Path.of("target/catalog-details-ui.log");
        Process process =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                "-Dderby.stream.error.file=target/catalog-details-derby.log",
                                "-cp",
                                cp,
                                CatalogDetailsUiProbe.class.getName())
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
        try {
            assertTrue(
                    process.waitFor(150, TimeUnit.SECONDS),
                    "Catalogue details UI timed out; see " + log);
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

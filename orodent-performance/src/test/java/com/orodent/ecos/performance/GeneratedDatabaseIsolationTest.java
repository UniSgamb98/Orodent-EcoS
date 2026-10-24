package com.orodent.ecos.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedDatabaseIsolationTest {
    @TempDir Path temporary;

    @Test
    void refusesAnExistingDatabaseBeforeBootingDerbyOrWritingData() throws Exception {
        Properties settings = new Properties();
        System.getProperties().stringPropertyNames().stream().filter(name -> name.startsWith("performance."))
                .forEach(name -> settings.setProperty(name, System.getProperty(name)));
        try (var output = Files.newOutputStream(temporary.resolve("settings.properties"))) { settings.store(output, "test"); }
        Path database = Files.createDirectories(PerformanceSettings.database(temporary));
        Path sentinel = database.resolve("keep.txt");
        Files.writeString(sentinel, "Dati preesistenti da conservare");
        assertThrows(IllegalStateException.class, () -> HeadlessScenario.main(new String[]{temporary.toString()}));
        assertEquals("Dati preesistenti da conservare", Files.readString(sentinel));
        try (var files = Files.list(database)) { assertEquals(1, files.count()); }
        assertFalse(Files.exists(temporary.resolve("headless-samples.csv")));
        assertFalse(Files.exists(temporary.resolve("dataset.properties")));
    }
}

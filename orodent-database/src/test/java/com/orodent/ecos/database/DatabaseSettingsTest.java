package com.orodent.ecos.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseSettingsTest {
    @TempDir Path temporary;

    private Path settings() throws Exception {
        Path file = temporary.resolve("config/application.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "database.home=../données\ndatabase.port=1528\ndiscovery.port=45680\n");
        return file;
    }

    @Test
    void readsUtf8AndResolvesFilePathsRelativeToTheConfigurationFile() throws Exception {
        var config = DatabaseSettings.load(settings(), true, new Properties(), null, temporary);
        assertEquals(temporary.resolve("données"), config.home());
        assertEquals(1528, config.databasePort());
        assertEquals(45680, config.discoveryPort());
    }

    @Test
    void environmentOverridesFileButJvmPropertiesOverrideEnvironment() throws Exception {
        Path file = settings();
        Path environment = temporary.resolve("environment");
        assertEquals(environment, DatabaseSettings.load(file, true, new Properties(),
                environment.toString(), temporary).home());
        Properties overrides = new Properties();
        overrides.setProperty("ton.database.home", temporary.resolve("jvm").toString());
        overrides.setProperty("ton.database.port", "1530");
        assertEquals(temporary.resolve("jvm"), DatabaseSettings.load(file, true, overrides,
                environment.toString(), temporary).home());
        assertEquals(1530, DatabaseSettings.load(file, true, overrides, null, temporary).databasePort());
    }

    @Test
    void absentOptionalFileUsesDevelopmentDefaultsOnSeparatePorts() {
        var config = DatabaseSettings.load(temporary.resolve("missing.properties"), false,
                new Properties(), null, temporary);
        assertEquals(temporary, config.home());
        assertEquals(1529, config.databasePort());
        assertEquals(45681, config.discoveryPort());
    }

    @Test
    void explicitlyMissingFileIsAnErrorRatherThanSilentFallback() {
        assertThrows(IllegalStateException.class, () -> DatabaseSettings.load(
                temporary.resolve("missing.properties"), true, new Properties(), null, temporary));
    }

    @Test
    void rejectsInvalidPorts() throws Exception {
        Path file = settings();
        Properties overrides = new Properties();
        overrides.setProperty("ton.discovery.port", "invalid");
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseSettings.load(file, true, overrides, null, temporary));
        overrides.setProperty("ton.discovery.port", "0");
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseSettings.load(file, true, overrides, null, temporary));
    }

    @Test
    void checkedInDevelopmentAndDistributionProfilesUseDifferentPorts() {
        // Surefire runs from orodent-database; inspect the real profiles shipped by the reactor.
        Path root = Path.of("..");
        var development = DatabaseSettings.load(root.resolve("config/development.properties"), true,
                new Properties(), temporary.resolve("dev").toString(), temporary);
        var production = DatabaseSettings.load(root.resolve(
                "orodent-launcher/src/distribution/config/application.properties"), true,
                new Properties(), temporary.resolve("prod").toString(), temporary);
        assertNotEquals(development.home(), production.home());
        assertNotEquals(development.databasePort(), production.databasePort());
        assertNotEquals(development.discoveryPort(), production.discoveryPort());
    }
}

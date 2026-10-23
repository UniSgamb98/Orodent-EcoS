package com.orodent.ecos.database;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** External settings; explicit JVM/environment overrides retain their precedence. */
public final class DatabaseSettings {
    private DatabaseSettings() { }

    public static DatabaseConfiguration load() {
        String configuredFile = System.getProperty("orodent.ecos.config");
        boolean explicit = configuredFile != null && !configuredFile.isBlank();
        Path file = explicit ? Path.of(configuredFile) : Path.of("config/development.properties");
        return load(file, explicit, System.getProperties(), System.getenv("ORODENT_ECOS_DATABASE_HOME"),
                Path.of(System.getProperty("user.home"), ".orodent-ecos", "development"));
    }

    static DatabaseConfiguration load(Path file, boolean required, Properties overrides,
                                      String environmentHome, Path defaultHome) {
        Path absoluteFile = file.toAbsolutePath().normalize();
        Properties settings = new Properties();
        if (required || Files.exists(absoluteFile)) {
            try (Reader reader = Files.newBufferedReader(absoluteFile, StandardCharsets.UTF_8)) {
                settings.load(reader);
            } catch (IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("Impossibile leggere la configurazione: " + absoluteFile, failure);
            }
        }
        String home = nonBlank(overrides.getProperty("ton.database.home"));
        if (home == null) home = nonBlank(environmentHome);
        Path databaseHome;
        if (home != null) {
            databaseHome = Path.of(home);
        } else {
            String fileHome = nonBlank(settings.getProperty("database.home"));
            databaseHome = fileHome == null ? defaultHome : absoluteFile.getParent().resolve(fileHome);
        }
        // With no external file, direct IDE starts still use isolated development defaults.
        return new DatabaseConfiguration(databaseHome,
                number(overrides, settings, "ton.database.port", "database.port", 1529),
                number(overrides, settings, "ton.discovery.port", "discovery.port", 45681),
                number(overrides, settings, "ton.discovery.timeout.ms", "discovery.timeout.ms", 1500));
    }

    private static int number(Properties overrides, Properties settings, String property, String key, int fallback) {
        String value = nonBlank(overrides.getProperty(property));
        if (value == null) value = nonBlank(settings.getProperty(key));
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Valore numerico non valido per " + key + ": " + value, failure);
        }
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

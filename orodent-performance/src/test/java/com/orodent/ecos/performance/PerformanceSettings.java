package com.orodent.ecos.performance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

record PerformanceSettings(int sales, int customers, int products, int years, int lastYear,
                           int seed, int iterations, int cycles, int importRows, int heapMb,
                           int timeoutSeconds, boolean ui) {
    static PerformanceSettings read(Properties properties) {
        PerformanceSettings settings = new PerformanceSettings(
                number(properties, "sales", 100, 2_000_000),
                number(properties, "customers", 1, 100_000),
                number(properties, "products", 1, 10_000),
                number(properties, "years", 1, 10),
                number(properties, "lastYear", 2000, 2090),
                number(properties, "seed", 0, Integer.MAX_VALUE),
                number(properties, "iterations", 2, 50),
                number(properties, "cycles", 2, 30),
                number(properties, "importRows", 1, 200_000),
                number(properties, "heapMb", 256, 8192),
                number(properties, "timeoutSeconds", 10, 900),
                Boolean.parseBoolean(properties.getProperty("performance.ui", "false")));
        if (settings.customers > settings.sales) throw new IllegalArgumentException("Clienti maggiori delle vendite di prova.");
        return settings;
    }

    private static int number(Properties properties, String key, int minimum, int maximum) {
        String name = "performance." + key;
        int value = Integer.parseInt(properties.getProperty(name));
        if (value < minimum || value > maximum) throw new IllegalArgumentException(name + " fuori intervallo: " + minimum + ".." + maximum);
        return value;
    }

    static PerformanceSettings load(Path run) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(run.resolve("settings.properties"))) { properties.load(input); }
        return read(properties);
    }

    static Path database(Path run) { return run.resolve("generated-database/OrodentEcoS"); }
}

package com.orodent.ecos.database;

import java.nio.file.Path;
import java.util.Objects;

public record DatabaseConfiguration(Path home, int databasePort, int discoveryPort, int discoveryTimeoutMs) {
    public static final String DATABASE_NAME = "OrodentEcoS";
    public static final String USER = "APP";
    public static final String PASSWORD = "pw";

    public DatabaseConfiguration {
        Objects.requireNonNull(home, "home");
        if (databasePort < 1 || databasePort > 65535 || discoveryPort < 1 || discoveryPort > 65535)
            throw new IllegalArgumentException("Porta database o discovery non valida");
        if (discoveryTimeoutMs < 1 || discoveryTimeoutMs > 60_000)
            throw new IllegalArgumentException("Timeout discovery non valido");
        home = home.toAbsolutePath().normalize();
    }

    public static DatabaseConfiguration forHome(Path home) {
        return new DatabaseConfiguration(home,
                Integer.parseInt(System.getProperty("ton.database.port", "1528")),
                Integer.parseInt(System.getProperty("ton.discovery.port", "45680")),
                Integer.parseInt(System.getProperty("ton.discovery.timeout.ms", "1500")));
    }
}

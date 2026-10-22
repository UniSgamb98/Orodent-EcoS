package com.orodent.tonv3.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseTest {

    @Test
    void usesEnvironmentVariableWhenSystemPropertyIsMissing() {
        Path home = Database.resolveDatabaseHome(
                null,
                "/var/lib/statistiche",
                "Linux",
                "/home/user"
        );

        assertEquals(Path.of("/var/lib/statistiche"), home);
    }

    @Test
    void systemPropertyOverridesEnvironmentVariable() {
        Path home = Database.resolveDatabaseHome(
                "/opt/statistiche",
                "/var/lib/statistiche",
                "Linux",
                "/home/user"
        );

        assertEquals(Path.of("/opt/statistiche"), home);
    }

    @Test
    void ignoresBlankConfigurationValues() {
        Path home = Database.resolveDatabaseHome(
                " ",
                " ",
                "Linux",
                "/home/user"
        );

        assertEquals(Path.of("/home/user", ".ton-v3", "database"), home);
    }
}

package com.orodent.ecos.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HexFormat;

/** An immutable, ordered migration. SQL resources must not change after publication. */
record SchemaMigration(int version, String description, String resource) {
    String script() {
        try (var input = SchemaMigration.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Risorsa non trovata: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Impossibile leggere la migrazione " + version, failure);
        }
    }

    String checksum() {
        String normalized = script().replace("\r\n", "\n").replace('\r', '\n');
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    void apply(Connection connection, boolean baseline) throws SQLException {
        for (String sql : script().split(";")) {
            if (sql.isBlank()) continue;
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(sql.trim());
            } catch (SQLException failure) {
                // Only V1 may adopt tables created before migration tracking existed.
                if (!baseline || !"X0Y32".equals(failure.getSQLState())) throw failure;
            }
        }
    }
}

package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Objects;

/** Crea in modo idempotente le strutture descritte in {@code database/schema.sql}. */
public final class DatabaseSchema {
    private static final String SCHEMA_RESOURCE = "/database/schema.sql";
    private static final String OBJECT_ALREADY_EXISTS = "X0Y32";

    private final ConnectionProvider connectionProvider;

    public DatabaseSchema(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    public void initialize() {
        String schema = loadSchema();
        connectionProvider.withConnection(connection -> {
            for (String sql : Arrays.stream(schema.split(";"))
                    .map(String::trim)
                    .filter(statement -> !statement.isEmpty())
                    .toList()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(sql);
                } catch (SQLException exception) {
                    if (!OBJECT_ALREADY_EXISTS.equals(exception.getSQLState())) {
                        throw exception;
                    }
                }
            }
            return null;
        });
    }

    private String loadSchema() {
        try (InputStream input = DatabaseSchema.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Risorsa non trovata: " + SCHEMA_RESOURCE);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Impossibile leggere lo schema database", exception);
        }
    }
}

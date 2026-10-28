package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;

/** Recreates the published domain schema without the current migration runner. */
final class SchemaV4Fixture {
    private SchemaV4Fixture() { }

    static void create(ConnectionProvider connections) {
        SchemaV3Fixture.create(connections);
        connections.withTransaction(connection -> {
            var domain = new SchemaMigration(4,
                    "TON versioned specifications, production, firing and documents",
                    "/database/migrations/V4__ton_domain.sql");
            domain.apply(connection, false);
            try (var statement = connection.prepareStatement(
                    "INSERT INTO ECOS.ECOS_SCHEMA_HISTORY(version,description,checksum) VALUES (4,?,?)")) {
                statement.setString(1, domain.description());
                statement.setString(2, domain.checksum());
                statement.executeUpdate();
            }
            return null;
        });
    }
}

package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;

/** Recreates the published catalogue format without the current migration runner. */
final class SchemaV3Fixture {
    private SchemaV3Fixture() { }

    static void create(ConnectionProvider connections) {
        SchemaV2Fixture.create(connections);
        connections.withTransaction(connection -> {
            var products = new SchemaMigration(3, "TON technical product catalogue",
                    "/database/migrations/V3__ton_products.sql");
            products.apply(connection, false);
            try (var statement = connection.prepareStatement(
                    "INSERT INTO ECOS.ECOS_SCHEMA_HISTORY(version,description,checksum) VALUES (3,?,?)")) {
                statement.setString(1, products.description());
                statement.setString(2, products.checksum());
                statement.executeUpdate();
            }
            return null;
        });
    }
}

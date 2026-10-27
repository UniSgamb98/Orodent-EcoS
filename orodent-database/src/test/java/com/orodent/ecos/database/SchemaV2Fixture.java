package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;

/** Builds the published V2 format without running the current application's migration runner. */
final class SchemaV2Fixture {
    private SchemaV2Fixture() { }

    static void create(ConnectionProvider connections) {
        connections.withTransaction(connection -> {
            var baseline = new SchemaMigration(1, "Shared customers and sales baseline", "/database/schema.sql");
            baseline.apply(connection, false);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE APP.ECOS_SCHEMA_HISTORY(version INTEGER PRIMARY KEY, description VARCHAR(200) NOT NULL, "
                        + "checksum CHAR(64) NOT NULL, applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            }
            try (var statement = connection.prepareStatement("INSERT INTO APP.ECOS_SCHEMA_HISTORY(version,description,checksum) VALUES (1,?,?)")) {
                statement.setString(1, baseline.description()); statement.setString(2, baseline.checksum()); statement.executeUpdate();
            }
            var domains = new SchemaMigration(2, "Separate ecosystem, master data and sales schemas", "/database/migrations/V2__domain_schemas.sql");
            domains.apply(connection, false);
            DomainSchemaMigration.copyData(connection);
            try (var statement = connection.prepareStatement("INSERT INTO ECOS.ECOS_SCHEMA_HISTORY(version,description,checksum) VALUES (2,?,?)")) {
                statement.setString(1, domains.description()); statement.setString(2, domains.checksum()); statement.executeUpdate();
            }
            DomainSchemaMigration.removeLegacyTables(connection);
            return null;
        });
    }
}

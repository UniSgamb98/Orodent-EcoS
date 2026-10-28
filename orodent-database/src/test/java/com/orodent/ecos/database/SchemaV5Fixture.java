package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;

final class SchemaV5Fixture {
    private SchemaV5Fixture() {}

    static void create(ConnectionProvider connections) {
        SchemaV4Fixture.create(connections);
        connections.withTransaction(
                c -> {
                    var migration =
                            new SchemaMigration(
                                    5,
                                    "TON powder colors",
                                    "/database/migrations/V5__ton_powder_colors.sql");
                    migration.apply(c, false);
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " ECOS.ECOS_SCHEMA_HISTORY(version,description,checksum)"
                                        + " VALUES (5,?,?)")) {
                        q.setString(1, migration.description());
                        q.setString(2, migration.checksum());
                        q.executeUpdate();
                    }
                    return null;
                });
    }
}

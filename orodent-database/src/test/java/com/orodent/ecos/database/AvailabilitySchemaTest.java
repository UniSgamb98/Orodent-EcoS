package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

class AvailabilitySchemaTest {
    @Test
    void upgradeCreatesEmptyIndependentSourcesAndIsIdempotent() throws Exception {
        String url =
                "jdbc:derby:memory:availability_schema_"
                        + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        try {
            new DatabaseSchema(db).initialize();
            db.withTransaction(
                    c -> {
                        AvailabilityFixtures.beforeV23(c);
                        return null;
                    });
            new DatabaseSchema(db).initialize();
            new DatabaseSchema(db).initialize();
            db.withConnection(
                    c -> {
                        DatabaseSchema.verifyCompatible(c);
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT COUNT(*),COUNT(import_id) FROM"
                                                    + " TON.DISPONIBILITA_FONTI")) {
                            r.next();
                            assertEquals(2, r.getInt(1));
                            assertEquals(0, r.getInt(2));
                        }
                        return null;
                    });
        } finally {
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException e) {
                assertEquals("08006", e.getSQLState());
            }
        }
    }
}

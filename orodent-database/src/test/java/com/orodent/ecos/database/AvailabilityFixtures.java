package com.orodent.ecos.database;

import java.sql.*;

final class AvailabilityFixtures {
    static void beforeV23(Connection c) throws SQLException {
        try (var q = c.createStatement()) {
            q.executeUpdate("DROP TABLE TON.DISPONIBILITA_FONTI");
            q.executeUpdate("DROP TABLE TON.DISPONIBILITA_VALORI");
            q.executeUpdate("DROP TABLE TON.DISPONIBILITA_IMPORT");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=23");
        }
    }
}

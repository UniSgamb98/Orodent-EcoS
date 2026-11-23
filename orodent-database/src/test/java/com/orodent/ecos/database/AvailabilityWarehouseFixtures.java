package com.orodent.ecos.database;

import java.sql.*;

final class AvailabilityWarehouseFixtures {
    static void beforeV25(Connection c) throws SQLException {
        CubatureRuleFixtures.beforeV26(c);
        try (var q = c.createStatement()) {
            q.executeUpdate(
                    "ALTER TABLE TON.DISPONIBILITA_VALORI DROP CONSTRAINT fk_ton_disp_magazzino");
            q.executeUpdate(
                    "ALTER TABLE TON.DISPONIBILITA_VALORI DROP CONSTRAINT pk_ton_disp_valori");
            q.executeUpdate("ALTER TABLE TON.DISPONIBILITA_VALORI DROP COLUMN magazzino RESTRICT");
            q.executeUpdate(
                    "ALTER TABLE TON.DISPONIBILITA_VALORI ADD PRIMARY KEY(import_id,codice)");
            q.executeUpdate(
                    "ALTER TABLE TON.DISPONIBILITA_IMPORT DROP COLUMN magazzini_distinti RESTRICT");
            q.executeUpdate("DROP TABLE TON.MAGAZZINI");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=25");
        }
    }
}

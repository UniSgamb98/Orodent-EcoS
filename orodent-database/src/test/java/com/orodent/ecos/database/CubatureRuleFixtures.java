package com.orodent.ecos.database;

import java.sql.*;

final class CubatureRuleFixtures {
    static void beforeV26(Connection c) throws SQLException {
        try (var q = c.createStatement()) {
            q.executeUpdate(
                    "ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO DROP CONSTRAINT"
                        + " fk_ton_cfg_regola_cubatura");
            q.executeUpdate("DROP TABLE TON.REGOLE_CUBATURA");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=26");
        }
    }
}

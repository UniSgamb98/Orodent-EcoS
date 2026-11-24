package com.orodent.ecos.database;

import java.sql.*;

final class CubatureVersionFixtures {
    static void beforeV27(Connection c) throws SQLException {
        try (var q = c.createStatement()) {
            q.executeUpdate("DROP TABLE TON.MARCATURA_VERSIONI");
            q.executeUpdate("DROP TABLE TON.MODELLI_MARCATURA");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=28");
            q.executeUpdate(
                    "ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO DROP CONSTRAINT"
                        + " fk_ton_cfg_calcolo_versione");
            q.executeUpdate(
                    "ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO DROP COLUMN calcolo_versione_id"
                        + " RESTRICT");
            q.executeUpdate("DROP TABLE TON.REGOLA_CUBATURA_VERSIONI");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=27");
        }
    }
}

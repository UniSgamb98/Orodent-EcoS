package com.orodent.ecos.database;

import java.sql.*;

final class CubatureVersionFixtures {
    static void beforeV27(Connection c) throws SQLException {
        FiringProgramFixtures.beforeV31(c);
        try (var q = c.createStatement()) {
            q.executeUpdate(
                    "ALTER TABLE TON.PIANI_PRODUZIONE DROP COLUMN dischi_confermati_il RESTRICT");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=30");
            q.executeUpdate("DROP TABLE TON.CARICO_DISCHI");
            q.executeUpdate("DROP TABLE TON.DISCHI_PRODOTTI");
            q.executeUpdate("DROP TABLE TON.ESECUZIONI_PIANO");
            for (String column :
                    new String[] {"caricato_il", "forno_codice", "forno_nome", "forno_revisione"})
                q.executeUpdate(
                        "ALTER TABLE TON.CICLI_COTTURA DROP COLUMN " + column + " RESTRICT");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=29");
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

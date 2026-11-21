package com.orodent.ecos.database;

import java.sql.*;

final class ProductSpecificationFixtures {
    static void beforeV21(Connection c) throws SQLException {
        SupplierFixtures.beforeV22(c);
        try (var q = c.createStatement()) {
            q.executeUpdate("DROP TABLE TON.PIANO_PRODOTTO_RIGHE");
            q.executeUpdate(
                    "ALTER TABLE TON.PRODOTTI_REVISIONI DROP CONSTRAINT fk_ton_pro_hist_default");
            q.executeUpdate("ALTER TABLE TON.PRODOTTI DROP CONSTRAINT fk_ton_pro_cfg_default");
            for (String t : new String[] {"PRODOTTI_REVISIONI", "PRODOTTI"}) {
                q.executeUpdate(
                        "ALTER TABLE TON." + t + " DROP COLUMN CONFIGURAZIONE_PREDEFINITA_ID");
                q.executeUpdate("ALTER TABLE TON." + t + " DROP COLUMN SPECIFICHE_DA_VERIFICARE");
            }
            q.executeUpdate("DROP TABLE TON.CONFIGURAZIONI_MIGRATE");
            q.executeUpdate("DROP TABLE TON.CONFIGURAZIONI_PRODOTTO");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE VERSION=21");
        }
    }
}

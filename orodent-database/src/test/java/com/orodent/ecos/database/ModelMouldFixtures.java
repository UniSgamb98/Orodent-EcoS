package com.orodent.ecos.database;

import java.sql.*;

/** Reconstruct an actual pre-V19 catalogue when testing older migration paths. */
final class ModelMouldFixtures {
    static void beforeV19(Connection c) throws SQLException {
        ModelDiameterFixtures.beforeV20(c);
        try (var q = c.createStatement()) {
            q.executeUpdate("DROP TABLE TON.MODELLO_STAMPI_AMMESSI");
            q.executeUpdate("DROP TABLE TON.MODELLO_STAMPO_RIF");
            q.executeUpdate(
                    "ALTER TABLE TON.MODELLI_DISCO ADD COLUMN diametro_old_mm DECIMAL(6,2)");
            q.executeUpdate("UPDATE TON.MODELLI_DISCO SET diametro_old_mm=diametro_pressatura_mm");
            q.executeUpdate("ALTER TABLE TON.MODELLI_DISCO ALTER COLUMN diametro_old_mm NOT NULL");
            q.executeUpdate("ALTER TABLE TON.MODELLI_DISCO DROP CONSTRAINT ck_ton_md_misure");
            q.executeUpdate(
                    "ALTER TABLE TON.MODELLI_DISCO DROP COLUMN diametro_pressatura_mm RESTRICT");
            q.executeUpdate(
                    "RENAME COLUMN TON.MODELLI_DISCO.diametro_old_mm TO diametro_pressatura_mm");
            q.executeUpdate(
                    "ALTER TABLE TON.MODELLI_DISCO ADD CONSTRAINT ck_ton_md_misure CHECK"
                        + " (diametro_pressatura_mm>0 AND pressione_kg_cm2>0 AND grammi_per_mm>0"
                        + " AND sovramateriale_superiore_mm>=0 AND"
                        + " sovramateriale_inferiore_mm>=0)");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE VERSION=19");
        }
    }
}

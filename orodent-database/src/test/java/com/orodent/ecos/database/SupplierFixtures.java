package com.orodent.ecos.database;

import java.sql.*;

final class SupplierFixtures {
    static void beforeV22(Connection c) throws SQLException {
        AvailabilityFixtures.beforeV23(c);
        try (var q = c.createStatement()) {
            q.executeUpdate("ALTER TABLE TON.POLVERI DROP CONSTRAINT fk_ton_polveri_fornitore");
            q.executeUpdate(
                    "ALTER TABLE COSTI.ORDINI_POLVERI DROP CONSTRAINT fk_costi_ordini_fornitore");
            q.executeUpdate("ALTER TABLE TON.POLVERI DROP COLUMN fornitore_id");
            q.executeUpdate("ALTER TABLE COSTI.ORDINI_POLVERI DROP COLUMN fornitore_id");
            q.executeUpdate("DROP TABLE ANAGRAFICHE.FORNITORI_ALIAS");
            q.executeUpdate("DROP TABLE ANAGRAFICHE.FORNITORI");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=22");
        }
    }
}

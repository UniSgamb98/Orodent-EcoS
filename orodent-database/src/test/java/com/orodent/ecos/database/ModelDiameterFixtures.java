package com.orodent.ecos.database;

import java.sql.*;

final class ModelDiameterFixtures {
    static void beforeV20(Connection c) throws SQLException {
        ProductSpecificationFixtures.beforeV21(c);
        try (var q = c.createStatement()) {
            q.executeUpdate("DROP TABLE TON.MODELLO_DIAM_STAMPI");
            q.executeUpdate("DROP TABLE TON.MODELLO_DIAMETRI");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE VERSION=20");
        }
    }
}

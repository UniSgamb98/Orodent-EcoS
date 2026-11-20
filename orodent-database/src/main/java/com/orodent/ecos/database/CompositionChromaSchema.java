package com.orodent.ecos.database;

import java.sql.*;

final class CompositionChromaSchema {
    static void verify(Connection c) throws SQLException {
        for (String[] column :
                new String[][] {
                    {"COMPOSIZIONE_CROMA", "PPM_OBIETTIVO"}, {"COMPOSIZIONE_CROMA_POLVERI", "QUOTA"}
                }) {
            try (var r = c.getMetaData().getColumns(null, "TON", column[0], column[1])) {
                if (!r.next() || r.getInt("DATA_TYPE") != Types.DECIMAL)
                    throw new SQLException(
                            "Schema croma incompatibile: " + column[0] + "." + column[1],
                            DatabaseSchema.INCOMPATIBLE_SCHEMA);
            }
        }
    }
}

package com.orodent.ecos.database;

import java.sql.*;

final class StockPolicySchema {
    static void verify(Connection c) throws SQLException {
        String[] names = {"POLVERE_ID", "NUMERO_VERSIONE", "UNITA", "VALORE", "CREATO_IL"};
        int[] types = {Types.BIGINT, Types.INTEGER, Types.VARCHAR, Types.DECIMAL, Types.TIMESTAMP};
        for (int i = 0; i < names.length; i++)
            try (var r = c.getMetaData().getColumns(null, "COSTI", "SCORTA_MINIMA", names[i])) {
                if (!r.next()
                        || r.getInt("DATA_TYPE") != types[i]
                        || i == 2 && r.getInt("COLUMN_SIZE") < 10
                        || i == 3
                                && (r.getInt("COLUMN_SIZE") < 15
                                        || r.getInt("DECIMAL_DIGITS") != 3))
                    throw new SQLException(
                            "Schema incompatibile: COSTI.SCORTA_MINIMA." + names[i], "XEC01");
            }
    }
}

package com.orodent.ecos.database;

import java.sql.*;

final class ModelDiameterSchema {
    static void verify(Connection c) throws SQLException {
        for (String table : new String[] {"MODELLO_DIAMETRI", "MODELLO_DIAM_STAMPI"}) {
            column(c, table, "MODELLO_ID", Types.BIGINT, 0, 0);
            column(c, table, "DIAMETRO_ARTICOLO_MM", Types.DECIMAL, 6, 2);
            column(c, table, "STAMPO_ID", Types.BIGINT, 0, 0);
            column(c, table, "STAMPO_REVISIONE", Types.INTEGER, 0, 0);
            column(c, table, "CODICE", Types.VARCHAR, 50, 0);
            column(c, table, "NOME", Types.VARCHAR, 100, 0);
            column(c, table, "DIAMETRO_MM", Types.DECIMAL, 10, 3);
        }
        column(c, "MODELLO_DIAMETRI", "GRAMMI_PER_MM", Types.DECIMAL, 12, 5);
    }

    private static void column(
            Connection c, String table, String name, int type, int size, int scale)
            throws SQLException {
        try (var r = c.getMetaData().getColumns(null, "TON", table, name)) {
            if (!r.next()
                    || r.getInt("DATA_TYPE") != type
                    || r.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls
                    || size > 0 && r.getInt("COLUMN_SIZE") != size
                    || type == Types.DECIMAL && r.getInt("DECIMAL_DIGITS") != scale)
                throw new SQLException(
                        "Schema diametri dei modelli incompatibile: " + table + "." + name,
                        DatabaseSchema.INCOMPATIBLE_SCHEMA);
        }
    }
}

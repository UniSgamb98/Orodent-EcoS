package com.orodent.ecos.database;

import java.sql.*;

final class ModelMouldSchema {
    static void verify(Connection c) throws SQLException {
        for (String table : new String[] {"MODELLO_STAMPO_RIF", "MODELLO_STAMPI_AMMESSI"}) {
            column(c, table, "MODELLO_ID", Types.BIGINT);
            column(c, table, "STAMPO_ID", Types.BIGINT);
            column(c, table, "STAMPO_REVISIONE", Types.INTEGER);
            column(c, table, "CODICE", Types.VARCHAR);
            column(c, table, "NOME", Types.VARCHAR);
            decimal(c, table, "DIAMETRO_MM");
        }
        decimal(c, "MODELLI_DISCO", "DIAMETRO_PRESSATURA_MM");
    }

    private static void column(Connection c, String table, String name, int type)
            throws SQLException {
        try (var r = c.getMetaData().getColumns(null, "TON", table, name)) {
            if (!r.next()
                    || r.getInt("DATA_TYPE") != type
                    || r.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls)
                throw invalid(table + "." + name);
        }
    }

    private static void decimal(Connection c, String table, String name) throws SQLException {
        column(c, table, name, Types.DECIMAL);
        try (var r = c.getMetaData().getColumns(null, "TON", table, name)) {
            if (!r.next() || r.getInt("COLUMN_SIZE") != 10 || r.getInt("DECIMAL_DIGITS") != 3)
                throw invalid(table + "." + name);
        }
    }

    private static SQLException invalid(String name) {
        return new SQLException(
                "Schema stampi dei modelli incompatibile: " + name,
                DatabaseSchema.INCOMPATIBLE_SCHEMA);
    }
}

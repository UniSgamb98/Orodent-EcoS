package com.orodent.ecos.database;

import java.sql.*;

final class EquipmentSchema {
    static void verify(Connection c) throws SQLException {
        for (String[] values :
                new String[][] {
                    {"PRESSE", "PRESSA_ID", "ALESAGGIO_MM"}, {"STAMPI", "STAMPO_ID", "DIAMETRO_MM"}
                }) {
            check(c, values[0], values[1], Types.BIGINT);
            check(c, values[0], "CODICE", Types.VARCHAR);
            check(c, values[0], "NOME", Types.VARCHAR);
            check(c, values[0], "ATTIVO", Types.BOOLEAN);
            check(c, values[0], "VERSIONE_MODIFICA", Types.INTEGER);
            check(c, values[0], values[2], Types.DECIMAL);
            try (var r = c.getMetaData().getColumns(null, "TON", values[0], values[2])) {
                if (!r.next() || r.getInt("COLUMN_SIZE") != 10 || r.getInt("DECIMAL_DIGITS") != 3)
                    throw incompatible(values[0]);
            }
        }
    }

    private static void check(Connection c, String table, String column, int type)
            throws SQLException {
        try (var r = c.getMetaData().getColumns(null, "TON", table, column)) {
            if (!r.next() || r.getInt("DATA_TYPE") != type)
                throw incompatible(table + "." + column);
        }
    }

    private static SQLException incompatible(String column) {
        return new SQLException(
                "Schema macchinari incompatibile: " + column, DatabaseSchema.INCOMPATIBLE_SCHEMA);
    }
}

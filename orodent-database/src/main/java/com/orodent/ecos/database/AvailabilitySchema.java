package com.orodent.ecos.database;

import java.sql.*;

final class AvailabilitySchema {
    static void verify(Connection c) throws SQLException {
        check(c, "DISPONIBILITA_IMPORT", "IMPORT_ID", Types.BIGINT);
        check(c, "DISPONIBILITA_IMPORT", "TIPO", Types.VARCHAR);
        check(c, "DISPONIBILITA_IMPORT", "NOME_FILE", Types.VARCHAR);
        check(c, "DISPONIBILITA_IMPORT", "DATA_RIFERIMENTO", Types.DATE);
        check(c, "DISPONIBILITA_IMPORT", "IMPORTATO_IL", Types.TIMESTAMP);
        check(c, "DISPONIBILITA_IMPORT", "CODICI_ASSENTI_ZERO", Types.BOOLEAN);
        check(c, "DISPONIBILITA_IMPORT", "RIGHE", Types.INTEGER);
        check(c, "DISPONIBILITA_VALORI", "IMPORT_ID", Types.BIGINT);
        check(c, "DISPONIBILITA_VALORI", "CODICE", Types.VARCHAR);
        check(c, "DISPONIBILITA_VALORI", "QUANTITA", Types.DECIMAL);
        check(c, "DISPONIBILITA_FONTI", "TIPO", Types.VARCHAR);
        check(c, "DISPONIBILITA_FONTI", "IMPORT_ID", Types.BIGINT);
        check(c, "DISPONIBILITA_FONTI", "VERSIONE_MODIFICA", Types.INTEGER);
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT COUNT(*) FROM TON.DISPONIBILITA_FONTI WHERE tipo IN"
                                    + " ('DISPONIBILITA','ORDINI')")) {
            r.next();
            if (r.getInt(1) != 2)
                throw new SQLException(
                        "Fonti disponibilità mancanti.", DatabaseSchema.INCOMPATIBLE_SCHEMA);
        }
    }

    private static void check(Connection c, String table, String name, int type)
            throws SQLException {
        try (var r = c.getMetaData().getColumns(null, "TON", table, name)) {
            if (!r.next() || r.getInt("DATA_TYPE") != type)
                throw new SQLException(
                        "Schema disponibilità incompatibile: " + table + "." + name,
                        DatabaseSchema.INCOMPATIBLE_SCHEMA);
        }
    }
}

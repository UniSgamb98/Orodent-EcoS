package com.orodent.ecos.database;

import java.sql.*;

final class ProductionPlanSchema {
    static void verify(Connection connection) throws SQLException {
        check(connection, "PIANI_PRODUZIONE", "PIANO_ID", Types.BIGINT);
        check(connection, "PIANI_PRODUZIONE", "CONTENUTO_PIANO", Types.CLOB);
        check(connection, "PIANI_PRODUZIONE", "VERSIONE_MODIFICA", Types.INTEGER);
        check(connection, "PIANO_PRODUZIONE_RIGHE", "CONFIGURAZIONE_ID", Types.BIGINT);
        check(connection, "PIANO_PRODUZIONE_RIGHE", "QUANTITA_RICHIESTA", Types.INTEGER);
        check(connection, "PIANI_DOCUMENTI", "CONTENUTO_HTML", Types.CLOB);
    }

    private static void check(Connection c, String table, String column, int type)
            throws SQLException {
        try (var r = c.getMetaData().getColumns(null, "TON", table, column)) {
            if (!r.next() || r.getInt("DATA_TYPE") != type)
                throw new SQLException(
                        "Schema piani di produzione incompatibile: " + table + "." + column,
                        DatabaseSchema.INCOMPATIBLE_SCHEMA);
        }
    }
}

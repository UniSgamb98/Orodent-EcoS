package com.orodent.ecos.database;

import java.sql.*;

final class ScenarioSchema {
    static void verify(Connection c) throws SQLException {
        record Col(String table, String name, int type, int size) {}
        var columns =
                java.util.List.of(
                        new Col("SCENARI_BASI", "BASE_ID", Types.VARCHAR, 36),
                        new Col("SCENARI_BASI", "FOTOGRAFIA", Types.CLOB, 0),
                        new Col("SCENARI", "SCENARIO_ID", Types.VARCHAR, 36),
                        new Col("SCENARI", "NUMERO_VERSIONE", Types.INTEGER, 0),
                        new Col("SCENARI_REVISIONI", "SCENARIO_ID", Types.VARCHAR, 36),
                        new Col("SCENARI_REVISIONI", "NUMERO_VERSIONE", Types.INTEGER, 0),
                        new Col("SCENARI_REVISIONI", "BASE_ID", Types.VARCHAR, 36),
                        new Col("SCENARI_REVISIONI", "NOME", Types.VARCHAR, 160),
                        new Col("SCENARI_REVISIONI", "NOTE", Types.VARCHAR, 4000),
                        new Col("SCENARI_REVISIONI", "ARCHIVIATO", Types.BOOLEAN, 0),
                        new Col("SCENARI_REVISIONI", "MODIFICHE", Types.CLOB, 0),
                        new Col("SCENARI_REVISIONI", "CREATO_IL", Types.TIMESTAMP, 0));
        for (var col : columns)
            try (var r = c.getMetaData().getColumns(null, "COSTI", col.table(), col.name())) {
                if (!r.next()
                        || r.getInt("DATA_TYPE") != col.type()
                        || r.getInt("COLUMN_SIZE") < col.size())
                    throw new SQLException(
                            "Schema incompatibile: COSTI." + col.table() + "." + col.name(),
                            "XEC01");
            }
    }
}

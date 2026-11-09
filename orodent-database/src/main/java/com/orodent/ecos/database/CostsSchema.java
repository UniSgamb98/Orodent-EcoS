package com.orodent.ecos.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/** Read-only contract for the economic snapshots. No automatic inventory movements. */
final class CostsSchema {
    private CostsSchema() {}

    static void verify(Connection connection) throws SQLException {
        for (var column :
                List.of(
                        new Column("POLVERI_PREZZI", "PREZZO_ID", Types.BIGINT, 0, 0),
                        new Column("POLVERI_PREZZI", "POLVERE_ID", Types.BIGINT, 0, 0),
                        new Column("POLVERI_PREZZI", "NUMERO_VERSIONE", Types.INTEGER, 0, 0),
                        new Column("POLVERI_PREZZI", "DATA_RIFERIMENTO", Types.DATE, 0, 0),
                        new Column("POLVERI_PREZZI", "MODALITA", Types.VARCHAR, 10, 0),
                        new Column("POLVERI_PREZZI", "PREZZO_SEMPLICE", Types.DECIMAL, 15, 4),
                        new Column("POLVERI_PREZZI", "QUANTITA_SEMPLICE", Types.DECIMAL, 15, 3),
                        new Column("POLVERI_PREZZI", "CREATO_IL", Types.TIMESTAMP, 0, 0),
                        new Column("POLVERI_LOTTI", "PREZZO_ID", Types.BIGINT, 0, 0),
                        new Column("POLVERI_LOTTI", "POSIZIONE", Types.INTEGER, 0, 0),
                        new Column("POLVERI_LOTTI", "LOTTO", Types.VARCHAR, 100, 0),
                        new Column("POLVERI_LOTTI", "QUANTITA_KG", Types.DECIMAL, 15, 3),
                        new Column("POLVERI_LOTTI", "PREZZO_KG", Types.DECIMAL, 15, 4))) {
            try (var rows =
                    connection.getMetaData().getColumns(null, "COSTI", column.table, column.name)) {
                if (!rows.next()
                        || rows.getInt("DATA_TYPE") != column.type
                        || rows.getInt("COLUMN_SIZE") < column.precision
                        || column.type == Types.DECIMAL
                                && rows.getInt("DECIMAL_DIGITS") != column.scale)
                    throw new SQLException(
                            "Schema incompatibile: COSTI." + column.table + "." + column.name,
                            "XEC01");
            }
        }
    }

    private record Column(String table, String name, int type, int precision, int scale) {}
}

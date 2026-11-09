package com.orodent.ecos.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/** Read-only client contract for V8; never applies migrations. */
final class ProductCostsSchema {
    static void verify(Connection connection) throws SQLException {
        for (var column :
                List.of(
                        new Column("PRODOTTI_PARAMETRI", "PARAMETRO_ID", Types.BIGINT, 0, 0),
                        new Column("PRODOTTI_PARAMETRI", "PRODOTTO_ID", Types.BIGINT, 0, 0),
                        new Column("PRODOTTI_PARAMETRI", "NUMERO_VERSIONE", Types.INTEGER, 0, 0),
                        new Column(
                                "PRODOTTI_PARAMETRI", "TRASFORMAZIONE_EUR", Types.DECIMAL, 15, 4),
                        new Column("PRODOTTI_PARAMETRI", "CREATO_IL", Types.TIMESTAMP, 0, 0),
                        new Column("ARTICOLI_TRASFORMAZIONE", "PARAMETRO_ID", Types.BIGINT, 0, 0),
                        new Column("ARTICOLI_TRASFORMAZIONE", "ARTICOLO_ID", Types.BIGINT, 0, 0),
                        new Column(
                                "ARTICOLI_TRASFORMAZIONE",
                                "TRASFORMAZIONE_EUR",
                                Types.DECIMAL,
                                15,
                                4))) {
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

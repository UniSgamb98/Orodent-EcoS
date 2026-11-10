package com.orodent.ecos.database;

import java.sql.*;
import java.util.*;

final class WarehouseSchema {
    static void verify(Connection c) throws SQLException {
        List<Column> columns =
                List.of(
                        new Column("POLVERI_PREZZI", "MODELLO_DATI", Types.INTEGER, 0, 0),
                        new Column("POLVERI_PREZZI", "PREZZO_RIFERIMENTO", Types.DECIMAL, 19, 8),
                        new Column("POLVERI_PREZZI", "QUANTITA_TOTALE", Types.DECIMAL, 15, 3),
                        new Column("ORDINI_POLVERI", "ORDINE_ID", Types.BIGINT, 0, 0),
                        new Column("ORDINI_POLVERI", "FORNITORE", Types.VARCHAR, 80, 0),
                        new Column("ORDINI_POLVERI", "NUMERO_ORDINE", Types.VARCHAR, 80, 0),
                        new Column("ORDINI_POLVERI", "POSIZIONE", Types.INTEGER, 0, 0),
                        new Column("ORDINI_POLVERI_REVISIONI", "ORDINE_ID", Types.BIGINT, 0, 0),
                        new Column(
                                "ORDINI_POLVERI_REVISIONI", "NUMERO_VERSIONE", Types.INTEGER, 0, 0),
                        new Column("ORDINI_POLVERI_REVISIONI", "POLVERE_ID", Types.BIGINT, 0, 0),
                        new Column(
                                "ORDINI_POLVERI_REVISIONI",
                                "CODICE_SORGENTE",
                                Types.VARCHAR,
                                80,
                                0),
                        new Column(
                                "ORDINI_POLVERI_REVISIONI", "DESCRIZIONE", Types.VARCHAR, 500, 0),
                        new Column("ORDINI_POLVERI_REVISIONI", "DATA_ORDINE", Types.DATE, 0, 0),
                        new Column(
                                "ORDINI_POLVERI_REVISIONI", "CONSEGNA_PREVISTA", Types.DATE, 0, 0),
                        new Column(
                                "ORDINI_POLVERI_REVISIONI", "CONSEGNA_MANUALE", Types.DATE, 0, 0),
                        new Column(
                                "ORDINI_POLVERI_REVISIONI",
                                "QUANTITA_ORDINATA",
                                Types.DECIMAL,
                                15,
                                3),
                        new Column(
                                "ORDINI_POLVERI_REVISIONI",
                                "QUANTITA_CONSEGNATA",
                                Types.DECIMAL,
                                15,
                                3),
                        new Column(
                                "ORDINI_POLVERI_REVISIONI",
                                "QUANTITA_RESIDUA",
                                Types.DECIMAL,
                                15,
                                3),
                        new Column("ORDINI_POLVERI_REVISIONI", "PREZZO_KG", Types.DECIMAL, 15, 4),
                        new Column("ORDINI_POLVERI_REVISIONI", "ESCLUSO", Types.BOOLEAN, 0, 0),
                        new Column("ORDINI_POLVERI_REVISIONI", "ORIGINE", Types.VARCHAR, 20, 0),
                        new Column("ORDINI_POLVERI_REVISIONI", "CREATO_IL", Types.TIMESTAMP, 0, 0),
                        new Column("POLVERI_CODICI_FORNITORE", "FORNITORE", Types.VARCHAR, 80, 0),
                        new Column(
                                "POLVERI_CODICI_FORNITORE",
                                "CODICE_SORGENTE",
                                Types.VARCHAR,
                                80,
                                0),
                        new Column("POLVERI_CODICI_FORNITORE", "POLVERE_ID", Types.BIGINT, 0, 0));
        for (var col : columns)
            try (var r = c.getMetaData().getColumns(null, "COSTI", col.table, col.name)) {
                if (!r.next()
                        || r.getInt("DATA_TYPE") != col.type
                        || r.getInt("COLUMN_SIZE") < col.precision
                        || col.type == Types.DECIMAL && r.getInt("DECIMAL_DIGITS") != col.scale)
                    throw new SQLException(
                            "Schema incompatibile: COSTI." + col.table + "." + col.name, "XEC01");
            }
    }

    private record Column(String table, String name, int type, int precision, int scale) {}
}

package com.orodent.ecos.database;

import java.sql.*;

/** Validate CRM only once V13 is present; older schemas can still be migrated. */
final class CrmSchema {
    static void verify(Connection c) throws SQLException {
        String[] tables = {
            "CLIENTI",
            "CONTATTI_CLIENTE",
            "INDIRIZZI_CLIENTE",
            "TELEFONI_CLIENTE",
            "EMAIL_CLIENTE",
            "SITI_WEB_CLIENTE",
            "NOTE_CLIENTE",
            "INTERAZIONI",
            "CLIENTI_PREFERITI",
            "VISTE_SALVATE",
            "TIPI_CLIENTE",
            "STATI_TRATTATIVA",
            "FORNI",
            "FRESATORI",
            "MATERIALI_DI_CONSUMO",
            "CANALI_DI_ACQUISTO",
            "CLIENTI_FORNI",
            "CLIENTI_FRESATORI",
            "CLIENTI_MATERIALI",
            "CATALOGHI_VERSIONI"
        };
        for (String table : tables)
            try (var r = c.getMetaData().getTables(null, "CRM", table, new String[] {"TABLE"})) {
                if (!r.next()) throw incompatible(table);
            }
        column(c, "CRM", "CLIENTI", "VERSIONE_MODIFICA", Types.INTEGER, 0);
        column(c, "CRM", "CLIENTI", "CODICE_GESTIONALE", Types.VARCHAR, 50);
        column(c, "ECOS", "OPERATORI", "ID", Types.CHAR, 36);
        column(c, "ECOS", "OPERATORI", "USERNAME", Types.VARCHAR, 80);
    }

    private static void column(
            Connection c, String schema, String table, String name, int type, int size)
            throws SQLException {
        try (var r = c.getMetaData().getColumns(null, schema, table, name)) {
            if (!r.next() || r.getInt("DATA_TYPE") != type || r.getInt("COLUMN_SIZE") < size)
                throw incompatible(table + "." + name);
        }
    }

    private static SQLException incompatible(String name) {
        return new SQLException(
                "Schema CRM incompatibile: " + name, DatabaseSchema.INCOMPATIBLE_SCHEMA);
    }
}

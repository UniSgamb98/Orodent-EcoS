package com.orodent.ecos.database;

import java.sql.*;
import java.util.*;

/** Old snapshots remain explicitly unallocated; their depot cannot be inferred retrospectively. */
final class AvailabilityWarehouseMigration {
    static void apply(Connection c) throws SQLException {
        String primaryKey;
        try (var r = c.getMetaData().getPrimaryKeys(null, "TON", "DISPONIBILITA_VALORI")) {
            if (!r.next()) throw new SQLException("Chiave delle disponibilità mancante.");
            primaryKey = r.getString("PK_NAME");
        }
        try (var q = c.createStatement()) {
            q.executeUpdate(
                    "ALTER TABLE TON.DISPONIBILITA_VALORI DROP CONSTRAINT \""
                            + primaryKey.replace("\"", "\"\"")
                            + "\"");
            q.executeUpdate(
                    "ALTER TABLE TON.DISPONIBILITA_VALORI ADD CONSTRAINT pk_ton_disp_valori PRIMARY"
                        + " KEY(import_id,codice,magazzino)");
        }
    }

    static void verify(Connection c) throws SQLException {
        try (var q = c.createStatement()) {
            q.executeQuery("SELECT codice,nome FROM TON.MAGAZZINI WHERE 1=0").close();
            q.executeQuery("SELECT magazzini_distinti FROM TON.DISPONIBILITA_IMPORT WHERE 1=0")
                    .close();
        }
        Map<Short, String> columns = new TreeMap<>();
        try (var r = c.getMetaData().getPrimaryKeys(null, "TON", "DISPONIBILITA_VALORI")) {
            while (r.next()) columns.put(r.getShort("KEY_SEQ"), r.getString("COLUMN_NAME"));
        }
        if (!List.copyOf(columns.values()).equals(List.of("IMPORT_ID", "CODICE", "MAGAZZINO")))
            throw new SQLException(
                    "Chiave disponibilità per magazzino incompatibile.",
                    DatabaseSchema.INCOMPATIBLE_SCHEMA);
    }
}

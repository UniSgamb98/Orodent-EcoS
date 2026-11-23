package com.orodent.ecos.database;

import java.sql.*;
import java.util.*;

/** Installs row triggers for the complete CRM domain, including cascades and direct SQL writes. */
final class CrmAuditMigration {
    private static final List<String> TABLES =
            List.of(
                    "ECOS.OPERATORI",
                    "CRM.CLIENTI",
                    "CRM.NOTE_CLIENTE",
                    "CRM.INTERAZIONI",
                    "CRM.CONTATTI_CLIENTE",
                    "CRM.INDIRIZZI_CLIENTE",
                    "CRM.TELEFONI_CLIENTE",
                    "CRM.EMAIL_CLIENTE",
                    "CRM.SITI_WEB_CLIENTE",
                    "CRM.CLIENTI_FORNI",
                    "CRM.CLIENTI_FRESATORI",
                    "CRM.CLIENTI_MATERIALI",
                    "CRM.CLIENTI_PREFERITI",
                    "CRM.VISTE_SALVATE",
                    "CRM.FORNI",
                    "CRM.FRESATORI",
                    "CRM.MATERIALI_DI_CONSUMO",
                    "CRM.CANALI_DI_ACQUISTO",
                    "CRM.TIPI_CLIENTE",
                    "CRM.STATI_TRATTATIVA");

    static void apply(Connection c) throws SQLException {
        for (String table : TABLES) {
            String[] parts = table.split("\\.");
            var columns = new LinkedHashMap<String, Integer>();
            try (var r = c.getMetaData().getColumns(null, parts[0], parts[1], null)) {
                while (r.next()) columns.put(r.getString("COLUMN_NAME"), r.getInt("DATA_TYPE"));
            }
            for (String kind : List.of("INSERT", "UPDATE", "DELETE")) {
                String row = kind.equals("DELETE") ? "O" : "N";
                String key =
                        columns.containsKey("ID")
                                ? row + ".ID"
                                : row + ".OPERATORE_ID || ':' || " + row + ".CLIENTE_ID";
                String customer =
                        table.equals("CRM.CLIENTI")
                                ? row + ".ID"
                                : columns.containsKey("CLIENTE_ID")
                                        ? row + ".CLIENTE_ID"
                                        : "CAST(NULL AS VARCHAR(36))";
                String refs =
                        kind.equals("INSERT")
                                ? "NEW AS N"
                                : kind.equals("DELETE") ? "OLD AS O" : "OLD AS O NEW AS N";
                String before =
                        kind.equals("INSERT") ? "CAST(NULL AS CLOB)" : snapshot(columns, "O");
                String after =
                        kind.equals("DELETE") ? "CAST(NULL AS CLOB)" : snapshot(columns, "N");
                try (var q = c.createStatement()) {
                    q.executeUpdate(
                            "CREATE TRIGGER "
                                    + parts[0]
                                    + ".AUD_"
                                    + parts[1]
                                    + "_"
                                    + kind.charAt(0)
                                    + " AFTER "
                                    + kind
                                    + " ON "
                                    + table
                                    + " REFERENCING "
                                    + refs
                                    + " FOR EACH ROW CALL AUDIT.RECORD_CHANGE('"
                                    + table
                                    + "',"
                                    + key
                                    + ","
                                    + customer
                                    + ","
                                    + before
                                    + ","
                                    + after
                                    + ")");
                }
            }
        }
    }

    private static String snapshot(Map<String, Integer> columns, String row) {
        var fields = new ArrayList<String>();
        columns.forEach(
                (name, type) -> {
                    String value = row + "." + name;
                    if (List.of(
                                    Types.SMALLINT,
                                    Types.INTEGER,
                                    Types.BIGINT,
                                    Types.DECIMAL,
                                    Types.NUMERIC,
                                    Types.DOUBLE,
                                    Types.REAL,
                                    Types.FLOAT)
                            .contains(type)) value = "TRIM(CHAR(" + value + "))";
                    if (type != Types.CLOB)
                        value = "CAST(CAST(" + value + " AS VARCHAR(32672)) AS CLOB)";
                    fields.add("AUDIT.FIELD_VALUE('" + name + "'," + value + ")");
                });
        return String.join(" || ", fields);
    }

    static void verify(Connection c) throws SQLException {
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT COUNT(*) FROM SYS.SYSTRIGGERS WHERE TRIGGERNAME LIKE"
                                    + " 'AUD\\_%' ESCAPE '\\'")) {
            r.next();
            if (r.getInt(1) != TABLES.size() * 3)
                throw new SQLException(
                        "Trigger registro CRM mancanti", DatabaseSchema.INCOMPATIBLE_SCHEMA);
        }
        try (var q = c.createStatement()) {
            q.executeQuery(
                            "SELECT"
                                + " OPERATION_ID,OCCURRED_AT,ACTOR_ID,ACTOR_NAME,STATION,SOURCE,DATABASE_USER"
                                + " FROM AUDIT.OPERATIONS WHERE 1=0")
                    .close();
            q.executeQuery(
                            "SELECT"
                                + " OPERATION_ID,TABLE_NAME,ROW_KEY,CUSTOMER_ID,CUSTOMER_NAME,CHANGE_KIND,BEFORE_VALUE,AFTER_VALUE"
                                + " FROM AUDIT.CHANGES WHERE 1=0")
                    .close();
        }
    }
}

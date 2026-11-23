package com.orodent.ecos.database.audit;

import java.sql.*;
import java.util.*;

/** Trigger entry point. Uses Derby's caller transaction, never opens or commits another one. */
public final class AuditWriter {
    private AuditWriter() {}

    public static void record(
            String table, String key, String customer, Clob oldValue, Clob newValue)
            throws SQLException {
        String before = AuditValues.text(oldValue), after = AuditValues.text(newValue);
        if (AuditValues.equal(before, after)) return;
        Connection c = DriverManager.getConnection("jdbc:default:connection");
        var context = AuditContext.current(c);
        boolean existing = false;
        try (var q =
                c.prepareStatement(
                        "SELECT BEFORE_VALUE FROM AUDIT.CHANGES WHERE OPERATION_ID=? AND"
                            + " TABLE_NAME=? AND ROW_KEY=?")) {
            q.setString(1, context.operationId());
            q.setString(2, table);
            q.setString(3, key);
            try (var r = q.executeQuery()) {
                if (r.next()) {
                    existing = true;
                    before = AuditValues.text(r.getClob(1));
                }
            }
        }
        if (AuditValues.equal(before, after)) {
            try (var q =
                    c.prepareStatement(
                            "DELETE FROM AUDIT.CHANGES WHERE OPERATION_ID=? AND TABLE_NAME=? AND"
                                + " ROW_KEY=?")) {
                q.setString(1, context.operationId());
                q.setString(2, table);
                q.setString(3, key);
                q.executeUpdate();
            }
            try (var q =
                    c.prepareStatement(
                            "DELETE FROM AUDIT.OPERATIONS WHERE OPERATION_ID=? AND NOT EXISTS"
                                + " (SELECT 1 FROM AUDIT.CHANGES C WHERE C.OPERATION_ID=?)")) {
                q.setString(1, context.operationId());
                q.setString(2, context.operationId());
                q.executeUpdate();
            }
            return;
        }
        ensureOperation(c, context);
        String name = customerName(c, customer, before, after);
        String kind = before == null ? "INSERT" : after == null ? "DELETE" : "UPDATE";
        if (existing) {
            try (var q =
                    c.prepareStatement(
                            "UPDATE AUDIT.CHANGES SET AFTER_VALUE=?, CHANGE_KIND=?,"
                                + " CUSTOMER_NAME=COALESCE(?,CUSTOMER_NAME) WHERE OPERATION_ID=?"
                                + " AND TABLE_NAME=? AND ROW_KEY=?")) {
                q.setString(1, after);
                q.setString(2, kind);
                q.setString(3, name);
                q.setString(4, context.operationId());
                q.setString(5, table);
                q.setString(6, key);
                q.executeUpdate();
            }
        } else {
            try (var q =
                    c.prepareStatement(
                            "INSERT INTO"
                                + " AUDIT.CHANGES(OPERATION_ID,TABLE_NAME,ROW_KEY,CUSTOMER_ID,CUSTOMER_NAME,CHANGE_KIND,BEFORE_VALUE,AFTER_VALUE)"
                                + " VALUES (?,?,?,?,?,?,?,?)")) {
                q.setString(1, context.operationId());
                q.setString(2, table);
                q.setString(3, key);
                q.setString(4, customer);
                q.setString(5, name);
                q.setString(6, kind);
                q.setString(7, before);
                q.setString(8, after);
                q.executeUpdate();
            }
        }
    }

    private static void ensureOperation(Connection c, AuditContext.Context ctx)
            throws SQLException {
        try (var q = c.prepareStatement("SELECT 1 FROM AUDIT.OPERATIONS WHERE OPERATION_ID=?")) {
            q.setString(1, ctx.operationId());
            try (var r = q.executeQuery()) {
                if (r.next()) return;
            }
        }
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " AUDIT.OPERATIONS(OPERATION_ID,ACTOR_ID,ACTOR_NAME,STATION,SOURCE,DATABASE_USER)"
                            + " VALUES (?,?,?,?,?,CURRENT_USER)")) {
            q.setString(1, ctx.operationId());
            q.setString(2, ctx.actorId());
            q.setString(3, ctx.actorName());
            q.setString(4, ctx.station());
            q.setString(5, ctx.source());
            q.executeUpdate();
        }
    }

    private static String customerName(Connection c, String customer, String before, String after)
            throws SQLException {
        if (customer == null) return null;
        String name = AuditValues.decode(after).get("RAGIONE_SOCIALE");
        if (name == null) name = AuditValues.decode(before).get("RAGIONE_SOCIALE");
        if (name != null) return name;
        try (var q = c.prepareStatement("SELECT RAGIONE_SOCIALE FROM CRM.CLIENTI WHERE ID=?")) {
            q.setString(1, customer);
            try (var r = q.executeQuery()) {
                if (r.next()) return r.getString(1);
            }
        }
        return null;
    }
}

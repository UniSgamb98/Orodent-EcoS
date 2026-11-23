package com.orodent.ecos.database.audit;

import java.sql.*;
import java.util.UUID;

/** Connection-local transaction metadata, also visible to Derby triggers on the HOST. */
public final class AuditContext {
    private AuditContext() {}

    public static void begin(Connection connection, UUID actorId, String actorName, String source)
            throws SQLException {
        declare(connection);
        try (var q = connection.createStatement()) {
            q.executeUpdate("DELETE FROM SESSION.ECOS_AUDIT_CONTEXT");
        }
        String station = System.getenv("COMPUTERNAME");
        if (station == null || station.isBlank()) station = System.getenv("HOSTNAME");
        insert(
                connection,
                new Context(
                        UUID.randomUUID().toString(),
                        actorId == null ? null : actorId.toString(),
                        actorName,
                        station,
                        source));
    }

    record Context(
            String operationId, String actorId, String actorName, String station, String source) {}

    static Context current(Connection c) throws SQLException {
        try (var q = c.createStatement();
                var r = q.executeQuery("SELECT * FROM SESSION.ECOS_AUDIT_CONTEXT")) {
            if (r.next())
                return new Context(
                        r.getString(1),
                        r.getString(2),
                        r.getString(3),
                        r.getString(4),
                        r.getString(5));
        } catch (SQLException absent) {
            if (!"42X05".equals(absent.getSQLState())) throw absent;
        }
        // Trigger routines cannot declare session tables. Uninstrumented SQL remains attributable
        // to its database user, without inventing a human author or merging unrelated statements.
        return new Context(
                UUID.randomUUID().toString(),
                null,
                null,
                null,
                "SQL esterno / origine non dichiarata");
    }

    private static void declare(Connection c) throws SQLException {
        try (var q = c.createStatement()) {
            q.executeUpdate(
                    "DECLARE GLOBAL TEMPORARY TABLE SESSION.ECOS_AUDIT_CONTEXT (OPERATION_ID"
                        + " VARCHAR(36), ACTOR_ID VARCHAR(36), ACTOR_NAME VARCHAR(240), STATION"
                        + " VARCHAR(128), SOURCE VARCHAR(80)) ON COMMIT DELETE ROWS NOT LOGGED");
        } catch (SQLException existing) {
            if (!"X0Y32".equals(existing.getSQLState())) throw existing;
        }
    }

    private static void insert(Connection c, Context value) throws SQLException {
        try (var q =
                c.prepareStatement("INSERT INTO SESSION.ECOS_AUDIT_CONTEXT VALUES (?,?,?,?,?)")) {
            q.setString(1, value.operationId());
            q.setString(2, value.actorId());
            q.setString(3, value.actorName());
            q.setString(4, value.station());
            q.setString(5, value.source());
            q.executeUpdate();
        }
    }
}

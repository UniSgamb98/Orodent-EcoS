package com.orodent.ecos.database;

import java.sql.*;
import java.util.*;

final class AuditFixtures {
    static void beforeV24(Connection c) throws SQLException {
        AvailabilityWarehouseFixtures.beforeV25(c);
        var triggers = new ArrayList<String>();
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT S.SCHEMANAME,T.TRIGGERNAME FROM SYS.SYSTRIGGERS T JOIN"
                                    + " SYS.SYSSCHEMAS S ON S.SCHEMAID=T.SCHEMAID WHERE"
                                    + " T.TRIGGERNAME LIKE 'AUD\\_%' ESCAPE '\\'")) {
            while (r.next()) triggers.add(r.getString(1) + "." + r.getString(2));
        }
        try (var q = c.createStatement()) {
            for (String trigger : triggers) q.executeUpdate("DROP TRIGGER " + trigger);
            q.executeUpdate("DROP PROCEDURE AUDIT.RECORD_CHANGE");
            q.executeUpdate("DROP FUNCTION AUDIT.FIELD_VALUE");
            q.executeUpdate("DROP TABLE AUDIT.CHANGES");
            q.executeUpdate("DROP TABLE AUDIT.OPERATIONS");
            q.executeUpdate("DROP SCHEMA AUDIT RESTRICT");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE VERSION=24");
        }
    }
}

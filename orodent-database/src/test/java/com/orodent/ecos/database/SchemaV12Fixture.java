package com.orodent.ecos.database;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/** Reconstruct the actual pre-CRM schema when testing older upgrade paths. */
final class SchemaV12Fixture {
    static void removeCrm(Connection c) throws SQLException {
        String sql;
        try (var input =
                DatabaseSchema.class.getResourceAsStream("/database/migrations/V13__crm.sql")) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new SQLException(e);
        }
        var tables = new ArrayList<String>();
        var matcher = Pattern.compile("^CREATE TABLE ([A-Z_.]+)", Pattern.MULTILINE).matcher(sql);
        while (matcher.find()) tables.add(matcher.group(1));
        Collections.reverse(tables);
        c.setSchema("CRM");
        try (var q = c.createStatement()) {
            for (String table : tables) q.executeUpdate("DROP TABLE " + table);
            q.executeUpdate("DROP SCHEMA CRM RESTRICT");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version>=13");
        }
        c.setSchema("APP");
    }
}

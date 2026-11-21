package com.orodent.ecos.database;

import java.sql.*;
import java.util.*;

final class SupplierMigration {
    static void apply(Connection c) throws SQLException {
        for (String table : List.of("TON.POLVERI", "COSTI.ORDINI_POLVERI")) {
            var names = new ArrayList<String>();
            try (var q = c.createStatement();
                    var r =
                            q.executeQuery(
                                    "SELECT DISTINCT fornitore FROM "
                                            + table
                                            + " WHERE fornitore IS NOT NULL ORDER BY fornitore")) {
                while (r.next()) names.add(r.getString(1));
            }
            for (String name : names) {
                Long id = SupplierCatalogue.resolve(c, name);
                if (id == null) continue;
                try (var q =
                        c.prepareStatement(
                                "UPDATE " + table + " SET fornitore_id=? WHERE fornitore=?")) {
                    q.setLong(1, id);
                    q.setString(2, name);
                    q.executeUpdate();
                }
            }
        }
    }
}

package com.orodent.ecos.database;

import java.sql.*;

final class SupplierSchema {
    static void verify(Connection c) throws SQLException {
        check(c, "ANAGRAFICHE", "FORNITORI", "FORNITORE_ID", Types.BIGINT);
        check(c, "ANAGRAFICHE", "FORNITORI", "CODICE", Types.VARCHAR);
        check(c, "ANAGRAFICHE", "FORNITORI", "NOME", Types.VARCHAR);
        check(c, "ANAGRAFICHE", "FORNITORI", "NOME_NORMALIZZATO", Types.VARCHAR);
        check(c, "ANAGRAFICHE", "FORNITORI", "ATTIVO", Types.BOOLEAN);
        check(c, "ANAGRAFICHE", "FORNITORI", "VERSIONE_MODIFICA", Types.INTEGER);
        check(c, "ANAGRAFICHE", "FORNITORI_ALIAS", "NOME_NORMALIZZATO", Types.VARCHAR);
        check(c, "ANAGRAFICHE", "FORNITORI_ALIAS", "FORNITORE_ID", Types.BIGINT);
        check(c, "TON", "POLVERI", "FORNITORE_ID", Types.BIGINT);
        check(c, "COSTI", "ORDINI_POLVERI", "FORNITORE_ID", Types.BIGINT);
    }

    private static void check(Connection c, String schema, String table, String name, int type)
            throws SQLException {
        try (var r = c.getMetaData().getColumns(null, schema, table, name)) {
            if (!r.next() || r.getInt("DATA_TYPE") != type)
                throw new SQLException(
                        "Schema fornitori incompatibile: " + table + "." + name,
                        DatabaseSchema.INCOMPATIBLE_SCHEMA);
        }
    }
}

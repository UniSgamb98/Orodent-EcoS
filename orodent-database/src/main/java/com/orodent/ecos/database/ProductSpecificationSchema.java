package com.orodent.ecos.database;

import java.sql.*;

final class ProductSpecificationSchema {
    static void verify(Connection c) throws SQLException {
        check(c, "CONFIGURAZIONI_PRODOTTO", "CONFIGURAZIONE_ID", Types.BIGINT);
        check(c, "CONFIGURAZIONI_PRODOTTO", "PRODOTTO_ID", Types.BIGINT);
        check(c, "CONFIGURAZIONI_PRODOTTO", "PRODOTTO_REVISIONE", Types.INTEGER);
        check(c, "CONFIGURAZIONI_MIGRATE", "ARTICOLO_CONFIGURAZIONE_ID", Types.BIGINT);
        check(c, "CONFIGURAZIONI_MIGRATE", "PRODOTTO_CONFIGURAZIONE_ID", Types.BIGINT);
        for (String t : new String[] {"PRODOTTI", "PRODOTTI_REVISIONI"}) {
            check(c, t, "CONFIGURAZIONE_PREDEFINITA_ID", Types.BIGINT);
            check(c, t, "SPECIFICHE_DA_VERIFICARE", Types.BOOLEAN);
        }
        check(c, "PIANO_PRODOTTO_RIGHE", "CONFIGURAZIONE_ID", Types.BIGINT);
        check(c, "PIANO_PRODOTTO_RIGHE", "ARTICOLO_ID", Types.BIGINT);
        check(c, "PIANO_PRODOTTO_RIGHE", "ARTICOLO_REVISIONE", Types.INTEGER);
    }

    private static void check(Connection c, String table, String name, int type)
            throws SQLException {
        try (var r = c.getMetaData().getColumns(null, "TON", table, name)) {
            if (!r.next() || r.getInt("DATA_TYPE") != type)
                throw new SQLException(
                        "Schema specifiche prodotto incompatibile: " + table + "." + name,
                        DatabaseSchema.INCOMPATIBLE_SCHEMA);
        }
    }
}

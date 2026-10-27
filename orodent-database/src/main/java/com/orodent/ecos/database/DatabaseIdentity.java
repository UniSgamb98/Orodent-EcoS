package com.orodent.ecos.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/** Persistent Derby property: no schema migration or external copy of the identity. */
final class DatabaseIdentity {
    static final String MISMATCH = "XEC03";
    private static final String PROPERTY = "orodent.ecos.database.identity";

    private DatabaseIdentity() { }

    static UUID read(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("VALUES SYSCS_UTIL.SYSCS_GET_DATABASE_PROPERTY(?)")) {
            statement.setString(1, PROPERTY);
            try (var result = statement.executeQuery()) {
                result.next();
                String value = result.getString(1);
                if (value == null) return null;
                try { return UUID.fromString(value); }
                catch (IllegalArgumentException invalid) {
                    throw new SQLException("Identità del database non valida.", MISMATCH, invalid);
                }
            }
        }
    }

    static UUID initialize(Connection connection) throws SQLException {
        UUID identity = read(connection);
        if (identity != null) return identity;
        identity = UUID.randomUUID();
        try (var statement = connection.prepareCall("CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(?, ?)")) {
            statement.setString(1, PROPERTY);
            statement.setString(2, identity.toString());
            statement.execute();
        }
        return identity;
    }

    static UUID require(Connection connection, UUID expected) throws SQLException {
        UUID actual = read(connection);
        if (actual == null) throw new SQLException(
                "L'HOST non espone l'identità del database: aggiornare e riavviare prima l'HOST.", MISMATCH);
        if (expected != null && !expected.equals(actual))
            throw new SQLException("Il database trovato è diverso da quello della sessione. Verificare la cartella condivisa.", MISMATCH);
        return actual;
    }
}

package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

class PowderSupplierSchemaTest {
    @Test
    void migratesV11OnceWithoutChangingExistingPowders() throws Exception {
        String url = "jdbc:derby:memory:supplier_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        try {
            new DatabaseSchema(db).initialize();
            db.withTransaction(
                    c -> {
                        SchemaV12Fixture.removeCrm(c);
                        try (var q = c.createStatement()) {
                            q.executeUpdate("ALTER TABLE TON.POLVERI DROP COLUMN FORNITORE");
                            q.executeUpdate(
                                    "DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=12");
                            q.executeUpdate(
                                    "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES"
                                        + " ('OLD','Esistente','BIANCA')");
                        }
                        return null;
                    });
            AtomicInteger backups = new AtomicInteger();
            new DatabaseSchema(db)
                    .initialize(
                            c -> {
                                backups.incrementAndGet();
                                return null;
                            });
            new DatabaseSchema(db)
                    .initialize(
                            c -> {
                                backups.incrementAndGet();
                                return null;
                            });
            assertEquals(1, backups.get());
            db.withConnection(
                    c -> {
                        c.setReadOnly(true);
                        DatabaseSchema.verifyCompatible(c);
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT codice,fornitore FROM TON.POLVERI")) {
                            assertTrue(r.next());
                            assertEquals("OLD", r.getString(1));
                            assertNull(r.getString(2));
                        }
                        return null;
                    });
            db.withTransaction(
                    c -> {
                        c.createStatement()
                                .executeUpdate("ALTER TABLE TON.POLVERI DROP COLUMN FORNITORE");
                        return null;
                    });
            db.withConnection(
                    c -> {
                        c.setReadOnly(true);
                        assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(c));
                        return null;
                    });
        } finally {
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException expected) {
                assertEquals("08006", expected.getSQLState());
            }
        }
    }
}

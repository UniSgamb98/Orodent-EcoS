package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class CrmSchemaTest {
    @Test
    void upgradesV12OnceAndLeavesOtherDomainsUntouched() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url =
                "jdbc:derby:memory:crm_schema_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        try {
            new DatabaseSchema(db).initialize();
            db.withTransaction(
                    c -> {
                        SchemaV12Fixture.removeCrm(c);
                        c.createStatement()
                                .executeUpdate(
                                        "INSERT INTO TON.POLVERI(codice,nome) VALUES"
                                            + " ('KEEP','Conservata')");
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
                                var r = q.executeQuery("SELECT codice FROM TON.POLVERI")) {
                            assertTrue(r.next());
                            assertEquals("KEEP", r.getString(1));
                        }
                        try (var q = c.createStatement();
                                var r = q.executeQuery("SELECT COUNT(*) FROM ECOS.OPERATORI")) {
                            r.next();
                            assertEquals(1, r.getInt(1));
                        }
                        return null;
                    });
            db.withTransaction(
                    c -> {
                        c.createStatement()
                                .executeUpdate(
                                        "ALTER TABLE CRM.CLIENTI DROP COLUMN CODICE_GESTIONALE");
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
            } catch (SQLException e) {
                assertEquals("08006", e.getSQLState());
            }
        }
    }
}

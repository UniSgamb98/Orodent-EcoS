package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.SupplierCatalogue.*;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

class SupplierCatalogueTest {
    String url;
    ConnectionProvider db;

    @BeforeEach
    void open() {
        url = "jdbc:derby:memory:suppliers_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
    }

    @AfterEach
    void close() throws SQLException {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    @Test
    void migrationMergesSpellingVariantsAcrossPowdersAndOrdersAndRetainsSources() {
        db.withTransaction(
                c -> {
                    SupplierFixtures.beforeV22(c);
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO TON.POLVERI(codice,nome,fornitore) VALUES"
                                    + " ('P1','One',' Tosoh  Japan '),('P2','Two','TOSOH"
                                    + " JAPAN'),('P3','Blank','  ')");
                        q.executeUpdate(
                                "INSERT INTO"
                                    + " COSTI.ORDINI_POLVERI(fornitore,numero_ordine,posizione)"
                                    + " VALUES ('tosoh Japan','001',1),('Sinocera','002',1)");
                    }
                    return null;
                });
        new DatabaseSchema(db).initialize();
        new DatabaseSchema(db).initialize();
        db.withConnection(
                c -> {
                    DatabaseSchema.verifyCompatible(c);
                    assertEquals(2, SupplierCatalogue.list(c, "", true).size());
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT COUNT(DISTINCT fornitore_id) FROM (SELECT"
                                                + " fornitore_id FROM TON.POLVERI WHERE"
                                                + " codice<>'P3' UNION ALL SELECT fornitore_id FROM"
                                                + " COSTI.ORDINI_POLVERI WHERE numero_ordine='001')"
                                                + " x")) {
                        r.next();
                        assertEquals(1, r.getInt(1));
                    }
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT fornitore,fornitore_id FROM TON.POLVERI WHERE"
                                                + " codice='P1'")) {
                        r.next();
                        assertEquals(" Tosoh  Japan ", r.getString(1));
                        assertNotNull(r.getObject(2));
                    }
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT fornitore_id FROM TON.POLVERI WHERE"
                                                + " codice='P3'")) {
                        r.next();
                        assertNull(r.getObject(1));
                    }
                    assertTrue(
                            SupplierCatalogue.list(c, "", true).stream()
                                    .allMatch(e -> e.code().startsWith("AUTO-")));
                    return null;
                });
    }

    @Test
    void renameKeepsIdentitySourceAliasesAndCanonicalDisplay() {
        Entry a =
                db.withTransaction(
                        c -> SupplierCatalogue.save(c, null, new Draft("TS", "Tosoh", true)));
        db.withTransaction(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO TON.POLVERI(codice,nome,fornitore,fornitore_id)"
                                        + " VALUES ('P','Powder','Tosoh',?)")) {
                        q.setLong(1, a.id());
                        q.executeUpdate();
                    }
                    return null;
                });
        Entry renamed =
                db.withTransaction(
                        c -> SupplierCatalogue.save(c, a, new Draft("TOS", "Tosoh Japan", true)));
        db.withTransaction(
                c -> {
                    assertEquals(a.id(), SupplierCatalogue.resolve(c, " TOSOH "));
                    assertEquals(a.id(), SupplierCatalogue.resolve(c, "tosoh   japan"));
                    assertEquals(1, SupplierCatalogue.list(c, "", true).size());
                    assertEquals(
                            "Tosoh Japan",
                            PowderDisplayCatalogue.read(c).identity(1, "").supplier());
                    assertEquals(
                            renamed,
                            List.copyOf(SupplierCatalogue.list(c, "tos", false)).getFirst());
                    return null;
                });
    }

    @Test
    void archiveExcludesNewAssignmentsButRetainsExistingAndConflictsDoNotOverwrite() {
        Entry a =
                db.withTransaction(
                        c -> SupplierCatalogue.save(c, null, new Draft("TS", "Tosoh", true)));
        Entry archived =
                db.withTransaction(
                        c -> SupplierCatalogue.save(c, a, new Draft("TS", "Tosoh", false)));
        assertThrows(
                Conflict.class,
                () ->
                        db.withTransaction(
                                c ->
                                        SupplierCatalogue.save(
                                                c, a, new Draft("OLD", "Stale", true))));
        db.withConnection(
                c -> {
                    assertTrue(SupplierCatalogue.list(c, "", false).isEmpty());
                    assertEquals(archived, SupplierCatalogue.load(c, a.id()));
                    return null;
                });
        db.withTransaction(
                c -> {
                    assertEquals(a.id(), SupplierCatalogue.assignment(c, a.id(), "", a.id()));
                    return null;
                });
        assertThrows(
                IllegalArgumentException.class,
                () -> db.withTransaction(c -> SupplierCatalogue.assignment(c, a.id(), "", null)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.withTransaction(
                                c ->
                                        SupplierCatalogue.save(
                                                c, null, new Draft("OTHER", "tosoh", true))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.withTransaction(
                                c ->
                                        SupplierCatalogue.save(
                                                c, null, new Draft("ts", "Other", true))));
    }

    @Test
    void missingSupplierColumnFailsClientVerification() {
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "ALTER TABLE COSTI.ORDINI_POLVERI DROP CONSTRAINT"
                                    + " fk_costi_ordini_fornitore");
                        q.executeUpdate(
                                "ALTER TABLE COSTI.ORDINI_POLVERI DROP COLUMN fornitore_id");
                    }
                    return null;
                });
        assertThrows(
                RuntimeException.class,
                () ->
                        db.withConnection(
                                c -> {
                                    DatabaseSchema.verifyCompatible(c);
                                    return null;
                                }));
    }
}

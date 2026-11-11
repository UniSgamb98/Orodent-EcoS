package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.costi.orders.*;
import com.orodent.ecos.costi.orders.PowderOrders.*;
import com.orodent.ecos.database.DatabaseSchema;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletionException;

class OrdersTest {
    @TempDir Path dir;
    ConnectionProvider db;
    OrderService service;
    String url, oldLog;
    static final String HEADER =
            "Cons. prev.;Data ordine;Ordine;Pos.;Fornitore;Articolo;Descrizione;Un."
                    + " Mis.;Quantita';UnitValue;Consegnata;Da consegn.\n";
    static final String A =
            "10/12/2026;14/07/2026;26/00034;1;2600266;ZST5Y-025OP;Zirconia Powder T5Y-025OP"
                    + " White;KG.;300;55;0;300\n";
    static final String B =
            "10/12/2026;14/07/2026;26/00034;2;2600266;ZST4Y-015OP;Zirconia Powder T4Y-015OP"
                    + " White;KG.;300;45;0;300\n";

    @BeforeEach
    void setup() throws Exception {
        oldLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/orders-tests.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:orders" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        db.withTransaction(
                c -> {
                    c.createStatement()
                            .executeUpdate(
                                    "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES"
                                        + " ('ZST5Y-025OP','Bianca"
                                        + " 5Y','BIANCA'),('ZST4Y-015OP','Bianca 4Y','BIANCA')");
                    return null;
                });
        service = new OrderService(db, Runnable::run);
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException ignored) {
        }
        if (oldLog == null) System.clearProperty("derby.stream.error.file");
        else System.setProperty("derby.stream.error.file", oldLog);
    }

    Preview preview(String text) throws Exception {
        return service.preview(
                        Files.writeString(dir.resolve("orders.csv"), text), StandardCharsets.UTF_8)
                .join();
    }

    @Test
    void databaseFailureOnSecondLineRollsBackFirstLineAndAliases() throws Exception {
        db.withTransaction(
                c -> {
                    c.createStatement()
                            .executeUpdate(
                                    "ALTER TABLE COSTI.ORDINI_POLVERI ADD CONSTRAINT test_position"
                                            + " CHECK (posizione<2)");
                    return null;
                });
        var p = preview(HEADER + A + B);
        assertThrows(CompletionException.class, () -> service.apply(p, p.mappings()).join());
        assertTrue(service.list().join().rows().isEmpty());
        assertTrue(db.withConnection(c -> new OrderRepository(c).aliases()).isEmpty());
    }

    @Test
    void exactSampleIsIdempotentAndPartialDeliveryRetainsManualDecisions() throws Exception {
        var p = preview("\ufeff" + HEADER + A + B);
        assertTrue(p.valid());
        assertTrue(p.powders().stream().allMatch(powder -> "BIANCA".equals(powder.color())));
        assertEquals(2, p.mappings().size());
        assertEquals(new Report(2, 0, 0), service.apply(p, p.mappings()).join());
        p = preview(HEADER + A + B);
        assertEquals(new Report(0, 0, 2), service.apply(p, p.mappings()).join());
        var row = service.list().join().rows().getFirst();
        service.edit(row, LocalDate.of(2026, 12, 20), true).join();
        p = preview(HEADER + A.replace(";0;300", ";50;250"));
        assertEquals(new Report(0, 1, 0), service.apply(p, p.mappings()).join());
        var rows = service.list().join().rows();
        assertEquals(2, rows.size());
        row = rows.getFirst();
        assertEquals(0, new java.math.BigDecimal("250").compareTo(row.row().remaining()));
        assertTrue(row.excluded());
        assertEquals(LocalDate.of(2026, 12, 20), row.date());
        assertEquals(3, row.version());
        assertEquals(
                0,
                db.withConnection(
                                c -> {
                                    var r =
                                            c.createStatement()
                                                    .executeQuery(
                                                            "SELECT COUNT(*) FROM"
                                                                    + " COSTI.POLVERI_PREZZI");
                                    r.next();
                                    return r.getInt(1);
                                })
                        .intValue());
    }

    @Test
    void stalePreviewRollsBackWholeImportAndUnknownCodeRequiresMapping() throws Exception {
        var stale = preview(HEADER + A + B);
        var current = preview(HEADER + A);
        service.apply(current, current.mappings()).join();
        assertInstanceOf(
                Conflict.class,
                assertThrows(
                                CompletionException.class,
                                () -> service.apply(stale, stale.mappings()).join())
                        .getCause());
        assertEquals(1, service.list().join().rows().size());
        var unknown = preview(HEADER + B.replace("ZST4Y-015OP", "SUPPLIER-ALIAS"));
        assertTrue(unknown.valid());
        assertFalse(
                unknown.mappings()
                        .containsKey(unknown.csv().validRows().getFirst().value().source()));
        assertThrows(CompletionException.class, () -> service.apply(unknown, Map.of()).join());
        service.apply(unknown, Map.of(unknown.csv().validRows().getFirst().value().source(), 2L))
                .join();
        assertEquals(
                2L,
                preview(HEADER + B.replace("ZST4Y-015OP", "SUPPLIER-ALIAS"))
                        .mappings()
                        .get(new Code("2600266", "SUPPLIER-ALIAS")));
    }

    @Test
    void invalidUnitDateQuantitiesAndDuplicatesCannotBeApplied() throws Exception {
        for (String bad :
                List.of(
                        A.replace("KG.", "G."),
                        A.replace("10/12/2026", "31/02/2026"),
                        A.replace(";0;300", ";10;300"),
                        A.replace(";55;", ";55,12345;"))) {
            var p = preview(HEADER + bad);
            assertFalse(p.valid());
            assertThrows(CompletionException.class, () -> service.apply(p, p.mappings()).join());
        }
        assertFalse(preview(HEADER + A + A).valid());
        assertTrue(preview(HEADER + A.replace(";55;", ";55,25;")).valid());
        assertTrue(service.list().join().rows().isEmpty());
    }
}

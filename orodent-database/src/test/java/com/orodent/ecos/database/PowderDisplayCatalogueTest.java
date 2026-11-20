package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.core.PowderAppearance;

import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

class PowderDisplayCatalogueTest {
    @Test
    void sqlPagingAndInMemoryOrderAgreeAndUseExactOrLatestApprovedY() throws Exception {
        String url =
                "jdbc:derby:memory:powder_display_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        try {
            db.withTransaction(
                    c -> {
                        try (var q = c.createStatement()) {
                            q.executeUpdate(
                                    "INSERT INTO TON.POLVERI(codice,nome,colore,fornitore) VALUES"
                                        + " ('A6','Six','BIANCA','Tosoh'),('Z3','Three','BIANCA','"
                                        + " tosoh '),"
                                        + " ('C4','Four','BIANCA','Tosoh'),('B4','Four','BIANCA','Tosoh'),"
                                        + " ('Y3','Yellow','GIALLA','Tosoh'),('R3','Pink','ROSA','Tosoh'),"
                                        + " ('G3','Gray','GRIGIA','Tosoh'),('FIRST','Supplier"
                                        + " first','GRIGIA','Sinocera'), ('NULLY','Unknown"
                                        + " Y','BIANCA','Tosoh'),('LEGACY','Unknown','BIANCA',NULL)");
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.POLVERI_REVISIONI(polvere_id,numero_versione,classe_ittria_y,stato,approvato_il)"
                                        + " SELECT polvere_id,1,CASE codice WHEN 'A6' THEN 6 WHEN"
                                        + " 'B4' THEN 4 WHEN 'C4' THEN 4 WHEN 'NULLY' THEN NULL"
                                        + " ELSE 3 END,'APPROVATA',CURRENT_TIMESTAMP FROM"
                                        + " TON.POLVERI");
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.POLVERI_REVISIONI(polvere_id,numero_versione,classe_ittria_y)"
                                        + " VALUES (2,2,6)");
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.POLVERI_REVISIONI(polvere_id,numero_versione,classe_ittria_y,stato,approvato_il)"
                                        + " VALUES (1,2,5,'APPROVATA',CURRENT_TIMESTAMP)");
                        }
                        return null;
                    });
            var catalogue = db.withConnection(PowderDisplayCatalogue::read);
            var expected =
                    List.of("FIRST", "Z3", "B4", "C4", "A6", "NULLY", "Y3", "R3", "G3", "LEGACY");
            assertEquals(
                    expected,
                    catalogue.identities().values().stream()
                            .sorted(PowderAppearance.order())
                            .map(PowderAppearance::code)
                            .toList());
            assertEquals(5, catalogue.identity(1, "").yttria());
            assertEquals(6, catalogue.version(1, 1, "").yttria());
            assertEquals(
                    3,
                    catalogue.identity(2, "").yttria(),
                    "A newer draft must not replace the approved display class");
            assertNull(catalogue.identity(9, "").yttria());
            for (int offset = 0; offset < expected.size(); offset += 3) {
                final int start = offset;
                var page =
                        db.withConnection(
                                c -> {
                                    var rows = new ArrayList<String>();
                                    try (var q =
                                            c.prepareStatement(
                                                    "SELECT p.codice FROM TON.POLVERI p"
                                                            + PowderDisplayCatalogue.latestJoin(
                                                                    "p", "py_display")
                                                            + " ORDER BY "
                                                            + PowderDisplayCatalogue.sqlOrder(
                                                                    "p",
                                                                    "py_display.classe_ittria_y")
                                                            + " OFFSET ? ROWS FETCH NEXT 3 ROWS"
                                                            + " ONLY")) {
                                        q.setInt(1, start);
                                        try (var r = q.executeQuery()) {
                                            while (r.next()) rows.add(r.getString(1));
                                        }
                                    }
                                    return rows;
                                });
                assertEquals(expected.subList(start, Math.min(start + 3, expected.size())), page);
            }
            assertEquals("#f6f6f0", catalogue.identity(1, "").background());
            assertEquals("#fff1b8", catalogue.identity(5, "").background());
            assertEquals("#fbe1e8", catalogue.identity(6, "").background());
            assertEquals("#e3e7ed", catalogue.identity(7, "").background());
        } finally {
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException expected) {
                assertEquals("08006", expected.getSQLState());
            }
        }
    }
}

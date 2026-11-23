package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.CubatureRuleCatalogue.*;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.UUID;

class CubatureRuleCatalogueTest {
    String url;
    ConnectionProvider db;

    @BeforeEach
    void setup() {
        url = "jdbc:derby:memory:cubature_" + UUID.randomUUID().toString().replace("-", "");
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
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException expected) {
            assertEquals("08006", expected.getSQLState());
        }
    }

    private void specification(Connection c, String code, int version) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.CONFIGURAZIONI_PRODOTTO(prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo)"
                            + " VALUES (1,?,1,1,?)")) {
            q.setInt(1, version);
            q.setString(2, code);
            q.executeUpdate();
        }
    }

    private void technical(Connection c) throws SQLException {
        try (var q = c.createStatement()) {
            q.executeUpdate("INSERT INTO TON.LINEE(codice,nome) VALUES ('L','Linea')");
            q.executeUpdate(
                    "INSERT INTO TON.PRODOTTI(codice,descrizione,linea_id,colore_commerciale)"
                        + " VALUES ('P','Prodotto',1,'A0')");
            q.executeUpdate(
                    "INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione) VALUES (1,1)");
            q.executeUpdate(
                    "INSERT INTO"
                        + " TON.MODELLI_DISCO(codice,numero_versione,diametro_pressatura_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm,pressione_kg_cm2,grammi_per_mm)"
                        + " VALUES ('M',1,99,1,1,1500,25)");
        }
    }

    @Test
    void migratesExistingReferencesWithoutRewritingSpecificationsAndIsIdempotent() {
        db.withTransaction(
                c -> {
                    CubatureRuleFixtures.beforeV26(c);
                    technical(c);
                    specification(c, "metodo-1", 1);
                    specification(c, "metodo-1", 2);
                    specification(c, "metodo-2", 3);
                    return null;
                });
        new DatabaseSchema(db).initialize();
        new DatabaseSchema(db).initialize();
        db.withConnection(
                c -> {
                    var rules = CubatureRuleCatalogue.list(c, "", true);
                    assertEquals(2, rules.size());
                    assertTrue(rules.stream().allMatch(Entry::used));
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT versione_calcolo,versione_modifica FROM"
                                                + " TON.CONFIGURAZIONI_PRODOTTO ORDER BY"
                                                + " numero_versione")) {
                        assertTrue(r.next());
                        assertEquals("metodo-1", r.getString(1));
                        assertEquals(1, r.getInt(2));
                        assertTrue(r.next());
                        assertEquals("metodo-1", r.getString(1));
                        assertTrue(r.next());
                        assertEquals("metodo-2", r.getString(1));
                    }
                    assertThrows(SQLException.class, () -> specification(c, "non-registrata", 4));
                    return null;
                });
    }

    @Test
    void checksDuplicatesConcurrentEditsUsedCodesAndArchivedAssignments() {
        Entry rule =
                db.withTransaction(
                        c ->
                                CubatureRuleCatalogue.save(
                                        c,
                                        null,
                                        new Draft("R1", "Prima regola", "Descrizione", true)));
        Entry changed =
                db.withTransaction(
                        c ->
                                CubatureRuleCatalogue.save(
                                        c,
                                        rule,
                                        new Draft("R1", "Nome corretto", "Descrizione", true)));
        assertThrows(
                Conflict.class,
                () ->
                        db.withTransaction(
                                c ->
                                        CubatureRuleCatalogue.save(
                                                c, rule, new Draft("R1", "Obsoleta", "", true))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.withTransaction(
                                c ->
                                        CubatureRuleCatalogue.save(
                                                c, null, new Draft("R1", "Duplicata", "", true))));
        db.withTransaction(
                c -> {
                    technical(c);
                    specification(c, "R1", 1);
                    CubatureRuleCatalogue.requireActive(c, "R1");
                    return null;
                });
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.withTransaction(
                                c ->
                                        CubatureRuleCatalogue.save(
                                                c,
                                                changed,
                                                new Draft(
                                                        "R2",
                                                        changed.name(),
                                                        changed.description(),
                                                        true))));
        db.withTransaction(
                c ->
                        CubatureRuleCatalogue.save(
                                c,
                                changed,
                                new Draft("R1", changed.name(), changed.description(), false)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.withTransaction(
                                c -> {
                                    CubatureRuleCatalogue.requireActive(c, "R1");
                                    return null;
                                }));
        db.withConnection(
                c -> {
                    assertTrue(CubatureRuleCatalogue.list(c, "", false).isEmpty());
                    assertEquals(1, CubatureRuleCatalogue.list(c, "", true).size());
                    return null;
                });
    }
}

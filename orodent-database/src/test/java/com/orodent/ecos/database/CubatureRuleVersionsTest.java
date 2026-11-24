package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.CubatureRuleCatalogue.*;
import com.orodent.ecos.database.CubatureRuleVersions.*;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;

class CubatureRuleVersionsTest {
    String url;
    ConnectionProvider db;

    @BeforeEach
    void setup() {
        url = "jdbc:derby:memory:formula_versions_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException failure) {
                        throw new IllegalStateException(failure);
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

    @Test
    void everySaveIsRetainedAndNewApprovalsNeverReplacePinnedResults() {
        Draft metadata = new Draft("CALC", "Prima", "Descrizione originale", true);
        Detail first =
                db.withTransaction(
                        c ->
                                CubatureRuleVersions.save(
                                        c, null, null, metadata, "D + Hmedia = a1", false));
        Detail approved =
                db.withTransaction(
                        c ->
                                CubatureRuleVersions.save(
                                        c,
                                        first.rule(),
                                        first.selected(),
                                        metadata,
                                        "D + Hmedia = a1\na1 * P = finale",
                                        true));
        assertEquals(1, approved.selected().number());
        assertEquals(2, approved.selected().revision());
        assertEquals(2, approved.versions().size());
        assertFalse(approved.selected().editable());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.withTransaction(
                                c ->
                                        CubatureRuleVersions.save(
                                                c,
                                                approved.rule(),
                                                approved.selected(),
                                                metadata,
                                                "D = x",
                                                false)));
        assertThrows(
                Conflict.class,
                () ->
                        db.withTransaction(
                                c ->
                                        CubatureRuleVersions.save(
                                                c,
                                                first.rule(),
                                                first.selected(),
                                                metadata,
                                                "D = x",
                                                false)));
        db.withTransaction(
                c -> {
                    technical(c);
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " TON.CONFIGURAZIONI_PRODOTTO(prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,calcolo_versione_id)"
                                        + " VALUES (1,1,1,1,'CALC',?)")) {
                        q.setLong(1, approved.selected().id());
                        q.executeUpdate();
                    }
                    return null;
                });
        Detail copied =
                db.withTransaction(
                        c -> CubatureRuleVersions.copy(c, approved.rule(), approved.selected()));
        assertEquals(2, copied.selected().number());
        assertTrue(copied.selected().editable());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.withTransaction(
                                c ->
                                        CubatureRuleVersions.copy(
                                                c, copied.rule(), approved.selected())));
        Draft changed = new Draft("CALC", "Seconda", "Descrizione nuova", true);
        Detail second =
                db.withTransaction(
                        c ->
                                CubatureRuleVersions.save(
                                        c,
                                        copied.rule(),
                                        copied.selected(),
                                        changed,
                                        "D * P = diverso",
                                        true));
        db.withConnection(
                c -> {
                    Version old = CubatureRuleVersions.load(c, approved.selected().id());
                    assertEquals("Prima", old.name());
                    assertEquals("Descrizione originale", old.description());
                    assertEquals("D + Hmedia = a1\na1 * P = finale", old.formulas());
                    assertEquals(
                            "D + Hmedia = a1",
                            CubatureRuleVersions.load(c, first.selected().id()).formulas());
                    assertEquals(
                            approved.selected().id(),
                            CubatureRuleVersions.assignment(c, "CALC", approved.selected().id())
                                    .id());
                    assertEquals(
                            second.selected().id(),
                            CubatureRuleVersions.assignment(c, "CALC", null).id());
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT calcolo_versione_id FROM"
                                                + " TON.CONFIGURAZIONI_PRODOTTO")) {
                        assertTrue(r.next());
                        assertEquals(old.id(), r.getLong(1));
                    }
                    assertEquals(4, CubatureRuleVersions.list(c, approved.rule().id()).size());
                    return null;
                });
    }

    @Test
    void invalidApprovalRollsBackAndDraftsRemainAvailableForCorrection() {
        var metadata = new Draft("C", "Calcolo", "", true);
        Detail draft =
                db.withTransaction(
                        c ->
                                CubatureRuleVersions.save(
                                        c, null, null, metadata, "non valida", false));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.withTransaction(
                                c ->
                                        CubatureRuleVersions.save(
                                                c,
                                                draft.rule(),
                                                draft.selected(),
                                                metadata,
                                                "non valida",
                                                true)));
        db.withConnection(
                c -> {
                    assertEquals(1, CubatureRuleVersions.list(c, draft.rule().id()).size());
                    assertEquals(
                            draft.rule().revision(),
                            CubatureRuleCatalogue.load(c, draft.rule().id()).revision());
                    return null;
                });
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.withTransaction(
                                c ->
                                        CubatureRuleVersions.assignment(
                                                c, "C", draft.selected().id())));
        assertThrows(
                IllegalArgumentException.class,
                () -> db.withTransaction(c -> CubatureRuleVersions.assignment(c, "C", null)));
    }

    @Test
    void migrationPinsExistingReferencesWithoutInventingFormulasOrChangingSnapshots() {
        db.withTransaction(
                c -> {
                    CubatureVersionFixtures.beforeV27(c);
                    technical(c);
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO TON.REGOLE_CUBATURA(codice,nome,versione_modifica)"
                                    + " VALUES ('OLD','Nome esistente',4)");
                        q.executeUpdate(
                                "INSERT INTO"
                                    + " TON.CONFIGURAZIONI_PRODOTTO(prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,versione_modifica)"
                                    + " VALUES (1,1,1,1,'OLD',7)");
                    }
                    return null;
                });
        new DatabaseSchema(db).initialize();
        new DatabaseSchema(db).initialize();
        db.withConnection(
                c -> {
                    var rules = CubatureRuleCatalogue.list(c, "", true);
                    var detail = CubatureRuleVersions.detail(c, rules.getFirst().id(), null);
                    assertEquals("STORICA", detail.selected().state());
                    assertEquals("", detail.selected().formulas());
                    assertEquals(4, detail.selected().revision());
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT"
                                                + " versione_calcolo,versione_modifica,calcolo_versione_id"
                                                + " FROM TON.CONFIGURAZIONI_PRODOTTO")) {
                        assertTrue(r.next());
                        assertEquals("OLD", r.getString(1));
                        assertEquals(7, r.getInt(2));
                        assertEquals(detail.selected().id(), r.getLong(3));
                    }
                    return null;
                });
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
}

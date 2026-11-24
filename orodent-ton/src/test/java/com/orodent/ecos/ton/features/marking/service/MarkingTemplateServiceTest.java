package com.orodent.ecos.ton.features.marking.service;

import static com.orodent.ecos.ton.features.marking.model.MarkingTemplates.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.*;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletionException;

class MarkingTemplateServiceTest {
    ConnectionProvider db;
    String url;
    MarkingTemplateService service;
    CubatureRuleVersions.Detail rule;

    @BeforeEach
    void setup() {
        url = "jdbc:derby:memory:marking_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException failure) {
                        throw new IllegalStateException(failure);
                    }
                };
        new DatabaseSchema(db).initialize();
        new DatabaseSchema(db).initialize();
        service = new MarkingTemplateService(db, Runnable::run);
        rule =
                db.withTransaction(
                        c ->
                                CubatureRuleVersions.save(
                                        c,
                                        null,
                                        null,
                                        new CubatureRuleCatalogue.Draft(
                                                "CALC", "Calcolo", "", true),
                                        "D / Hmedia = fattore",
                                        true));
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException expected) {
            assertEquals("08006", expected.getSQLState());
        }
    }

    Draft draft(String text, int places) {
        return new Draft(
                "DORSO",
                "Dorso standard",
                "Descrizione",
                true,
                text,
                rule.selected().id(),
                Map.of("fattore", places),
                ".");
    }

    @Test
    void preservesDraftSavesApprovedTextDecimalsAndTheExactRuleVersion() {
        var first =
                service.save(null, null, draft("{codice_articolo} R {fattore}", 2), false).join();
        var approved =
                service.save(
                                first.entry(),
                                first.selected(),
                                draft("{codice_articolo} LOT {lotto} R {fattore}", 3),
                                true)
                        .join();
        assertEquals(2, approved.versions().size());
        assertFalse(approved.selected().editable());
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(
                                        approved.entry(),
                                        approved.selected(),
                                        draft("MODIFICA", 2),
                                        false)
                                .join());
        var copy = service.copy(approved.entry(), approved.selected()).join();
        assertEquals(2, copy.selected().number());
        assertThrows(
                CompletionException.class,
                () -> service.copy(copy.entry(), approved.selected()).join());
        var second =
                service.save(copy.entry(), copy.selected(), draft("NUOVO {fattore}", 5), true)
                        .join();
        var old = service.load(second.entry().id(), first.selected().id()).join();
        assertEquals("{codice_articolo} R {fattore}", old.selected().text());
        assertEquals(2, old.selected().decimals().get("fattore"));
        assertFalse(old.selected().editable());
        var oldApproved = service.load(second.entry().id(), approved.selected().id()).join();
        assertEquals(3, oldApproved.selected().decimals().get("fattore"));
        var newRule =
                db.withTransaction(c -> CubatureRuleVersions.copy(c, rule.rule(), rule.selected()));
        db.withTransaction(
                c ->
                        CubatureRuleVersions.save(
                                c,
                                newRule.rule(),
                                newRule.selected(),
                                new CubatureRuleCatalogue.Draft("CALC", "Calcolo nuovo", "", true),
                                "D * Hmedia = fattore",
                                true));
        assertEquals(
                rule.selected().id(),
                service.load(second.entry().id(), null).join().selected().ruleId());
        assertEquals(4, second.versions().size());
        assertEquals(1, service.list("standard", false).join().size());
    }

    @Test
    void failedApprovalAndStaleWritesNeverAddSnapshotsOrOverwriteContent() {
        var saved = service.save(null, null, draft("{campo_inesistente}", 2), false).join();
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(
                                        saved.entry(),
                                        saved.selected(),
                                        draft("{campo_inesistente}", 2),
                                        true)
                                .join());
        assertEquals(1, service.load(saved.entry().id(), null).join().versions().size());
        var newer =
                service.save(saved.entry(), saved.selected(), draft("{fattore}", 4), false).join();
        assertThrows(
                CompletionException.class,
                () ->
                        service.save(
                                        saved.entry(),
                                        saved.selected(),
                                        draft("STATO VECCHIO", 2),
                                        false)
                                .join());
        assertEquals(
                newer.selected().id(),
                service.load(saved.entry().id(), null).join().selected().id());
        assertThrows(
                CompletionException.class,
                () -> service.save(null, null, draft("DUPLICATO", 2), false).join());
        assertEquals(1, service.list("", true).join().size());
    }
}

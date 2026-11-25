package com.orodent.ecos.ton.features.marking.model;

import static com.orodent.ecos.ton.features.marking.model.MarkingTemplates.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.database.CubatureRuleVersions.Version;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

class MarkingTemplatesTest {
    Version rule(String formulas) {
        return new Version(
                7,
                1,
                1,
                1,
                "APPROVATA",
                "CALC",
                "Calcolo",
                "",
                true,
                formulas,
                LocalDateTime.now(),
                true);
    }

    @Test
    void formatsEachResultWithoutChangingTheNumbersAndNeverInterpretsSampleText() {
        var rule = rule("D / Hmedia = fattore");
        var d =
                new Draft(
                        "M",
                        "Dorso",
                        "",
                        true,
                        "{codice_articolo} LOT {lotto} R {fattore} D {D}",
                        7L,
                        Map.of("fattore", 2, "D", 0),
                        ",");
        var precise = new BigDecimal("1.2356789");
        var values =
                Map.of(
                        "codice_articolo",
                        "ZR98$\\",
                        "lotto",
                        "{lotto originale}",
                        "fattore",
                        precise,
                        "D",
                        new BigDecimal("98"));
        assertEquals("ZR98$\\ LOT {lotto originale} R 1,24 D 98", render(d, rule, values));
        assertEquals("1.2356789", precise.toPlainString());
        assertEquals(Map.of("fattore", 2, "D", 0), formats(formatsJson(d.decimals())));
    }

    @Test
    void approvalRejectsUnknownFieldsBrokenTokensAndUnrelatedNumericFormats() {
        for (String text : List.of("{mancante}", "{D", "{{D}}", "{D+P}"))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> validate(new Draft("M", "M", "", true, text, null, Map.of(), "."), null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        validate(
                                new Draft("M", "M", "", true, "{D}", null, Map.of("lotto", 2), "."),
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Draft("M", "M", "", true, "{D}", null, Map.of("D", 9), "."));
        assertThrows(IllegalArgumentException.class, () -> fields(rule("D = lotto")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        validate(
                                new Draft("M", "M", "", true, "{fattore}", 8L, Map.of(), "."),
                                rule("D = fattore")));
    }

    @Test
    void rendersBatchAndArticleDataSeparatelyFromMeasurementsAndFormulaResults() {
        var rule = rule("D / Hmedia = fattore");
        var draft =
                new Draft(
                        "M",
                        "Dorso",
                        "",
                        true,
                        "{descrizione_articolo} LOT {lotto} Disco {numero_batch}"
                                + " Ø{diametro_nominale}x{altezza_nominale}"
                                + " misurato {D}x{Hmedia} R {fattore}",
                        7L,
                        Map.of(
                                "diametro_nominale",
                                0,
                                "altezza_nominale",
                                1,
                                "D",
                                2,
                                "Hmedia",
                                2,
                                "fattore",
                                2),
                        ",");
        var values = new HashMap<String, Object>();
        values.put("descrizione_articolo", "ORODENT THOR 98x16 COLOR: A2");
        values.put("lotto", "LOT-1");
        values.put("numero_batch", "0007");
        values.put("diametro_nominale", new BigDecimal("98"));
        values.put("altezza_nominale", new BigDecimal("16"));
        var measuredD = new BigDecimal("97.45");
        var measuredH = new BigDecimal("15.63");
        values.put("D", measuredD);
        values.put("Hmedia", measuredH);
        values.putAll(
                com.orodent.ecos.core.math.FormulaProgram.compile(rule.formulas())
                        .evaluate(measuredD, measuredH, new BigDecimal("400")));
        assertEquals(
                "ORODENT THOR 98x16 COLOR: A2 LOT LOT-1 Disco 0007"
                        + " Ø98x16,0 misurato 97,45x15,63 R 6,23",
                render(draft, rule, values));
        values.remove("numero_batch");
        assertThrows(IllegalArgumentException.class, () -> render(draft, rule, values));
        assertFalse(
                fields(null).stream()
                        .filter(f -> f.name().equals("numero_batch"))
                        .findFirst()
                        .orElseThrow()
                        .numeric());
        for (String reserved :
                List.of(
                        "numero_batch",
                        "descrizione_articolo",
                        "diametro_nominale",
                        "altezza_nominale"))
            assertThrows(IllegalArgumentException.class, () -> fields(rule("D = " + reserved)));
    }

    @Test
    void missingValuesFailInsteadOfPublishingUnresolvedTokens() {
        var d = new Draft("M", "M", "", true, "{codice_articolo} {D}", null, Map.of(), ".");
        assertThrows(
                IllegalArgumentException.class,
                () -> render(d, null, Map.of("codice_articolo", "A")));
        assertThrows(
                IllegalArgumentException.class,
                () -> render(d, null, Map.of("codice_articolo", "A", "D", "98")));
        assertEquals(
                "A 98.000",
                render(d, null, Map.of("codice_articolo", "A", "D", new BigDecimal("98"))));
    }
}

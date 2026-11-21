package com.orodent.ecos.ton.features.catalog.bulk;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.ton.features.catalog.bulk.model.CodeComposition;
import com.orodent.ecos.ton.features.catalog.bulk.model.CodeComposition.*;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

class CodeCompositionTest {
    @Test
    void defaultsUseOnlyLineColorAndFormat() {
        var v = new Values("ZRMST", "A0", new BigDecimal("95"), new BigDecimal("12.5"));
        assertEquals("ZRMSTA0", CodeComposition.defaults(CatalogKind.PRODUCTS).code(v));
        assertEquals("9512.5ZRMSTA0", CodeComposition.defaults(CatalogKind.ARTICLES).code(v));
        assertEquals(
                List.of(Type.DIAMETER, Type.HEIGHT, Type.PRODUCT, Type.TEXT),
                CodeComposition.available(CatalogKind.ARTICLES));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CodeComposition.defaults(CatalogKind.ARTICLES)
                                .code(new Values("MST", "A0", null, BigDecimal.ONE)));
    }

    @Test
    void fixedTextCanMoveAnExplicitPrefixWithoutRepeatingIt() {
        for (String prefix : List.of("ZR", "ZS")) {
            var rule =
                    new CodeComposition(
                            List.of(
                                    new Part(Type.TEXT, prefix),
                                    new Part(Type.DIAMETER),
                                    new Part(Type.HEIGHT),
                                    new Part(Type.PRODUCT)),
                            "");
            assertEquals(
                    prefix + "9512MSTA0",
                    rule.code(
                            new Values(
                                    prefix + "MST",
                                    "A0",
                                    new BigDecimal("95"),
                                    new BigDecimal("12"))));
            assertEquals(
                    prefix + "9512MSTA0",
                    rule.code(new Values("MST", "A0", new BigDecimal("95"), new BigDecimal("12"))));
        }
    }

    @Test
    void unrelatedOrNonLeadingLiteralsDoNotStripTheProduct() {
        var v = new Values("ZRMST", "Senza colore", BigDecimal.ONE, BigDecimal.TEN);
        var rule =
                new CodeComposition(List.of(new Part(Type.TEXT, "X"), new Part(Type.PRODUCT)), "-");
        assertEquals("X-ZRMST", rule.code(v));
        var suffix =
                new CodeComposition(
                        List.of(new Part(Type.PRODUCT), new Part(Type.TEXT, "ZR")), "-");
        assertEquals("ZRMST-ZR", suffix.code(v));
        var colors = new CodeComposition(List.of(new Part(Type.LINE), new Part(Type.COLOR)), "-");
        assertEquals("ZRMST", colors.code(v));
    }
}

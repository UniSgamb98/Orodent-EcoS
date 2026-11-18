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
    void composesCodesWithoutFormulaSyntaxAndSkipsUncoloredSeparators() {
        var v = new Values("ZR", "MST", "A3", new BigDecimal("98.00"), new BigDecimal("12.50"));
        assertEquals("MSTA3", CodeComposition.defaults(CatalogKind.PRODUCTS).code(v));
        assertEquals("ZR9812.5MSTA3", CodeComposition.defaults(CatalogKind.ARTICLES).code(v));
        var custom =
                new CodeComposition(
                        List.of(
                                new Part(Type.TEXT, "Disco"),
                                new Part(Type.LINE),
                                new Part(Type.COLOR)),
                        "-");
        assertEquals("DISCO-MST-A3", custom.code(v));
        assertEquals("DISCO-MST", custom.code(new Values("ZR", "MST", "Senza colore", null, null)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CodeComposition.defaults(CatalogKind.ARTICLES)
                                .code(new Values("", "MST", "A3", BigDecimal.ONE, BigDecimal.TEN)));
    }

    @Test
    void articleProductUsesLineAndColorWithoutRepeatingItsOwnMaterialPrefix() {
        var rule = CodeComposition.defaults(CatalogKind.ARTICLES);
        for (String material : List.of("ZR", "ZS")) {
            var values =
                    new Values(
                            material,
                            material + "MST",
                            "A0",
                            new BigDecimal("98"),
                            new BigDecimal("12"));
            assertEquals(material + "9812MSTA0", rule.code(values));
            assertEquals(
                    material + "MSTA0",
                    CodeComposition.defaults(CatalogKind.PRODUCTS).code(values));
        }
        assertEquals(
                "ZR9812MSTA3.5",
                rule.code(
                        new Values(
                                "ZR",
                                "ZRMST",
                                "A3.5",
                                new BigDecimal("98"),
                                new BigDecimal("12"))));
        assertEquals(
                "ZR9812MST",
                rule.code(
                        new Values(
                                "ZR",
                                "ZRMST",
                                "Senza colore",
                                new BigDecimal("98"),
                                new BigDecimal("12"))));
        assertEquals(
                "ZR9812MSTA0",
                rule.code(
                        new Values("ZR", "MST", "A0", new BigDecimal("98"), new BigDecimal("12"))));
    }

    @Test
    void stripsOnlyOneMatchingPrefixAtTheStartOfTheLine() {
        var part = new Part(Type.PRODUCT);
        assertEquals(
                "ZSMSTA0",
                CodeComposition.value(part, new Values("ZR", "ZSMST", "A0", null, null)));
        assertEquals(
                "MSTZRA0",
                CodeComposition.value(part, new Values("ZR", "MSTZR", "A0", null, null)));
        assertEquals(
                "ZRMSTA0",
                CodeComposition.value(part, new Values("ZR", "ZRZRMST", "A0", null, null)));
        assertEquals(
                "MSTA0", CodeComposition.value(part, new Values("zr", "zrmst", "a0", null, null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> CodeComposition.value(part, new Values("ZR", "", "A0", null, null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> CodeComposition.value(part, new Values("ZR", "ZR", "A0", null, null)));
    }
}

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
        var v =
                new Values(
                        "ZR",
                        "MST",
                        "A3",
                        "MSTA3",
                        new BigDecimal("98.00"),
                        new BigDecimal("12.50"));
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
        assertEquals(
                "DISCO-MST", custom.code(new Values("ZR", "MST", "Senza colore", "", null, null)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CodeComposition.defaults(CatalogKind.ARTICLES)
                                .code(
                                        new Values(
                                                "",
                                                "MST",
                                                "A3",
                                                "MSTA3",
                                                BigDecimal.ONE,
                                                BigDecimal.TEN)));
    }
}

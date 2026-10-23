package com.orodent.ecos.statistiche.features.sales.products.service;

import com.orodent.ecos.statistiche.features.sales.products.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductCodeParserTest {
    private final ProductCodeParser parser = new ProductCodeParser();

    @Test void recognizesTheRealFamiliesWithoutPrefixAmbiguities() {
        assertEquals(ProductFamily.MST, parser.parse("ZR9812MSTA2").orElseThrow().family());
        assertEquals(ProductFamily.MS, parser.parse("ZR9816MSA3").orElseThrow().family());
        assertEquals(ProductFamily.UMS, parser.parse("ZR9818UMSA2").orElseThrow().family());
        assertEquals(ProductFamily.T4, parser.parse("ZR9812T4").orElseThrow().family());
        assertEquals(ProductFamily.T, parser.parse("ZR9814T").orElseThrow().family());
        assertEquals(ProductFamily.T_COLORED, parser.parse("ZR9818TA35").orElseThrow().family());
        assertEquals(ProductFamily.G1200, parser.parse("ZR9818G1200").orElseThrow().family());
        assertEquals(ProductFamily.H, parser.parse("ZR9810H").orElseThrow().family());
    }
    @Test void extractsDimensionsAndNormalizesColorsIncludingA0() {
        assertEquals(new ProductAttributes("ZR", 95, 14, ProductFamily.MST, "A0"), parser.parse("ZR9514MSTA0").orElseThrow());
        assertEquals("A3.5", parser.parse("ZR9814MSTA35").orElseThrow().color());
        assertEquals("A3.5", parser.parse("ZR9814MSTA3.5").orElseThrow().color());
        assertEquals(new ProductAttributes("ZS", 98, 30, ProductFamily.MS, "D3"), parser.parse(" zs9830msd3 ").orElseThrow());
        for (String color : new String[]{"A0","A1","A2","A3","A35","A4","B1","B2","B3","C1","C2","C3","D2","D3"}) {
            assertTrue(parser.parse("ZR9818MST" + color).isPresent(), color);
        }
    }
    @Test void rejectsUnrelatedProductsAndInvalidOrIncompleteCodes() {
        for (String code : new String[]{"ZRYTZ1_50","ZRYTZ3_50","ZRPC","ZR9818MST","ZR9818MS","ZR9818UMS",
                "ZR9818HA2","ZR9818T4A2","ZR9818G1200A2","ZR9818MSTA5","ZR9818MSTD1",
                "ZR9618MSTA2","ZR9811MSTA2","ZR9818MSTA2_EXTRA",""}) {
            assertTrue(parser.parse(code).isEmpty(), code);
        }
        assertTrue(parser.parse(null).isEmpty());
    }
    @Test void groupingNeverMergesMaterialsOrTheTwoTFamilies() {
        var colored = parser.parse("ZR9818TA2").orElseThrow();
        var plain = parser.parse("ZR9818T").orElseThrow();
        assertNotEquals(ProductGroupKey.of(colored, ProductGrouping.FAMILY), ProductGroupKey.of(plain, ProductGrouping.FAMILY));
        var a2 = parser.parse("ZR9512MSTA2").orElseThrow();
        var a3 = parser.parse("ZR9825MSTA3").orElseThrow();
        assertEquals(ProductGroupKey.of(a2, ProductGrouping.FAMILY), ProductGroupKey.of(a3, ProductGrouping.FAMILY));
        assertNotEquals(ProductGroupKey.of(a2, ProductGrouping.FAMILY_COLOR), ProductGroupKey.of(a3, ProductGrouping.FAMILY_COLOR));
        assertNotEquals(ProductGroupKey.of(a2, ProductGrouping.FAMILY), ProductGroupKey.of(parser.parse("ZS9512MSTA2").orElseThrow(), ProductGrouping.FAMILY));
    }
}

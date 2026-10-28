package com.orodent.ecos.ton.features.compositions.service;

import static com.orodent.ecos.ton.features.compositions.model.Compositions.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class CompositionChemistryTest {
    @Test
    void incompleteRecipesAndIncompleteCoverageNeverGetRenormalised() {
        var refs = Map.of(1L, powder(1, 1, Map.of("Y2O3", n("5.15"))), 2L, powder(2, 2, Map.of()));
        Layer complete =
                new Layer(1, "", List.of(new Ingredient(1, n("75")), new Ingredient(2, n("25"))));
        var partial = CompositionChemistry.balance(complete, "Y2O3", refs);
        assertNull(partial.weightPercent());
        assertTrue(partial.completeRecipe());
        assertEquals(0, n("75").compareTo(partial.coverage()));
        Layer pending = new Layer(1, "", List.of(new Ingredient(1, n("75"))));
        var unfinished = CompositionChemistry.balance(pending, "Y2O3", refs);
        assertNull(unfinished.weightPercent());
        assertFalse(unfinished.completeRecipe());
    }

    @Test
    void massBalanceKeepsAllTenDecimalPlacesAndDistinguishesZeroFromMissing() {
        var refs =
                Map.of(
                        1L,
                        powder(1, 1, Map.of("Y2O3", n("0.0001"), "ZERO", BigDecimal.ZERO)),
                        2L,
                        powder(2, 2, Map.of("Y2O3", n("0.0002"), "ZERO", BigDecimal.ZERO)));
        Layer tiny =
                new Layer(
                        1,
                        "",
                        List.of(new Ingredient(1, n("0.0001")), new Ingredient(2, n("99.9999"))));
        assertEquals(
                0,
                n("0.0001999999")
                        .compareTo(
                                CompositionChemistry.balance(tiny, "Y2O3", refs).weightPercent()));
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        CompositionChemistry.balance(tiny, "ZERO", refs).weightPercent()));
        assertNull(CompositionChemistry.balance(tiny, "UNKNOWN", refs).weightPercent());
    }

    @Test
    void differentRevisionsOfTheSamePowderCannotLookLikeACompleteMixture() {
        var refs =
                Map.of(
                        1L,
                        powder(1, 1, Map.of("Y2O3", n("5.15"))),
                        2L,
                        powder(2, 1, Map.of("Y2O3", n("9.35"))));
        Layer duplicate =
                new Layer(1, "", List.of(new Ingredient(1, n("50")), new Ingredient(2, n("50"))));
        assertFalse(CompositionChemistry.complete(duplicate, refs));
        assertNull(CompositionChemistry.balance(duplicate, "Y2O3", refs).weightPercent());
    }

    private static PowderRef powder(long id, long family, Map<String, BigDecimal> oxides) {
        return new PowderRef(
                id,
                family,
                "P" + family,
                "Polvere",
                "BIANCA",
                6,
                (int) id,
                State.APPROVATA,
                true,
                oxides);
    }

    private static BigDecimal n(String s) {
        return new BigDecimal(s);
    }
}

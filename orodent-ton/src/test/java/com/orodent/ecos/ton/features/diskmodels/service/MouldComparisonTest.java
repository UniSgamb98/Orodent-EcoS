package com.orodent.ecos.ton.features.diskmodels.service;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.ton.features.diskmodels.model.DiskModels.Mould;

import org.junit.jupiter.api.Test;

import java.math.*;

class MouldComparisonTest {
    private Mould mould(long id, String d) {
        return new Mould(id, 1, "S" + id, "Stampo", new BigDecimal(d));
    }

    @Test
    void reportsExtraPowderForTheSameHeightAndDensityWithoutReplacingStandardWeights() {
        var difference =
                MouldComparison.between(mould(1, "99"), mould(2, "99.2"), new BigDecimal("25"));
        assertEquals(new BigDecimal("0.2"), difference.diameterMm());
        assertEquals(
                new BigDecimal("0.404449"),
                difference.sectionPercent().setScale(6, RoundingMode.HALF_UP));
        assertEquals(
                new BigDecimal("0.404449"),
                difference.extraGramsPer100g().setScale(6, RoundingMode.HALF_UP));
        assertEquals(
                new BigDecimal("0.101112"),
                difference.extraGramsPerMm().setScale(6, RoundingMode.HALF_UP));
    }

    @Test
    void smallerMouldsHaveNegativeDifferencesAndTheReferenceHasNone() {
        var reference = mould(1, "99");
        var small = MouldComparison.between(reference, mould(2, "98.8"), new BigDecimal("25"));
        assertTrue(small.sectionPercent().signum() < 0);
        assertTrue(small.extraGramsPerMm().signum() < 0);
        var same = MouldComparison.between(reference, reference, null);
        assertEquals(0, same.extraGramsPer100g().signum());
        assertEquals(0, same.diameterMm().signum());
        assertNull(same.extraGramsPerMm());
    }
}

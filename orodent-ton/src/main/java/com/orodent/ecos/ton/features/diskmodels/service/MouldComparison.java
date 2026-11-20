package com.orodent.ecos.ton.features.diskmodels.service;

import com.orodent.ecos.ton.features.diskmodels.model.DiskModels.Mould;

import java.math.*;

/** Geometric estimate at equal height/density; never changes model g/mm or production doses. */
public final class MouldComparison {
    private MouldComparison() {}

    public record Difference(
            BigDecimal diameterMm,
            BigDecimal sectionPercent,
            BigDecimal extraGramsPer100g,
            BigDecimal extraGramsPerMm) {}

    public static Difference between(Mould reference, Mould other, BigDecimal gramsPerMm) {
        BigDecimal ratio =
                other.diameter().pow(2).divide(reference.diameter().pow(2), MathContext.DECIMAL128);
        BigDecimal change = ratio.subtract(BigDecimal.ONE);
        return new Difference(
                other.diameter().subtract(reference.diameter()),
                change.movePointRight(2),
                change.multiply(new BigDecimal("100")),
                gramsPerMm == null ? null : change.multiply(gramsPerMm));
    }
}

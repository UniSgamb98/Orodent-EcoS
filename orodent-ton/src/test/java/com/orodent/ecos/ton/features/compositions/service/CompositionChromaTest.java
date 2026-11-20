package com.orodent.ecos.ton.features.compositions.service;

import static com.orodent.ecos.ton.features.compositions.model.Compositions.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class CompositionChromaTest {
    BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    ChromaPart p(long id, ChromaGroup g, String value) {
        return new ChromaPart(id, g, n(value));
    }

    void equal(String expected, BigDecimal actual) {
        assertEquals(0, n(expected).compareTo(actual));
    }

    @Test
    void fractionsAreConvertedExactlyAndWhitesRemainUserInputs() {
        var result =
                CompositionChroma.calculate(
                        new Chroma(
                                n("1000"),
                                List.of(
                                        p(1, ChromaGroup.BIANCA, "50"),
                                        p(2, ChromaGroup.GRIGIA, "1"),
                                        p(3, ChromaGroup.CROMATICA, "60"),
                                        p(4, ChromaGroup.CROMATICA, "40"))));
        equal("29.4", result.percentages().get(3L));
        equal("19.6", result.percentages().get(4L));
        equal("1", result.gray());
        equal("50", result.whiteTarget());
        equal("50", result.whiteActual());
        equal(
                "100",
                result.ingredients().stream()
                        .map(Ingredient::mass)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        var partial =
                CompositionChroma.calculate(
                        new Chroma(
                                n("1000"),
                                List.of(
                                        p(1, ChromaGroup.BIANCA, "10"),
                                        p(2, ChromaGroup.GRIGIA, "1"),
                                        p(3, ChromaGroup.CROMATICA, "100"))));
        equal("10", partial.percentages().get(1L));
        equal("50", partial.whiteTarget());
        equal(
                "60",
                partial.ingredients().stream()
                        .map(Ingredient::mass)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void impossiblePpmGrayOrRatiosAreRejectedWithoutNormalisingThem() {
        assertThrows(IllegalArgumentException.class, () -> new Chroma(n("10000"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Chroma(n("-1"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Chroma(n("0.001"), List.of()));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                CompositionChroma.calculate(
                                        new Chroma(
                                                n("19.99"),
                                                List.of(
                                                        p(2, ChromaGroup.GRIGIA, "1"),
                                                        p(3, ChromaGroup.CROMATICA, "100")))));
        assertTrue(error.getMessage().contains("20 ppm"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CompositionChroma.calculate(
                                new Chroma(
                                        n("1000"),
                                        List.of(
                                                p(3, ChromaGroup.CROMATICA, "60"),
                                                p(4, ChromaGroup.CROMATICA, "30")))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CompositionChroma.calculate(
                                new Chroma(n("1000"), List.of(p(1, ChromaGroup.BIANCA, "50")))));
    }

    @Test
    void endpointsAndZeroCandidatesRemainPossibleWithoutZeroPhysicalIngredients() {
        var zero =
                CompositionChroma.calculate(
                        new Chroma(
                                n("0"),
                                List.of(
                                        p(1, ChromaGroup.BIANCA, "100"),
                                        p(2, ChromaGroup.GRIGIA, "0"),
                                        p(3, ChromaGroup.CROMATICA, "100"))));
        assertEquals(1, zero.ingredients().size());
        equal("0", zero.percentages().get(3L));
        var full =
                CompositionChroma.calculate(
                        new Chroma(
                                n("2000"),
                                List.of(
                                        p(1, ChromaGroup.BIANCA, "0"),
                                        p(3, ChromaGroup.CROMATICA, "100"))));
        equal("100", full.percentages().get(3L));
        equal("0", full.whiteTarget());
        assertEquals(1, full.ingredients().size());
        var grayOnly =
                CompositionChroma.calculate(
                        new Chroma(
                                n("20"),
                                List.of(
                                        p(1, ChromaGroup.BIANCA, "99"),
                                        p(2, ChromaGroup.GRIGIA, "1"))));
        equal("0", grayOnly.chromatic());
    }

    @Test
    void fourDecimalRoundingPreservesBothPpmAndTheTotal() {
        var result =
                CompositionChroma.calculate(
                        new Chroma(
                                n("0.01"),
                                List.of(
                                        p(1, ChromaGroup.BIANCA, "99.9995"),
                                        p(2, ChromaGroup.CROMATICA, "33.3333"),
                                        p(3, ChromaGroup.CROMATICA, "33.3333"),
                                        p(4, ChromaGroup.CROMATICA, "33.3334"))));
        equal("0.0005", result.chromatic());
        equal(
                "100",
                result.ingredients().stream()
                        .map(Ingredient::mass)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        equal(
                "0.0005",
                result.percentages().entrySet().stream()
                        .filter(e -> e.getKey() != 1)
                        .map(Map.Entry::getValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(
                result,
                CompositionChroma.calculate(
                        new Chroma(
                                n("0.01"),
                                List.of(
                                        p(1, ChromaGroup.BIANCA, "99.9995"),
                                        p(2, ChromaGroup.CROMATICA, "33.3333"),
                                        p(3, ChromaGroup.CROMATICA, "33.3333"),
                                        p(4, ChromaGroup.CROMATICA, "33.3334")))));
    }

    @Test
    void ppmPrecisionRetainsTheExistingFourDecimalMassResolution() {
        var input =
                new Chroma(
                        n("6.666"),
                        List.of(
                                p(1, ChromaGroup.BIANCA, "99.6667"),
                                p(2, ChromaGroup.CROMATICA, "100")));
        var result = CompositionChroma.calculate(input);
        equal("0.3333", result.percentages().get(2L));
        equal(
                "100",
                result.ingredients().stream()
                        .map(Ingredient::mass)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}

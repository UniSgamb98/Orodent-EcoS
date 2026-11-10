package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.costi.products.model.NominalMixtureCost;
import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.products.service.ProductCostCalculator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

class ProductCostCalculatorTest {
    private final ProductCostCalculator calculator = new ProductCostCalculator();

    static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    private final Product product = new Product(1, "MSTA2", 1L, "MST", "Thor", "A2", true);
    private final Article article = new Article(1, 1, "MSTA2-98-10", n("98"), n("10"), true, 11L);
    private final Specification spec =
            new Specification(11, 1, 1, 1, 1, "TON-OLD", 1, n("98"), n("10"));
    private final Parameters parameters = new Parameters(1, n("12"), Map.of());

    private Model model(List<Range> ranges) {
        return new Model(
                1,
                "THOR",
                2,
                "APPROVATA",
                n("25"),
                n("1"),
                n("2"),
                Map.of(1, n("30"), 2, n("70")),
                ranges);
    }

    private Recipe recipe() {
        return new Recipe(
                1,
                3,
                "APPROVATA",
                Map.of(
                        1,
                                List.of(
                                        new Ingredient(1, "WHITE", 1, n("60")),
                                        new Ingredient(2, "YELLOW", 2, n("40"))),
                        2, List.of(new Ingredient(1, "WHITE", 1, n("100")))));
    }

    private Map<Long, PowderPrice> prices() {
        return Map.of(
                1L,
                new PowderPrice(1, 4, LocalDate.of(2026, 11, 9), "SEMPLICE", n("100")),
                2L,
                new PowderPrice(2, 2, LocalDate.of(2026, 11, 8), "AVANZATA", n("200")));
    }

    private Catalogue catalogue(Model model, Recipe recipe, Map<Long, PowderPrice> prices) {
        return new Catalogue(
                Instant.now(),
                List.of(product),
                List.of(article),
                Map.of(1L, List.of(spec)),
                Map.of(1L, recipe),
                Map.of(1L, model),
                prices,
                Map.of(1L, parameters));
    }

    private void decimal(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, n(expected).compareTo(actual));
    }

    @Test
    void productSummaryDoesNotHideDifferentNominalConfigurations() {
        var first = model(List.of());
        var second =
                new Model(
                        2,
                        "OTHER",
                        1,
                        "APPROVATA",
                        n("25"),
                        n("5"),
                        n("5"),
                        Map.of(1, n("50"), 2, n("50")),
                        List.of());
        var secondArticle = new Article(2, 1, "SECOND", n("98"), n("20"), true, 12L);
        var secondSpec = new Specification(12, 2, 1, 1, 2, "TON-OLD", 1, n("98"), n("20"));
        var c =
                new Catalogue(
                        Instant.now(),
                        List.of(product),
                        List.of(article, secondArticle),
                        Map.of(1L, List.of(spec), 2L, List.of(secondSpec)),
                        Map.of(1L, recipe()),
                        Map.of(1L, first, 2L, second),
                        prices(),
                        Map.of());
        var summary = NominalMixtureCost.products(c).get(1L);
        assertTrue(summary.variable());
        assertNull(summary.price());
        var bad =
                new Model(
                        3,
                        "BAD",
                        1,
                        "APPROVATA",
                        n("25"),
                        n("0"),
                        n("0"),
                        Map.of(1, n("31"), 2, n("70")),
                        List.of());
        assertNull(NominalMixtureCost.price(recipe(), bad, prices()));
    }

    @Test
    void nominalPriceWeightsLayersWithoutExcessLossesHeightOrTransformation() {
        var a =
                calculator
                        .product(
                                catalogue(model(List.of()), recipe(), prices()),
                                1,
                                Parameters.empty(),
                                false,
                                false)
                        .getFirst();
        // Layer 1 = 140 €/kg, layer 2 = 100 €/kg. 30% × 140 + 70% × 100 = 112.
        decimal("112", a.nominalMixPrice());
        var changed = model(List.of(new Range(n("0"), n("100"), n("5"), n("9"))));
        var b =
                calculator
                        .product(
                                catalogue(changed, recipe(), prices()), 1, parameters, false, false)
                        .getFirst();
        decimal("112", b.nominalMixPrice());
        assertNotEquals(0, a.powderCost().compareTo(b.powderCost()));
        assertNotEquals(
                0,
                b.powderCost()
                        .divide(b.grams().movePointLeft(3), 12, java.math.RoundingMode.HALF_UP)
                        .compareTo(b.nominalMixPrice()));
        var summary = NominalMixtureCost.products(catalogue(changed, recipe(), prices())).get(1L);
        assertFalse(summary.variable());
        decimal("112", summary.price());
        var missing =
                calculator
                        .product(
                                catalogue(changed, recipe(), Map.of()), 1, parameters, false, false)
                        .getFirst();
        assertNull(missing.nominalMixPrice());
        var csv = com.orodent.ecos.costi.products.csv.ProductCostCsv.summary(Instant.now());
        decimal(
                "112",
                (BigDecimal) csv.map(b).get(csv.headers().indexOf("Miscela nominale (EUR/kg)")));
    }

    @Test
    void calculatesOuterExcessesAndAppliesLossExactlyOnce() {
        var result =
                calculator
                        .product(
                                catalogue(model(List.of()), recipe(), prices()),
                                1,
                                parameters,
                                false,
                                false)
                        .getFirst();
        decimal("326.625", result.grams());
        decimal("60.3", result.materials().get(0).grams());
        decimal("40.2", result.materials().get(1).grams());
        decimal("226.125", result.materials().get(2).grams());
        decimal("36.6825", result.powderCost());
        decimal("48.6825", result.total());
        decimal(
                "36.6825",
                result.materials().stream()
                        .map(Material::cost)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(result.problems().isEmpty());
    }

    @Test
    void usesInclusiveLowerExclusiveUpperHeightBandsAndBothExcessesOnMonolayer() {
        var band = model(List.of(new Range(n("10"), n("15"), n("0.4"), n("0.6"))));
        var lower =
                calculator.calculate(
                        catalogue(band, recipe(), prices()), product, article, spec, parameters);
        decimal("276.375", lower.grams());
        var upperSpec = new Specification(11, 1, 1, 1, 1, "TON-OLD", 1, n("98"), n("15"));
        decimal(
                "452.25",
                calculator
                        .calculate(
                                catalogue(band, recipe(), prices()),
                                product,
                                article,
                                upperSpec,
                                parameters)
                        .grams());
        var mono =
                new Model(
                        1,
                        "MONO",
                        1,
                        "APPROVATA",
                        n("25"),
                        n("1"),
                        n("2"),
                        Map.of(1, n("100")),
                        List.of());
        var monoRecipe =
                new Recipe(
                        1,
                        1,
                        "APPROVATA",
                        Map.of(1, List.of(new Ingredient(1, "WHITE", 1, n("100")))));
        var result =
                calculator.calculate(
                        catalogue(mono, monoRecipe, prices()), product, article, spec, parameters);
        decimal("326.625", result.grams());
        decimal("32.6625", result.powderCost());
    }

    @Test
    void neverTreatsMissingPricesOrTransformationAsZero() {
        var missing =
                calculator.calculate(
                        catalogue(model(List.of()), recipe(), Map.of()),
                        product,
                        article,
                        spec,
                        parameters);
        assertNull(missing.powderCost());
        assertNull(missing.total());
        assertNotNull(missing.grams());
        assertTrue(missing.status().contains("WHITE"));
        assertTrue(missing.status().contains("YELLOW"));
        var unknown =
                calculator.calculate(
                        catalogue(model(List.of()), recipe(), prices()),
                        product,
                        article,
                        spec,
                        Parameters.empty());
        decimal("36.6825", unknown.powderCost());
        assertNull(unknown.total());
        var zero = new Parameters(1, n("12"), Map.of(1L, BigDecimal.ZERO));
        decimal(
                "36.6825",
                calculator
                        .calculate(
                                catalogue(model(List.of()), recipe(), prices()),
                                product,
                                article,
                                spec,
                                zero)
                        .total());
    }

    @Test
    void validatesLayerMassAndGeometryInsteadOfNormalisingBadInput() {
        var invalidRecipe =
                new Recipe(
                        1,
                        1,
                        "APPROVATA",
                        Map.of(
                                1,
                                List.of(new Ingredient(1, "WHITE", 1, n("99"))),
                                2,
                                List.of(new Ingredient(1, "WHITE", 1, n("100")))));
        var result =
                calculator.calculate(
                        catalogue(model(List.of()), invalidRecipe, prices()),
                        product,
                        article,
                        spec,
                        parameters);
        assertNull(result.total());
        assertTrue(result.materials().isEmpty());
        var invalidModel =
                new Model(
                        1,
                        "THOR",
                        1,
                        "APPROVATA",
                        n("25"),
                        n("1"),
                        n("2"),
                        Map.of(1, n("20"), 2, n("70")),
                        List.of());
        assertNull(
                calculator
                        .calculate(
                                catalogue(invalidModel, recipe(), prices()),
                                product,
                                article,
                                spec,
                                parameters)
                        .total());
        var overlapping =
                model(
                        List.of(
                                new Range(n("0"), n("20"), n("1"), n("1")),
                                new Range(n("10"), n("30"), n("1"), n("1"))));
        assertNull(
                calculator
                        .calculate(
                                catalogue(overlapping, recipe(), prices()),
                                product,
                                article,
                                spec,
                                parameters)
                        .total());
    }

    @Test
    void usesExplicitPreferredSpecificationAndKeepsAlternativesSeparate() {
        var snapshot = catalogue(model(List.of()), recipe(), prices());
        var newer = new Specification(12, 1, 2, 1, 1, "TON-NEW", 2, n("98"), n("14"));
        var revised =
                new Catalogue(
                        snapshot.capturedAt(),
                        snapshot.products(),
                        snapshot.articles(),
                        Map.of(1L, List.of(newer, spec)),
                        snapshot.recipes(),
                        snapshot.models(),
                        snapshot.prices(),
                        snapshot.parameters());
        var defaults = calculator.product(revised, 1, parameters, false, false);
        assertEquals(1, defaults.size());
        assertEquals(11, defaults.getFirst().specification().id());
        decimal("326.625", defaults.getFirst().grams());
        assertEquals(2, calculator.product(revised, 1, parameters, true, false).size());
        var noDefault =
                new Catalogue(
                        snapshot.capturedAt(),
                        snapshot.products(),
                        List.of(new Article(1, 1, article.code(), n("98"), n("10"), true, null)),
                        snapshot.specifications(),
                        snapshot.recipes(),
                        snapshot.models(),
                        snapshot.prices(),
                        snapshot.parameters());
        assertNull(calculator.product(noDefault, 1, parameters, false, false).getFirst().total());
        assertNotNull(calculator.product(noDefault, 1, parameters, true, false).getFirst().total());
    }

    @Test
    void validatesTransformationWithoutRoundingAndCopiesOverrides() {
        assertThrows(IllegalArgumentException.class, () -> new Parameters(0, n("-1"), Map.of()));
        assertThrows(
                IllegalArgumentException.class, () -> new Parameters(0, n("1.00001"), Map.of()));
        var source = new HashMap<Long, BigDecimal>();
        source.put(1L, n("0"));
        var p = new Parameters(0, n("12"), source);
        source.clear();
        decimal("0", p.forArticle(1));
        decimal("12", p.forArticle(2));
        assertTrue(p.sameValues(new Parameters(1, n("12.0000"), Map.of(1L, n("0.0000")))));
    }
}

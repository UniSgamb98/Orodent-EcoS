package com.orodent.ecos.costi.products.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Immutable technical/economic inputs: no TON data or inventory is changed by costing. */
public final class ProductCosts {
    public static final BigDecimal LOSS_FACTOR = new BigDecimal("1.005");
    public static final String CALCULATION = "COSTI-NOMINALE-1";
    public static final int PAGE_SIZE = 40;

    private ProductCosts() {}

    public record Product(
            long id,
            String code,
            Long lineId,
            String lineCode,
            String lineName,
            String color,
            boolean active) {
        @Override
        public String toString() {
            return code;
        }
    }

    public record Article(
            long id,
            long productId,
            String code,
            BigDecimal diameter,
            BigDecimal height,
            boolean active,
            Long preferredId) {}

    public record Specification(
            long id,
            long articleId,
            int version,
            long compositionId,
            long modelId,
            String productionCalculation,
            Integer articleRevision,
            BigDecimal diameter,
            BigDecimal height) {}

    public record Ingredient(
            long powderId, String code, int technicalVersion, BigDecimal percent) {}

    public record Recipe(
            long id, int version, String state, Map<Integer, List<Ingredient>> layers) {
        public Recipe {
            layers = immutableLists(layers);
        }
    }

    public record Range(BigDecimal from, BigDecimal to, BigDecimal top, BigDecimal bottom) {}

    public record Model(
            long id,
            String code,
            int version,
            String state,
            BigDecimal gramsPerMm,
            BigDecimal top,
            BigDecimal bottom,
            Map<Integer, BigDecimal> layers,
            List<Range> ranges) {
        public Model {
            layers = Map.copyOf(layers);
            ranges = List.copyOf(ranges);
        }
    }

    public record PowderPrice(
            long powderId, int version, LocalDate date, String mode, BigDecimal price) {}

    /** Null common price means unknown; absent overrides inherit it. Zero is an explicit cost. */
    public record Parameters(int version, BigDecimal commonCost, Map<Long, BigDecimal> overrides) {
        public Parameters {
            if (version < 0) throw new IllegalArgumentException("Revisione parametri non valida.");
            commonCost = amount(commonCost);
            var checked = new HashMap<Long, BigDecimal>();
            overrides.forEach(
                    (id, value) -> {
                        if (id == null || id <= 0 || value == null)
                            throw new IllegalArgumentException("Eccezione articolo non valida.");
                        checked.put(id, amount(value));
                    });
            overrides = Map.copyOf(checked);
        }

        public static Parameters empty() {
            return new Parameters(0, null, Map.of());
        }

        public BigDecimal forArticle(long id) {
            return overrides.getOrDefault(id, commonCost);
        }

        public boolean sameValues(Parameters other) {
            return Objects.equals(commonCost, other.commonCost)
                    && overrides.equals(other.overrides);
        }
    }

    public record Catalogue(
            Instant capturedAt,
            List<Product> products,
            List<Article> articles,
            Map<Long, List<Specification>> specifications,
            Map<Long, Recipe> recipes,
            Map<Long, Model> models,
            Map<Long, PowderPrice> prices,
            Map<Long, Parameters> parameters) {
        public Catalogue {
            products = List.copyOf(products);
            articles = List.copyOf(articles);
            specifications = immutableLists(specifications);
            recipes = Map.copyOf(recipes);
            models = Map.copyOf(models);
            prices = Map.copyOf(prices);
            parameters = Map.copyOf(parameters);
        }

        public Parameters parameters(long product) {
            return parameters.getOrDefault(product, Parameters.empty());
        }

        public Catalogue withParameters(long product, Parameters saved) {
            var updated = new HashMap<>(parameters);
            updated.put(product, saved);
            return new Catalogue(
                    capturedAt,
                    products,
                    articles,
                    specifications,
                    recipes,
                    models,
                    prices,
                    updated);
        }
    }

    public record Material(
            int layer,
            Ingredient ingredient,
            BigDecimal grams,
            PowderPrice price,
            BigDecimal cost) {}

    public record Result(
            Product product,
            Article article,
            Specification specification,
            Recipe recipe,
            Model model,
            Parameters parameters,
            BigDecimal transformation,
            BigDecimal grams,
            BigDecimal powderCost,
            BigDecimal total,
            BigDecimal nominalMixPrice,
            List<Material> materials,
            List<String> problems) {
        public Result {
            materials = List.copyOf(materials);
            problems = List.copyOf(problems);
        }

        public String status() {
            return problems.isEmpty() ? "Completo" : "Incompleto: " + String.join("; ", problems);
        }
    }

    public static final class Conflict extends IllegalStateException {
        public Conflict() {
            super(
                    "Parametri modificati da un altro operatore. Ricarica prima di salvare: le tue"
                            + " modifiche restano visibili.");
        }
    }

    private static BigDecimal amount(BigDecimal value) {
        if (value == null) return null;
        try {
            if (value.signum() < 0 || value.setScale(4, RoundingMode.UNNECESSARY).precision() > 15)
                throw new ArithmeticException();
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException(
                    "Trasformazione: valore non negativo, massimo 4 decimali e 11 cifre intere.");
        }
        return value.stripTrailingZeros();
    }

    private static <K, V> Map<K, List<V>> immutableLists(Map<K, List<V>> input) {
        var result = new HashMap<K, List<V>>();
        input.forEach((k, v) -> result.put(k, List.copyOf(v)));
        return Map.copyOf(result);
    }
}

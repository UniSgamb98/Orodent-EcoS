package com.orodent.ecos.costi.scenarios;

import com.orodent.ecos.costi.products.model.PowderPricing.Selection;
import com.orodent.ecos.costi.products.model.ProductCosts.*;

import java.math.*;
import java.time.*;
import java.util.*;

/** Immutable scenario state. No override is ever written to a real TON/economic record. */
public final class Scenarios {
    private Scenarios() {}

    public record Powder(long id, String code, String name, String color, int technicalVersion) {
        public String toString() {
            return code + " · " + name;
        }
    }

    public record Lot(long powderId, int snapshot, String code, BigDecimal kg, BigDecimal price) {}

    public record Base(
            UUID id,
            Catalogue catalogue,
            LocalDate from,
            LocalDate through,
            Map<Long, BigDecimal> sales,
            Map<Long, BigDecimal> production,
            Map<Long, Long> recipeProducts,
            List<Powder> powders,
            List<Lot> lots,
            List<String> warnings) {
        public Base {
            Objects.requireNonNull(id);
            Objects.requireNonNull(catalogue);
            Objects.requireNonNull(from);
            Objects.requireNonNull(through);
            sales = Map.copyOf(sales);
            production = Map.copyOf(production);
            recipeProducts = Map.copyOf(recipeProducts);
            powders = List.copyOf(powders);
            lots = List.copyOf(lots);
            warnings = List.copyOf(warnings);
        }
    }

    public enum PriceMode {
        PERCENT,
        PRICE;

        public String toString() {
            return this == PERCENT ? "Variazione %" : "Prezzo €/kg";
        }
    }

    public record PriceChange(PriceMode mode, BigDecimal value) {
        public PriceChange {
            Objects.requireNonNull(mode);
            Objects.requireNonNull(value);
            if (value.scale() > 8
                    || value.precision() > 19
                    || (mode == PriceMode.PRICE
                            ? value.signum() < 0
                            : value.compareTo(new BigDecimal("-100")) < 0))
                throw new IllegalArgumentException(
                        "Prezzo non negativo o variazione almeno −100%, massimo 8 decimali e 19"
                                + " cifre.");
        }

        public BigDecimal apply(BigDecimal base) {
            return mode == PriceMode.PRICE
                    ? value
                    : base == null
                            ? null
                            : base.multiply(BigDecimal.ONE.add(value.movePointLeft(2)));
        }
    }

    public record Draft(
            UUID id,
            int version,
            String name,
            String notes,
            boolean archived,
            Base base,
            Selection selection,
            Set<Long> products,
            Map<Long, PriceChange> prices,
            Map<Long, Recipe> recipes,
            Map<Long, Model> models,
            Map<Long, Parameters> transforms) {
        public Draft {
            Objects.requireNonNull(id);
            Objects.requireNonNull(base);
            Objects.requireNonNull(selection);
            name = Objects.toString(name, "").strip();
            notes = Objects.toString(notes, "");
            if (name.length() > 160 || notes.length() > 4000 || version < 0)
                throw new IllegalArgumentException(
                        "Nome massimo 160 caratteri; note massimo 4000.");
            products = Set.copyOf(products);
            prices = Map.copyOf(prices);
            recipes = Map.copyOf(recipes);
            models = Map.copyOf(models);
            transforms = Map.copyOf(transforms);
        }

        public static Draft create(Base base, String name) {
            return new Draft(
                    UUID.randomUUID(),
                    0,
                    name,
                    "",
                    false,
                    base,
                    base.catalogue().pricing(),
                    base.catalogue().products().stream()
                            .filter(Product::active)
                            .map(Product::id)
                            .collect(java.util.stream.Collectors.toSet()),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of());
        }

        public Draft state(
                String name,
                String notes,
                boolean archived,
                Selection selection,
                Set<Long> products,
                Map<Long, PriceChange> prices,
                Map<Long, Recipe> recipes,
                Map<Long, Model> models,
                Map<Long, Parameters> transforms) {
            return new Draft(
                    id,
                    version,
                    name,
                    notes,
                    archived,
                    base,
                    selection,
                    products,
                    prices,
                    recipes,
                    models,
                    transforms);
        }

        public Draft saved(int revision) {
            return new Draft(
                    id,
                    revision,
                    name,
                    notes,
                    archived,
                    base,
                    selection,
                    products,
                    prices,
                    recipes,
                    models,
                    transforms);
        }

        public Draft duplicate() {
            return new Draft(
                    UUID.randomUUID(),
                    0,
                    (name.length() > 150 ? name.substring(0, 150) : name) + " · copia",
                    notes,
                    false,
                    base,
                    selection,
                    products,
                    prices,
                    recipes,
                    models,
                    transforms);
        }

        public int changes() {
            return prices.size() + recipes.size() + models.size() + transforms.size();
        }
    }

    public record Entry(UUID id, int version, String name, boolean archived, Instant savedAt) {
        public String toString() {
            return name + " · v" + version + (archived ? " · Archiviato" : "");
        }
    }

    public enum Weight {
        ARTICLES,
        SALES,
        PRODUCTION;

        public String toString() {
            return switch (this) {
                case ARTICLES -> "Media per articolo · stesso peso";
                case SALES -> "Ponderata sulle vendite";
                case PRODUCTION -> "Ponderata sulle produzioni";
            };
        }
    }

    public record Row(Result before, Result after, BigDecimal sales, BigDecimal production) {
        public Long articleId() {
            return before.article() == null ? null : before.article().id();
        }

        public BigDecimal delta() {
            return before.total() == null || after.total() == null
                    ? null
                    : after.total().subtract(before.total());
        }

        public BigDecimal percent() {
            return delta() == null || before.total().signum() == 0
                    ? null
                    : delta().multiply(new BigDecimal("100"))
                            .divide(before.total(), 8, RoundingMode.HALF_UP);
        }

        public String status() {
            return before.problems().isEmpty() && after.problems().isEmpty()
                    ? "Completo"
                    : "Base: " + before.status() + " · Scenario: " + after.status();
        }
    }

    public record Group(
            String key,
            String name,
            Long lineId,
            long productId,
            int articles,
            int comparable,
            int weighted,
            BigDecimal weight,
            BigDecimal before,
            BigDecimal after,
            BigDecimal delta,
            BigDecimal percent) {}

    public record Analysis(
            Draft draft, Catalogue before, Catalogue after, List<Row> rows, List<String> problems) {
        public Analysis {
            rows = List.copyOf(rows);
            problems = List.copyOf(problems);
        }
    }

    public record Refresh(Draft draft, List<String> differences, List<String> conflicts) {
        public Draft accepted() {
            if (!conflicts.isEmpty()) throw new IllegalStateException(String.join("\n", conflicts));
            return draft;
        }

        public Refresh {
            differences = List.copyOf(differences);
            conflicts = List.copyOf(conflicts);
        }
    }

    public static final class Conflict extends IllegalStateException {
        public Conflict() {
            super(
                    "Lo scenario è cambiato su un'altra postazione. Le tue modifiche restano"
                            + " nell'editor: ricarica o duplica la bozza.");
        }
    }
}

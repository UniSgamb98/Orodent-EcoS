package com.orodent.ecos.costi.products.model;

import com.orodent.ecos.costi.products.model.ProductCosts.*;

import java.math.BigDecimal;
import java.util.*;

/** Price of one nominal kg: layer shares × ingredient shares × powder reference price. */
public final class NominalMixtureCost {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private NominalMixtureCost() {}

    public record Summary(BigDecimal price, boolean variable) {}

    public static BigDecimal price(Recipe recipe, Model model, Map<Long, PowderPrice> prices) {
        if (recipe == null
                || model == null
                || "BOZZA".equals(recipe.state())
                || "BOZZA".equals(model.state())
                || model.layers().isEmpty()
                || !model.layers().keySet().equals(recipe.layers().keySet())) return null;
        BigDecimal total = BigDecimal.ZERO, shares = BigDecimal.ZERO;
        for (int layer = 1; layer <= model.layers().size(); layer++) {
            BigDecimal share = model.layers().get(layer);
            if (share == null || share.signum() <= 0) return null;
            shares = shares.add(share);
            BigDecimal ingredients = BigDecimal.ZERO;
            for (Ingredient ingredient : recipe.layers().get(layer)) {
                if (ingredient.percent() == null || ingredient.percent().signum() < 0) return null;
                ingredients = ingredients.add(ingredient.percent());
                if (ingredient.percent().signum() == 0) continue;
                PowderPrice price = prices.get(ingredient.powderId());
                if (price == null || price.price() == null || price.price().signum() < 0)
                    return null;
                total =
                        total.add(
                                share.multiply(ingredient.percent())
                                        .multiply(price.price())
                                        .divide(HUNDRED.multiply(HUNDRED)));
            }
            if (ingredients.compareTo(HUNDRED) != 0) return null;
        }
        return shares.compareTo(HUNDRED) == 0 ? total : null;
    }

    /**
     * Preferred configurations only. Never choose an arbitrary configuration when prices differ.
     */
    public static Map<Long, Summary> products(Catalogue catalogue) {
        var result = new HashMap<Long, Summary>();
        record Key(long recipe, long model) {}
        var cache = new HashMap<Key, Optional<BigDecimal>>();
        for (Article article : catalogue.articles()) {
            var spec =
                    catalogue.specifications().getOrDefault(article.id(), List.of()).stream()
                            .filter(s -> Objects.equals(s.id(), article.preferredId()))
                            .findFirst()
                            .orElse(null);
            BigDecimal value =
                    spec == null
                            ? null
                            : cache.computeIfAbsent(
                                            new Key(spec.compositionId(), spec.modelId()),
                                            key ->
                                                    Optional.ofNullable(
                                                            price(
                                                                    catalogue
                                                                            .recipes()
                                                                            .get(key.recipe()),
                                                                    catalogue
                                                                            .models()
                                                                            .get(key.model()),
                                                                    catalogue.prices())))
                                    .orElse(null);
            Summary before = result.get(article.productId());
            if (before == null) result.put(article.productId(), new Summary(value, false));
            else if (before.variable()) continue;
            else if (before.price() == null || value == null)
                result.put(article.productId(), new Summary(null, false));
            else if (before.price().compareTo(value) != 0)
                result.put(article.productId(), new Summary(null, true));
        }
        return Map.copyOf(result);
    }
}

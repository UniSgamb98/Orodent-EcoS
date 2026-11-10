package com.orodent.ecos.costi.products.service;

import com.orodent.ecos.costi.products.model.NominalMixtureCost;
import com.orodent.ecos.costi.products.model.ProductCosts;
import com.orodent.ecos.costi.products.model.ProductCosts.*;

import java.math.BigDecimal;
import java.util.*;

/** COSTI-NOMINALE-1: percentages of nominal height, then excess on the two outer layers. */
public final class ProductCostCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public List<Result> product(
            Catalogue catalogue,
            long productId,
            Parameters parameters,
            boolean alternatives,
            boolean archived) {
        Product product =
                catalogue.products().stream()
                        .filter(p -> p.id() == productId)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Prodotto non presente nel catalogo."));
        var results = new ArrayList<Result>();
        for (Article article : catalogue.articles()) {
            if (article.productId() != productId || !archived && !article.active()) continue;
            List<Specification> specifications =
                    catalogue.specifications().getOrDefault(article.id(), List.of());
            if (!alternatives)
                specifications =
                        specifications.stream()
                                .filter(s -> Objects.equals(article.preferredId(), s.id()))
                                .toList();
            if (specifications.isEmpty())
                results.add(calculate(catalogue, product, article, null, parameters));
            else
                for (Specification specification : specifications)
                    results.add(calculate(catalogue, product, article, specification, parameters));
        }
        if (results.isEmpty())
            results.add(
                    new Result(
                            product,
                            null,
                            null,
                            null,
                            null,
                            parameters,
                            parameters.commonCost(),
                            null,
                            null,
                            null,
                            null,
                            catalogue.pricing().label(),
                            List.of(),
                            List.of("Nessun articolo nell'ambito scelto")));
        return List.copyOf(results);
    }

    public Result calculate(
            Catalogue catalogue,
            Product product,
            Article article,
            Specification specification,
            Parameters parameters) {
        var problems = new LinkedHashSet<String>();
        BigDecimal transformation = parameters.forArticle(article.id());
        if (transformation == null) problems.add("Trasformazione da compilare");
        Recipe recipe =
                specification == null
                        ? null
                        : catalogue.recipes().get(specification.compositionId());
        Model model =
                specification == null ? null : catalogue.models().get(specification.modelId());
        BigDecimal nominalMixPrice = NominalMixtureCost.price(recipe, model, catalogue.prices());
        if (specification == null) problems.add("Configurazione predefinita approvata assente");
        else if (recipe == null || model == null) problems.add("Composizione o modello assente");
        if (recipe == null || model == null)
            return result(
                    product,
                    article,
                    specification,
                    recipe,
                    model,
                    parameters,
                    transformation,
                    catalogue.pricing().label(),
                    nominalMixPrice,
                    null,
                    null,
                    List.of(),
                    problems);

        String invalid = validate(specification, recipe, model);
        if (invalid != null) {
            problems.add(invalid);
            return result(
                    product,
                    article,
                    specification,
                    recipe,
                    model,
                    parameters,
                    transformation,
                    catalogue.pricing().label(),
                    nominalMixPrice,
                    null,
                    null,
                    List.of(),
                    problems);
        }
        BigDecimal top = model.top(), bottom = model.bottom();
        for (Range range : model.ranges()) {
            if (specification.height().compareTo(range.from()) >= 0
                    && specification.height().compareTo(range.to()) < 0) {
                top = range.top();
                bottom = range.bottom();
                break;
            }
        }
        BigDecimal grams = BigDecimal.ZERO, powderCost = BigDecimal.ZERO;
        boolean missingPrice = false;
        var materials = new ArrayList<Material>();
        for (int layer = 1; layer <= model.layers().size(); layer++) {
            BigDecimal height =
                    specification.height().multiply(model.layers().get(layer)).divide(HUNDRED);
            if (layer == 1) height = height.add(top);
            if (layer == model.layers().size()) height = height.add(bottom);
            BigDecimal layerGrams =
                    height.multiply(model.gramsPerMm()).multiply(ProductCosts.LOSS_FACTOR);
            grams = grams.add(layerGrams);
            for (Ingredient ingredient : recipe.layers().get(layer)) {
                BigDecimal quantity = layerGrams.multiply(ingredient.percent()).divide(HUNDRED);
                PowderPrice price = catalogue.prices().get(ingredient.powderId());
                BigDecimal cost =
                        price == null || price.price() == null
                                ? null
                                : quantity.movePointLeft(3).multiply(price.price());
                materials.add(new Material(layer, ingredient, quantity, price, cost));
                if (cost == null) {
                    missingPrice = true;
                    problems.add("Prezzo mancante: " + ingredient.code());
                } else powderCost = powderCost.add(cost);
            }
        }
        return result(
                product,
                article,
                specification,
                recipe,
                model,
                parameters,
                transformation,
                catalogue.pricing().label(),
                nominalMixPrice,
                grams,
                missingPrice ? null : powderCost,
                materials,
                problems);
    }

    private Result result(
            Product product,
            Article article,
            Specification specification,
            Recipe recipe,
            Model model,
            Parameters parameters,
            BigDecimal transformation,
            String pricingSource,
            BigDecimal nominalMixPrice,
            BigDecimal grams,
            BigDecimal cost,
            List<Material> materials,
            Collection<String> problems) {
        return new Result(
                product,
                article,
                specification,
                recipe,
                model,
                parameters,
                transformation,
                grams,
                cost,
                problems.isEmpty() ? cost.add(transformation) : null,
                nominalMixPrice,
                pricingSource,
                materials,
                List.copyOf(problems));
    }

    private String validate(Specification specification, Recipe recipe, Model model) {
        if ("BOZZA".equals(recipe.state()) || "BOZZA".equals(model.state()))
            return "Composizione o modello non approvato";
        if (specification.height() == null
                || specification.height().signum() <= 0
                || model.gramsPerMm() == null
                || model.gramsPerMm().signum() <= 0)
            return "Altezza o coefficiente g/mm non valido";
        if (model.layers().isEmpty() || !model.layers().keySet().equals(recipe.layers().keySet()))
            return "Strati di modello e composizione non corrispondenti";
        BigDecimal total = BigDecimal.ZERO;
        for (int n = 1; n <= model.layers().size(); n++) {
            BigDecimal part = model.layers().get(n);
            if (part == null || part.signum() <= 0)
                return "Strati del modello non validi o non consecutivi";
            total = total.add(part);
            var ingredients = recipe.layers().get(n);
            if (ingredients == null
                    || ingredients.isEmpty()
                    || ingredients.stream()
                            .anyMatch(i -> i.percent() == null || i.percent().signum() <= 0)
                    || ingredients.stream()
                                    .map(Ingredient::percent)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .compareTo(HUNDRED)
                            != 0) return "Le polveri dello strato " + n + " non totalizzano 100%";
        }
        if (total.compareTo(HUNDRED) != 0) return "Le altezze degli strati non totalizzano 100%";
        BigDecimal previous = null;
        for (Range range : model.ranges()) {
            if (range.from().signum() < 0
                    || range.to().compareTo(range.from()) <= 0
                    || range.top().signum() < 0
                    || range.bottom().signum() < 0
                    || previous != null && range.from().compareTo(previous) < 0)
                return "Fasce di sovramateriale non valide o sovrapposte";
            previous = range.to();
        }
        return null;
    }
}

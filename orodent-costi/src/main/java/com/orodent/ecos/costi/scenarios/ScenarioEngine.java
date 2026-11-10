package com.orodent.ecos.costi.scenarios;

import com.orodent.ecos.costi.products.model.*;
import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.products.service.ProductCostCalculator;
import com.orodent.ecos.costi.scenarios.Scenarios.*;

import java.math.*;
import java.util.*;

public final class ScenarioEngine {
    public Analysis analyse(Draft draft) {
        Catalogue before = PowderPricing.apply(draft.base().catalogue(), draft.selection());
        var prices = new HashMap<>(before.prices());
        draft.prices()
                .forEach(
                        (id, change) -> {
                            var p = prices.get(id);
                            prices.put(
                                    id,
                                    new PowderPrice(
                                            id,
                                            p == null ? 0 : p.version(),
                                            p == null ? null : p.date(),
                                            "IPOTESI SCENARIO",
                                            change.apply(p == null ? null : p.price()),
                                            false,
                                            "Prezzo dello scenario · "
                                                    + change.mode()
                                                    + " "
                                                    + change.value()
                                                    + " · base "
                                                    + (p == null ? "assente" : p.price())));
                        });
        var recipes = new HashMap<>(before.recipes());
        recipes.putAll(draft.recipes());
        var models = new HashMap<>(before.models());
        models.putAll(draft.models());
        var params = new HashMap<>(before.parameters());
        params.putAll(draft.transforms());
        Catalogue after =
                new Catalogue(
                        before.capturedAt(),
                        before.products(),
                        before.articles(),
                        before.specifications(),
                        recipes,
                        models,
                        prices,
                        params,
                        before.stocks(),
                        before.pricing());
        var rows = new ArrayList<Row>();
        var problems = new ArrayList<>(draft.base().warnings());
        var calc = new ProductCostCalculator();
        var byProduct = new HashMap<Long, Product>();
        before.products().forEach(p -> byProduct.put(p.id(), p));
        var seen = new HashSet<Long>();
        for (Article article : before.articles())
            if (draft.products().contains(article.productId())) {
                Product product = byProduct.get(article.productId());
                if (product == null) continue;
                seen.add(product.id());
                Specification spec =
                        before.specifications().getOrDefault(article.id(), List.of()).stream()
                                .filter(v -> Objects.equals(article.preferredId(), v.id()))
                                .findFirst()
                                .orElse(null);
                rows.add(
                        new Row(
                                calc.calculate(
                                        before,
                                        product,
                                        article,
                                        spec,
                                        before.parameters(product.id())),
                                calc.calculate(
                                        after,
                                        product,
                                        article,
                                        spec,
                                        after.parameters(product.id())),
                                draft.base().sales().getOrDefault(article.id(), BigDecimal.ZERO),
                                draft.base()
                                        .production()
                                        .getOrDefault(article.id(), BigDecimal.ZERO)));
            }
        Catalogue emptyBefore =
                new Catalogue(
                        before.capturedAt(),
                        before.products(),
                        List.of(),
                        before.specifications(),
                        before.recipes(),
                        before.models(),
                        before.prices(),
                        before.parameters(),
                        before.stocks(),
                        before.pricing());
        Catalogue emptyAfter =
                new Catalogue(
                        after.capturedAt(),
                        after.products(),
                        List.of(),
                        after.specifications(),
                        after.recipes(),
                        after.models(),
                        after.prices(),
                        after.parameters(),
                        after.stocks(),
                        after.pricing());
        for (Product p : before.products())
            if (draft.products().contains(p.id()) && !seen.contains(p.id()))
                rows.add(
                        new Row(
                                calc.product(
                                                emptyBefore,
                                                p.id(),
                                                before.parameters(p.id()),
                                                false,
                                                true)
                                        .getFirst(),
                                calc.product(
                                                emptyAfter,
                                                p.id(),
                                                after.parameters(p.id()),
                                                false,
                                                true)
                                        .getFirst(),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO));
        rows.sort(
                Comparator.comparing((Row r) -> r.before().product().code())
                        .thenComparing(
                                r ->
                                        r.before().article() == null
                                                ? ""
                                                : r.before().article().code()));
        var ids =
                before.products().stream()
                        .map(Product::id)
                        .collect(java.util.stream.Collectors.toSet());
        if (!ids.containsAll(draft.products()))
            problems.add("Alcuni prodotti dello scenario non esistono nella base.");
        return new Analysis(draft, before, after, rows, problems);
    }

    public List<Group> groups(Analysis analysis, Weight weight, boolean byLine, Long line) {
        var groups = new TreeMap<String, List<Row>>();
        for (Row row : analysis.rows()) {
            Product p = row.before().product();
            if (line != null && !Objects.equals(p.lineId(), line)) continue;
            String key = byLine ? Objects.toString(p.lineId(), "none") : Long.toString(p.id());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        var result = new ArrayList<Group>();
        for (var entry : groups.entrySet()) {
            var rows = entry.getValue();
            Product product = rows.getFirst().before().product();
            BigDecimal sum = BigDecimal.ZERO, b = BigDecimal.ZERO, a = BigDecimal.ZERO;
            int comparable = 0, weighted = 0;
            for (Row row : rows) {
                if (row.articleId() == null
                        || row.before().total() == null
                        || row.after().total() == null) continue;
                comparable++;
                BigDecimal w =
                        switch (weight) {
                            case ARTICLES -> BigDecimal.ONE;
                            case SALES -> row.sales();
                            case PRODUCTION -> row.production();
                        };
                if (w == null || w.signum() <= 0) continue;
                weighted++;
                sum = sum.add(w);
                b = b.add(row.before().total().multiply(w));
                a = a.add(row.after().total().multiply(w));
            }
            BigDecimal before = sum.signum() == 0 ? null : b.divide(sum, 8, RoundingMode.HALF_UP),
                    after = sum.signum() == 0 ? null : a.divide(sum, 8, RoundingMode.HALF_UP),
                    delta = before == null ? null : after.subtract(before),
                    percent =
                            before == null || b.signum() == 0
                                    ? null
                                    : a.subtract(b)
                                            .multiply(new BigDecimal("100"))
                                            .divide(b, 8, RoundingMode.HALF_UP);
            result.add(
                    new Group(
                            entry.getKey(),
                            byLine
                                    ? Objects.toString(product.lineCode(), "Senza linea")
                                    : product.code(),
                            product.lineId(),
                            byLine ? 0 : product.id(),
                            (int) rows.stream().filter(r -> r.articleId() != null).count(),
                            comparable,
                            weighted,
                            sum,
                            before,
                            after,
                            delta,
                            percent));
        }
        result.sort(Comparator.comparing(Group::name));
        return List.copyOf(result);
    }

    public Refresh refresh(Draft old, Base next) {
        var differences = new ArrayList<String>();
        var conflicts = new ArrayList<String>();
        Catalogue before = old.base().catalogue(), after = next.catalogue();
        var valuedBefore = PowderPricing.apply(before, old.selection());
        var valuedAfter = PowderPricing.apply(after, old.selection());
        var ids = new HashSet<>(valuedBefore.prices().keySet());
        ids.addAll(valuedAfter.prices().keySet());
        for (long id : ids)
            if (!Objects.equals(valuedBefore.prices().get(id), valuedAfter.prices().get(id)))
                differences.add("Polvere " + id + ": prezzo, scorte o provenienza aggiornati.");
        var products =
                after.products().stream()
                        .map(Product::id)
                        .collect(java.util.stream.Collectors.toSet());
        for (long id : old.products())
            if (!products.contains(id))
                conflicts.add("Prodotto " + id + " assente nella nuova base.");
        for (long id : old.recipes().keySet())
            if (!Objects.equals(before.recipes().get(id), after.recipes().get(id)))
                conflicts.add(
                        "Composizione di partenza "
                                + id
                                + " cambiata o assente: ripristinare o ricreare la relativa"
                                + " modifica.");
        for (long id : old.models().keySet())
            if (!Objects.equals(before.models().get(id), after.models().get(id)))
                conflicts.add(
                        "Modello di partenza "
                                + id
                                + " cambiato o assente: ripristinare o ricreare la relativa"
                                + " modifica.");
        for (var e : before.specifications().entrySet())
            if (!Objects.equals(e.getValue(), after.specifications().get(e.getKey())))
                differences.add("Articolo " + e.getKey() + ": configurazioni o misure aggiornate.");
        if (!before.parameters().equals(after.parameters()))
            differences.add("Parametri di trasformazione aggiornati.");
        for (var e : old.transforms().entrySet())
            if (!Objects.equals(before.parameters(e.getKey()), after.parameters(e.getKey())))
                conflicts.add(
                        "Trasformazione base del prodotto "
                                + e.getKey()
                                + " cambiata: verificare o ripristinare la relativa modifica.");
        for (long id : old.prices().keySet())
            if (!next.powders().stream().anyMatch(p -> p.id() == id))
                conflicts.add("Polvere modificata " + id + " assente dalla nuova base.");
        if (!old.base().sales().equals(next.sales())
                || !old.base().production().equals(next.production()))
            differences.add("Volumi del periodo aggiornati.");
        if (!before.stocks().equals(after.stocks()))
            differences.add("Fotografie di magazzino, lotti o ordini residui aggiornati.");
        var nextArticles = new HashMap<Long, Article>();
        after.articles().forEach(a -> nextArticles.put(a.id(), a));
        for (Article a : before.articles())
            if (old.products().contains(a.productId())
                    && !Objects.equals(a, nextArticles.get(a.id())))
                differences.add(
                        "Articolo "
                                + a.code()
                                + ": anagrafica o configurazione predefinita aggiornata.");
        var usedRecipes = new HashSet<Long>();
        var usedModels = new HashSet<Long>();
        for (Article a : after.articles())
            if (old.products().contains(a.productId()))
                for (Specification spec : after.specifications().getOrDefault(a.id(), List.of()))
                    if (Objects.equals(a.preferredId(), spec.id())) {
                        usedRecipes.add(spec.compositionId());
                        usedModels.add(spec.modelId());
                    }
        for (long id : old.recipes().keySet())
            if (!usedRecipes.contains(id))
                conflicts.add(
                        "Composizione modificata "
                                + id
                                + " non più usata dalle predefinite del nuovo ambito.");
        for (long id : old.models().keySet())
            if (!usedModels.contains(id))
                conflicts.add(
                        "Modello modificato "
                                + id
                                + " non più usato dalle predefinite del nuovo ambito.");
        for (var parameters : old.transforms().values())
            for (long id : parameters.overrides().keySet())
                if (!nextArticles.containsKey(id))
                    conflicts.add(
                            "Eccezione di trasformazione per articolo "
                                    + id
                                    + " non più collegabile.");
        Draft updated =
                new Draft(
                        old.id(),
                        old.version(),
                        old.name(),
                        old.notes(),
                        old.archived(),
                        next,
                        old.selection(),
                        old.products(),
                        old.prices(),
                        old.recipes(),
                        old.models(),
                        old.transforms());
        return new Refresh(updated, differences, conflicts);
    }

    public static void validateRecipe(Recipe recipe) {
        if (recipe.layers().isEmpty())
            throw new IllegalArgumentException("Aggiungi almeno uno strato.");
        for (int i = 1; i <= recipe.layers().size(); i++) {
            var layer = recipe.layers().get(i);
            if (layer == null || layer.isEmpty())
                throw new IllegalArgumentException("Strati consecutivi, con almeno una polvere.");
            var ids = new HashSet<Long>();
            BigDecimal sum = BigDecimal.ZERO;
            for (Ingredient p : layer) {
                if (p.percent() == null
                        || p.percent().signum() <= 0
                        || p.percent().compareTo(new BigDecimal("100")) > 0
                        || !ids.add(p.powderId()))
                    throw new IllegalArgumentException(
                            "Percentuali positive, senza ripetere la stessa polvere nello strato.");
                sum = sum.add(p.percent());
            }
            if (sum.compareTo(new BigDecimal("100")) != 0)
                throw new IllegalArgumentException("Lo strato " + i + " deve totalizzare 100%.");
        }
    }

    public static void validateModel(Model model) {
        if (model.gramsPerMm() == null
                || model.gramsPerMm().signum() <= 0
                || model.top() == null
                || model.bottom() == null
                || model.top().signum() < 0
                || model.bottom().signum() < 0)
            throw new IllegalArgumentException(
                    "g/mm deve essere positivo; i sovramateriali non negativi.");
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 1; i <= model.layers().size(); i++) {
            BigDecimal p = model.layers().get(i);
            if (p == null || p.signum() <= 0)
                throw new IllegalArgumentException("Strati consecutivi con percentuali positive.");
            sum = sum.add(p);
        }
        if (sum.compareTo(new BigDecimal("100")) != 0)
            throw new IllegalArgumentException("Le altezze degli strati devono totalizzare 100%.");
        BigDecimal previous = null;
        for (Range r : model.ranges()) {
            if (r.from() == null
                    || r.to() == null
                    || r.top() == null
                    || r.bottom() == null
                    || r.from().signum() < 0
                    || r.to().compareTo(r.from()) <= 0
                    || r.top().signum() < 0
                    || r.bottom().signum() < 0
                    || previous != null && r.from().compareTo(previous) < 0)
                throw new IllegalArgumentException(
                        "Fasce ordinate, senza sovrapposizioni: Da incluso, A escluso;"
                                + " sovramateriali non negativi.");
            previous = r.to();
        }
    }
}

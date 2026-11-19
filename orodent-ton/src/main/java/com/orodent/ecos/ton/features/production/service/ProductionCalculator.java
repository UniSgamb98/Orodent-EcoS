package com.orodent.ecos.ton.features.production.service;

import static com.orodent.ecos.ton.features.production.model.Production.*;

import java.math.*;
import java.util.*;

/** Exact quantities; only operational mixture totals/quotas are rounded to 0.1g. */
public final class ProductionCalculator {
    public static final BigDecimal EXTRA = new BigDecimal("1.005");

    private ProductionCalculator() {}

    public static Plan calculate(Workspace workspace, Request request, TemplateChoice template) {
        if (workspace.product().id() != request.productId()
                || workspace.diameter().compareTo(request.diameter()) != 0)
            throw invalid("La selezione degli articoli è cambiata. Aggiorna i dati.");
        var byId = new HashMap<Long, ArticleChoice>();
        workspace.articles().forEach(a -> byId.put(a.id(), a));
        var formats = new ArrayList<Format>();
        ArticleChoice reference = null;
        int count = 0;
        BigDecimal needed = BigDecimal.ZERO;
        for (var q : request.quantities().entrySet()) {
            if (q.getValue() == 0) continue;
            var a = byId.get(q.getKey());
            if (a == null)
                throw invalid("Un articolo non appartiene al prodotto o al diametro selezionati.");
            if (!a.available()) throw invalid(a.code() + ": " + a.reason());
            if (reference == null) reference = a;
            if (a.compositionId() != reference.compositionId()
                    || a.modelId() != reference.modelId())
                throw invalid(
                        "I formati selezionati usano composizioni o modelli diversi. Seleziona"
                            + " specifiche compatibili.");
            try {
                count = Math.addExact(count, q.getValue());
            } catch (ArithmeticException e) {
                throw invalid("Numero complessivo di dischi troppo grande.");
            }
            formats.add(new Format(a, q.getValue()));
            needed = needed.add(a.weight().multiply(BigDecimal.valueOf(q.getValue())));
        }
        formats.sort(Comparator.comparing(f -> f.article().height()));
        var mixtures = new ArrayList<Mixture>();
        if (reference != null)
            for (int layer = 0; layer < reference.doses().size(); layer++) {
                BigDecimal total = BigDecimal.ZERO;
                for (var f : formats)
                    total =
                            total.add(
                                    f.article()
                                            .doses()
                                            .get(layer)
                                            .grams()
                                            .multiply(BigDecimal.valueOf(f.quantity())));
                BigDecimal prepared = total.multiply(EXTRA).setScale(1, RoundingMode.HALF_UP);
                mixtures.add(
                        new Mixture(
                                layer + 1,
                                count,
                                total,
                                prepared,
                                distribute(prepared, reference.doses().get(layer).powders())));
            }
        return new Plan(
                1,
                RULE,
                workspace.product(),
                request.diameter(),
                request.date().toString(),
                request.notes(),
                template,
                formats,
                mixtures,
                count,
                needed,
                mixtures.stream().map(Mixture::prepared).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    static List<PowderWeight> distribute(BigDecimal grams, List<PowderShare> shares) {
        record Part(PowderShare powder, BigDecimal exact, BigInteger floor) {}
        BigInteger ticks = grams.movePointRight(1).toBigIntegerExact();
        var parts =
                shares.stream()
                        .map(
                                p -> {
                                    var exact =
                                            new BigDecimal(ticks)
                                                    .multiply(p.percentage())
                                                    .movePointLeft(2);
                                    return new Part(
                                            p,
                                            exact,
                                            exact.setScale(0, RoundingMode.FLOOR)
                                                    .toBigIntegerExact());
                                })
                        .toList();
        BigInteger rest =
                ticks.subtract(
                        parts.stream().map(Part::floor).reduce(BigInteger.ZERO, BigInteger::add));
        if (rest.signum() < 0 || rest.compareTo(BigInteger.valueOf(parts.size())) > 0)
            throw invalid("Quote delle polveri non valide.");
        var order = new ArrayList<>(parts);
        order.sort(
                Comparator.<Part, BigDecimal>comparing(
                                p -> p.exact().subtract(new BigDecimal(p.floor())))
                        .reversed()
                        .thenComparingLong(p -> p.powder().revisionId()));
        var bonus = new HashSet<Long>();
        for (int i = 0; i < rest.intValueExact(); i++)
            bonus.add(order.get(i).powder().revisionId());
        return parts.stream()
                .map(
                        p ->
                                new PowderWeight(
                                        p.powder().revisionId(),
                                        p.powder().code(),
                                        p.powder().name(),
                                        p.powder().percentage(),
                                        new BigDecimal(
                                                p.floor()
                                                        .add(
                                                                bonus.contains(
                                                                                p.powder()
                                                                                        .revisionId())
                                                                        ? BigInteger.ONE
                                                                        : BigInteger.ZERO),
                                                1)))
                .toList();
    }
}

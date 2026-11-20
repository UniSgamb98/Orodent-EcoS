package com.orodent.ecos.ton.features.compositions.service;

import static com.orodent.ecos.ton.features.compositions.model.Compositions.*;

import java.math.*;
import java.util.*;

/**
 * The laboratory's empirical ppm rule. Percent inputs/outputs use percent points, not fractions.
 */
public final class CompositionChroma {
    private CompositionChroma() {}

    public record Result(
            Map<Long, BigDecimal> percentages,
            BigDecimal gray,
            BigDecimal chromatic,
            BigDecimal whiteTarget,
            BigDecimal whiteActual,
            BigDecimal hpTotal) {
        public Result {
            percentages = Map.copyOf(percentages);
        }

        public List<Ingredient> ingredients() {
            return percentages.entrySet().stream()
                    .filter(e -> e.getValue().signum() > 0)
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> new Ingredient(e.getKey(), e.getValue()))
                    .toList();
        }
    }

    public static ChromaGroup group(PowderRef p) {
        if (p == null) throw invalid("Seleziona una polvere per calcolare il croma.");
        return switch (Objects.toString(p.color(), "")) {
            case "BIANCA" -> ChromaGroup.BIANCA;
            case "GIALLA", "ROSA" -> ChromaGroup.CROMATICA;
            case "GRIGIA" -> ChromaGroup.GRIGIA;
            default ->
                    throw invalid(
                            p.code() + ": specifica il colore della polvere prima di usare i ppm.");
        };
    }

    public static Result calculate(Chroma input) {
        BigDecimal hundred = new BigDecimal("100");
        // ppm/2000 is a fraction. In percent points it is ppm/20.
        BigDecimal totalColor = input.ppm().divide(new BigDecimal("20"));
        BigDecimal gray = sum(input, ChromaGroup.GRIGIA), white = sum(input, ChromaGroup.BIANCA);
        BigDecimal budget = totalColor.subtract(gray), target = hundred.subtract(totalColor);
        if (budget.signum() < 0)
            throw invalid(
                    "Il grigio richiede almeno "
                            + plain(gray.multiply(new BigDecimal("20")))
                            + " ppm. Riduci il grigio o aumenta i ppm.");
        var colored =
                input.parts().stream().filter(p -> p.group() == ChromaGroup.CROMATICA).toList();
        BigDecimal hp = sum(input, ChromaGroup.CROMATICA);
        if (!colored.isEmpty() && hp.compareTo(hundred) != 0)
            throw invalid("Rapporti rosa e gialle: " + plain(hp) + "%. La somma deve essere 100%.");
        if (budget.signum() > 0 && colored.isEmpty())
            throw invalid("Aggiungi almeno una polvere rosa o gialla per il croma richiesto.");
        var percentages = new LinkedHashMap<Long, BigDecimal>();
        for (var p : input.parts())
            if (p.group() != ChromaGroup.CROMATICA)
                percentages.put(p.powderRevisionId(), p.value());
        percentages.putAll(distribute(budget, colored));
        return new Result(percentages, gray, budget, target, white, hp);
    }

    /** Round only once to the existing 0.0001% storage precision; keep each group total exact. */
    private static Map<Long, BigDecimal> distribute(BigDecimal budget, List<ChromaPart> parts) {
        var result = new LinkedHashMap<Long, BigDecimal>();
        if (parts.isEmpty()) return result;
        record Part(long id, BigDecimal exact, BigInteger floor) {}
        var ticks = budget.movePointRight(4).toBigIntegerExact();
        var values =
                parts.stream()
                        .map(
                                p -> {
                                    var exact =
                                            new BigDecimal(ticks)
                                                    .multiply(p.value())
                                                    .movePointLeft(2);
                                    return new Part(
                                            p.powderRevisionId(),
                                            exact,
                                            exact.setScale(0, RoundingMode.FLOOR)
                                                    .toBigIntegerExact());
                                })
                        .toList();
        int remaining =
                ticks.subtract(
                                values.stream()
                                        .map(Part::floor)
                                        .reduce(BigInteger.ZERO, BigInteger::add))
                        .intValueExact();
        var order = new ArrayList<>(values);
        order.sort(
                Comparator.<Part, BigDecimal>comparing(
                                p -> p.exact().subtract(new BigDecimal(p.floor())))
                        .reversed()
                        .thenComparingLong(Part::id));
        var bonus = new HashSet<Long>();
        for (int n = 0; n < remaining; n++) bonus.add(order.get(n).id());
        for (var p : values)
            result.put(
                    p.id(),
                    new BigDecimal(
                                    p.floor()
                                            .add(
                                                    bonus.contains(p.id())
                                                            ? BigInteger.ONE
                                                            : BigInteger.ZERO),
                                    4)
                            .stripTrailingZeros());
        return result;
    }

    private static BigDecimal sum(Chroma c, ChromaGroup group) {
        return c.parts().stream()
                .filter(p -> p.group() == group)
                .map(ChromaPart::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal ppm(Layer layer, Map<Long, PowderRef> powders) {
        BigDecimal color = BigDecimal.ZERO;
        for (var i : layer.ingredients())
            if (group(powders.get(i.powderRevisionId())) != ChromaGroup.BIANCA)
                color = color.add(i.mass());
        return color.multiply(new BigDecimal("20")).stripTrailingZeros();
    }

    public static void verify(Layer layer, Map<Long, PowderRef> refs) {
        if (layer.chroma() == null) return;
        for (var p : layer.chroma().parts())
            if (group(refs.get(p.powderRevisionId())) != p.group())
                throw invalid("Il colore di una polvere è cambiato. Rivedi il calcolo del croma.");
        var actual = new TreeMap<Long, BigDecimal>();
        layer.ingredients()
                .forEach(i -> actual.put(i.powderRevisionId(), i.mass().stripTrailingZeros()));
        var expected = new TreeMap<Long, BigDecimal>();
        calculate(layer.chroma())
                .ingredients()
                .forEach(i -> expected.put(i.powderRevisionId(), i.mass().stripTrailingZeros()));
        if (!actual.equals(expected))
            throw invalid(
                    "Le percentuali dello strato non corrispondono ai parametri ppm. Ricalcola il"
                            + " croma prima di salvare.");
    }

    private static String plain(BigDecimal n) {
        return n.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}

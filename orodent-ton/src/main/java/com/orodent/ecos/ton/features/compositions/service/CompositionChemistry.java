package com.orodent.ecos.ton.features.compositions.service;

import com.orodent.ecos.ton.features.compositions.model.Compositions.*;

import java.math.BigDecimal;
import java.util.*;

/** Nominal mass balance only. Missing oxide values are never treated as zero or renormalised. */
public final class CompositionChemistry {
    private CompositionChemistry() {}

    public record Balance(
            String oxide, BigDecimal weightPercent, BigDecimal coverage, boolean completeRecipe) {}

    public static List<String> oxides(List<Layer> layers, Map<Long, PowderRef> powders) {
        var codes = new TreeSet<String>();
        for (Layer l : layers)
            for (Ingredient i : l.ingredients()) {
                PowderRef p = powders.get(i.powderRevisionId());
                if (p != null) codes.addAll(p.oxides().keySet());
            }
        return List.copyOf(codes);
    }

    public static boolean complete(Layer layer, Map<Long, PowderRef> powders) {
        if (!layer.complete()) return false;
        var seen = new HashSet<Long>();
        for (Ingredient i : layer.ingredients()) {
            PowderRef p = powders.get(i.powderRevisionId());
            if (p == null || !seen.add(p.powderId())) return false;
        }
        return true;
    }

    public static Balance balance(Layer layer, String oxide, Map<Long, PowderRef> powders) {
        boolean complete = complete(layer, powders);
        BigDecimal sum = BigDecimal.ZERO, coverage = BigDecimal.ZERO;
        for (Ingredient i : layer.ingredients()) {
            PowderRef powder = powders.get(i.powderRevisionId());
            BigDecimal value = powder == null ? null : powder.oxides().get(oxide);
            if (value != null) {
                coverage = coverage.add(i.mass());
                sum = sum.add(i.mass().multiply(value).movePointLeft(2));
            }
        }
        return new Balance(
                oxide,
                complete && coverage.compareTo(BigDecimal.valueOf(100)) == 0
                        ? sum.stripTrailingZeros()
                        : null,
                coverage.stripTrailingZeros(),
                complete);
    }
}

package com.orodent.ecos.core;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/** Shared display metadata. It never changes technical revisions or calculated quantities. */
public record PowderAppearance(
        long id, String code, String supplier, String color, Integer yttria) {
    public PowderAppearance {
        code = Objects.toString(code, "").strip();
        supplier = Objects.toString(supplier, "").strip();
        color = Objects.toString(color, "ALTRE").strip().toUpperCase(Locale.ROOT);
    }

    public static Comparator<PowderAppearance> order() {
        return Comparator.comparing((PowderAppearance p) -> p.supplier().isBlank())
                .thenComparing(PowderAppearance::supplier, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(p -> colorRank(p.color()))
                .thenComparing(PowderAppearance::yttria, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(PowderAppearance::code, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(PowderAppearance::id);
    }

    public static int colorRank(String color) {
        return switch (Objects.toString(color, "").toUpperCase(Locale.ROOT)) {
            case "BIANCA" -> 0;
            case "GIALLA" -> 1;
            case "ROSA" -> 2;
            case "GRIGIA" -> 3;
            default -> 4;
        };
    }

    public String background() {
        return switch (color) {
            case "BIANCA" -> "#f6f6f0";
            case "GIALLA" -> "#fff1b8";
            case "ROSA" -> "#fbe1e8";
            case "GRIGIA" -> "#e3e7ed";
            default -> "#edf3f7";
        };
    }

    public static PowderAppearance unknown(long id, String code) {
        return new PowderAppearance(id, code, "", "ALTRE", null);
    }
}

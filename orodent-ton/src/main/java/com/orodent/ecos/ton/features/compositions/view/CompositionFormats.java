package com.orodent.ecos.ton.features.compositions.view;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

final class CompositionFormats {
    private CompositionFormats() {}

    /** Four input decimals times four oxide decimals / 100: at most ten fractional digits. */
    static String oxide(BigDecimal value) {
        if (value == null) return "—";
        return new DecimalFormat("0.000#######", DecimalFormatSymbols.getInstance(Locale.ITALIAN))
                        .format(value)
                + " wt%";
    }

    static String color(String value) {
        return switch (value == null ? "" : value) {
            case "BIANCA" -> "#e4ebef";
            case "GIALLA" -> "#e8dca8";
            case "ROSA" -> "#e6c7ce";
            case "GRIGIA" -> "#b9c5cb";
            default -> "#d1e1ea";
        };
    }
}

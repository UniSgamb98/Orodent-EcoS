package com.orodent.ecos.costi.view;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class PriceFormats {
    private PriceFormats() {}

    public static BigDecimal decimal(String text, String name) {
        String value = text.strip();
        if (value.isEmpty()) return null;
        if (!value.matches("[+]?[0-9]+([.,][0-9]+)?"))
            throw new IllegalArgumentException(
                    name + ": usa virgola o punto decimale, senza separatori delle migliaia.");
        return new BigDecimal(value.replace(',', '.'));
    }

    public static String edit(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    public static String number(BigDecimal value, int min, int max) {
        if (value == null) return "—";
        var format = NumberFormat.getNumberInstance(Locale.ITALY);
        format.setMinimumFractionDigits(min);
        format.setMaximumFractionDigits(max);
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return format.format(value);
    }

    public static String quantity(BigDecimal value) {
        return value == null ? "Non indicata" : number(value, 0, 3) + " kg";
    }

    public static String price(BigDecimal value) {
        return value == null ? "Da compilare" : number(value, 2, 4) + " €/kg";
    }

    public static String currency(BigDecimal value) {
        return number(value, 2, 2) + " €";
    }
}

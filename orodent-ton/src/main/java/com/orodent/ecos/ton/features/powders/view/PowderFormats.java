package com.orodent.ecos.ton.features.powders.view;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

final class PowderFormats {
    private PowderFormats() {}

    static final Map<String, String> OXIDES = oxides();

    private static Map<String, String> oxides() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String[] pair :
                new String[][] {
                    {"ZRO2", "ZrO₂"},
                    {"HFO2", "HfO₂"},
                    {"Y2O3", "Y₂O₃"},
                    {"AL2O3", "Al₂O₃"},
                    {"SIO2", "SiO₂"},
                    {"FE2O3", "Fe₂O₃"},
                    {"TIO2", "TiO₂"},
                    {"CEO2", "CeO₂"},
                    {"CAO", "CaO"},
                    {"MGO", "MgO"},
                    {"NA2O", "Na₂O"},
                    {"K2O", "K₂O"}
                }) result.put(pair[0], pair[1]);
        return java.util.Collections.unmodifiableMap(result);
    }

    static String oxide(String code) {
        return OXIDES.getOrDefault(code, code);
    }

    static String number(BigDecimal value) {
        return value == null ? "" : value.toPlainString().replace('.', ',');
    }

    static String weight(BigDecimal value) {
        return number(value.setScale(Math.max(3, value.stripTrailingZeros().scale())));
    }

    static BigDecimal decimal(String value, String field) {
        String text = value.strip();
        if (text.isEmpty()) return null;
        if (!text.matches("[+]?[0-9]+([.,][0-9]+)?"))
            throw new IllegalArgumentException(
                    field + ": inserisci un numero usando virgola o punto decimale.");
        return new BigDecimal(text.replace(',', '.'));
    }

    static int integer(String value, String field) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(field + ": inserisci un numero intero valido.");
        }
    }

    static Integer yttria(String value) {
        String text = value.strip().toUpperCase(java.util.Locale.ROOT);
        if (text.endsWith("Y")) text = text.substring(0, text.length() - 1).strip();
        return text.isEmpty() ? null : integer(text, "Classe Y");
    }
}

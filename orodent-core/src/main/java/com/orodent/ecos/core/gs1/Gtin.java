package com.orodent.ecos.core.gs1;

/** GS1 identifiers are stored in the 14-digit representation used by AI 01. */
public final class Gtin {
    private Gtin() {}

    public static String normalize(String input) {
        if (input == null || input.isBlank()) return null;
        String value = input.strip();
        if (!value.matches("[0-9]{8}|[0-9]{12,14}"))
            throw new IllegalArgumentException("GTIN: inserisci 8, 12, 13 o 14 cifre.");
        int sum = 0;
        for (int i = value.length() - 2, weight = 3; i >= 0; i--, weight = 4 - weight)
            sum += (value.charAt(i) - '0') * weight;
        if ((10 - sum % 10) % 10 != value.charAt(value.length() - 1) - '0')
            throw new IllegalArgumentException("La cifra di controllo del GTIN non è valida.");
        return "0".repeat(14 - value.length()) + value;
    }
}

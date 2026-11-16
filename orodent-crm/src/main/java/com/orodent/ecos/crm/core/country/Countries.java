package com.orodent.ecos.crm.core.country;

import java.text.Collator;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/** Offline country catalogue shared by forms, legacy matching and searches. */
public final class Countries {
    public static final String MISSING = "__MISSING__";
    public static final String REVIEW = "__REVIEW__";
    private static final Map<String, String> NAMES = new HashMap<>();
    private static final Map<String, Set<String>> ALIASES = new HashMap<>();
    private static final List<String> SORTED_NAMES;

    static {
        var languages = List.of("it", "en", "de", "fr", "es", "pt", "ro");
        for (String code : Locale.getISOCountries()) {
            Locale region = Locale.forLanguageTag("und-" + code);
            NAMES.put(code, region.getDisplayCountry(Locale.ITALIAN));
            alias(code, code);
            alias(code, region.getISO3Country());
            for (String language : languages)
                alias(code, region.getDisplayCountry(Locale.forLanguageTag(language)));
        }
        NAMES.put("XK", "Kosovo");
        alias("XK", "XK");
        alias("XK", "Kosovo");
        alias("GB", "UK");
        alias("GB", "U.K.");
        alias("GB", "Gran Bretagna");
        alias("US", "U.S.A.");
        alias("US", "Stati Uniti d'America");
        SORTED_NAMES =
                NAMES.values().stream().sorted(Collator.getInstance(Locale.ITALIAN)).toList();
    }

    private Countries() {}

    private static void alias(String code, String text) {
        ALIASES.computeIfAbsent(key(text), ignored -> new HashSet<>()).add(code);
    }

    public static String key(String text) {
        return Normalizer.normalize(Objects.toString(text, "").strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    public static Optional<String> code(String text) {
        var matches = ALIASES.get(key(text));
        return matches != null && matches.size() == 1
                ? Optional.of(matches.iterator().next())
                : Optional.empty();
    }

    public static List<String> names() {
        return SORTED_NAMES;
    }

    public static String name(String code) {
        return NAMES.getOrDefault(code, code);
    }

    public static String display(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return code(raw).map(Countries::name).orElse(raw.strip() + " · da verificare");
    }

    public static String displayAll(List<String> raw) {
        return raw.stream()
                .map(Countries::display)
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted(Collator.getInstance(Locale.ITALIAN))
                .collect(Collectors.joining(" · "));
    }

    public static String forWrite(String raw, String previous) {
        if (raw == null || raw.isBlank()) return null;
        var known = code(raw);
        if (known.isPresent()) return name(known.get());
        if (previous != null && raw.strip().equals(previous.strip())) return previous;
        throw new IllegalArgumentException(
                "Paese non riconosciuto: scegli una voce dall'elenco dei paesi.");
    }

    public static String filterLabel(String code) {
        return switch (Objects.toString(code, "")) {
            case MISSING -> "Paese non indicato";
            case REVIEW -> "Paesi da verificare";
            case "" -> "Tutti i paesi";
            default -> name(code);
        };
    }
}

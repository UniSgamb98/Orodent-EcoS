package com.orodent.ecos.ton.features.marking.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orodent.ecos.core.math.FormulaProgram;
import com.orodent.ecos.database.CubatureRuleVersions.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/** Plain marking text and explicit numeric formatting; no executable template language. */
public final class MarkingTemplates {
    private MarkingTemplates() {}

    private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]{0,31})\\}");

    public record Field(String name, String label, boolean numeric) {
        @Override
        public String toString() {
            return "{" + name + "} · " + label;
        }
    }

    public record Entry(long id, String code, int token, Snapshot latest) {}

    public record Snapshot(
            long id,
            long modelId,
            int number,
            int revision,
            String state,
            String name,
            String description,
            boolean active,
            String text,
            Long ruleId,
            Map<String, Integer> decimals,
            String separator,
            LocalDateTime savedAt,
            boolean current) {
        public Snapshot {
            decimals = Map.copyOf(decimals);
        }

        public boolean editable() {
            return current && state.equals("BOZZA");
        }

        public String label() {
            return "v" + number + " · r" + revision;
        }

        public String stateLabel() {
            return state.equals("APPROVATA") ? "Approvata" : "Bozza";
        }
    }

    public record Detail(
            Entry entry, Snapshot selected, List<Snapshot> versions, List<Version> rules) {
        public Detail {
            versions = List.copyOf(versions);
            rules = List.copyOf(rules);
        }
    }

    public record Draft(
            String code,
            String name,
            String description,
            boolean active,
            String text,
            Long ruleId,
            Map<String, Integer> decimals,
            String separator) {
        public Draft {
            code = required(code, "Codice", 50);
            name = required(name, "Nome", 150);
            description = limited(description, "Descrizione", 1000);
            text = limited(text, "Scritta", 2000).replace("\r\n", "\n").replace('\r', '\n');
            decimals = Map.copyOf(decimals);
            if (decimals.size() > 205)
                throw new IllegalArgumentException("Troppi formati numerici.");
            decimals.forEach(
                    (field, places) -> {
                        if (!field.matches("[A-Za-z][A-Za-z0-9_]{0,31}")
                                || places < 0
                                || places > 8)
                            throw new IllegalArgumentException(
                                    "Decimali: scegli un numero da 0 a 8.");
                    });
            if (!Set.of(".", ",").contains(separator))
                throw new IllegalArgumentException("Separatore decimale non valido.");
            if (formatsJson(decimals).length() > 4000)
                throw new IllegalArgumentException("Troppi formati numerici.");
        }
    }

    private static String required(String text, String label, int max) {
        String value = limited(text, label, max).strip();
        if (value.isEmpty()) throw new IllegalArgumentException(label + ": inserisci un valore.");
        return value;
    }

    private static String limited(String text, String label, int max) {
        String value = Objects.requireNonNullElse(text, "");
        if (value.length() > max)
            throw new IllegalArgumentException(label + ": massimo " + max + " caratteri.");
        return value;
    }

    public static List<Field> fields(Version rule) {
        var fields =
                new ArrayList<>(
                        List.of(
                                new Field("codice_articolo", "Codice articolo", false),
                                new Field("descrizione_articolo", "Descrizione articolo", false),
                                new Field("codice_prodotto", "Codice prodotto", false),
                                new Field("lotto", "Lotto", false),
                                new Field("numero_batch", "Numero batch del disco", false),
                                new Field(
                                        "diametro_nominale",
                                        "Diametro nominale articolo · mm",
                                        true),
                                new Field(
                                        "altezza_nominale", "Altezza nominale articolo · mm", true),
                                new Field("D", "Diametro misurato · mm", true),
                                new Field("Hmedia", "Altezza media misurata · mm", true),
                                new Field("P", "Peso · g", true)));
        if (rule != null) {
            if (!rule.state().equals("APPROVATA"))
                throw new IllegalArgumentException("Seleziona una regola di cubatura approvata.");
            for (String name : FormulaProgram.compile(rule.formulas()).outputs()) {
                if (fields.stream().anyMatch(f -> f.name().equals(name)))
                    throw new IllegalArgumentException(
                            "Il risultato "
                                    + name
                                    + " coincide con un campo della marcatura. Rinominalo nella"
                                    + " regola di cubatura.");
                fields.add(new Field(name, "Risultato calcolato", true));
            }
        }
        return List.copyOf(fields);
    }

    public static List<String> references(String text) {
        var names = new LinkedHashSet<String>();
        var matcher = TOKEN.matcher(text);
        int end = 0;
        while (matcher.find()) {
            checkLiteral(text.substring(end, matcher.start()));
            names.add(matcher.group(1));
            end = matcher.end();
        }
        checkLiteral(text.substring(end));
        return List.copyOf(names);
    }

    private static void checkLiteral(String text) {
        if (text.indexOf('{') >= 0 || text.indexOf('}') >= 0)
            throw new IllegalArgumentException("Campo non valido: usa {nome_variabile}.");
    }

    public static void validate(Draft draft, Version rule) {
        if (draft.text().isBlank())
            throw new IllegalArgumentException("Inserisci la scritta da marcare.");
        if (!Objects.equals(draft.ruleId(), rule == null ? null : rule.id()))
            throw new IllegalArgumentException("Versione della regola di cubatura non coerente.");
        var fields = fields(rule);
        var names = fields.stream().map(Field::name).toList();
        for (String name : references(draft.text()))
            if (!names.contains(name))
                throw new IllegalArgumentException(
                        "Campo non disponibile: "
                                + name
                                + ". Scegli la regola di cubatura o correggi il nome.");
        for (String name : draft.decimals().keySet())
            if (fields.stream().noneMatch(f -> f.numeric() && f.name().equals(name)))
                throw new IllegalArgumentException("Formato numerico non valido per " + name + ".");
    }

    public static String render(Draft draft, Version rule, Map<String, ?> values) {
        validate(draft, rule);
        var numeric = new HashSet<String>();
        fields(rule).stream().filter(Field::numeric).forEach(f -> numeric.add(f.name()));
        var matcher = TOKEN.matcher(draft.text());
        var result = new StringBuilder();
        int end = 0;
        while (matcher.find()) {
            result.append(draft.text(), end, matcher.start());
            String name = matcher.group(1);
            Object value = values.get(name);
            if (value == null) throw new IllegalArgumentException("Valore mancante: " + name + ".");
            if (numeric.contains(name)) {
                if (!(value instanceof BigDecimal number))
                    throw new IllegalArgumentException("Valore numerico richiesto: " + name + ".");
                if (number.precision() - number.scale() > 100 || number.scale() > 1000)
                    throw new IllegalArgumentException("Valore fuori intervallo: " + name + ".");
                result.append(
                        number.setScale(
                                        draft.decimals().getOrDefault(name, 3),
                                        RoundingMode.HALF_UP)
                                .toPlainString()
                                .replace(".", draft.separator()));
            } else result.append(value);
            end = matcher.end();
        }
        return result.append(draft.text(), end, draft.text().length()).toString();
    }

    public static String formatsJson(Map<String, Integer> formats) {
        var json = new JsonObject();
        new TreeMap<>(formats).forEach(json::addProperty);
        return json.toString();
    }

    public static Map<String, Integer> formats(String json) {
        var values = new LinkedHashMap<String, Integer>();
        JsonParser.parseString(json)
                .getAsJsonObject()
                .entrySet()
                .forEach(e -> values.put(e.getKey(), e.getValue().getAsInt()));
        return Map.copyOf(values);
    }
}

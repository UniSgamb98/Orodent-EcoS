package com.orodent.ecos.ton.features.documents.model;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.*;

/** Versioned visual layouts, independent of issued documents and production data. */
public final class DocumentTemplates {
    public static final int MAX_CONTENT = 8_000_000;

    private DocumentTemplates() {}

    public enum State {
        BOZZA,
        APPROVATA,
        RITIRATA
    }

    public record Template(
            long id,
            String code,
            int number,
            String name,
            State state,
            int token,
            LocalDateTime updated) {
        @Override
        public String toString() {
            return "v" + number + " · " + stateLabel(state);
        }
    }

    public record Detail(Template template, String content) {}

    public record Page(List<Template> rows, int offset, boolean more) {
        public Page {
            rows = List.copyOf(rows);
        }
    }

    public record Draft(String code, String content) {
        public Draft {
            code = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Z0-9][A-Z0-9._-]{0,99}"))
                throw new IllegalArgumentException(
                        "Codice modello: usa da 1 a 100 lettere, numeri, punti o trattini.");
            validate(content, false);
        }

        public String name() {
            return parse(content).get("title").getAsString().strip();
        }

        public void requireApprovable() {
            validate(content, true);
        }
    }

    /**
     * Parse strictly before persisting. Layout rendering is additionally checked by Tiptap's
     * schema.
     */
    private static JsonObject parse(String content) {
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT)
            throw new IllegalArgumentException("Il modello è vuoto o supera il limite di 8 MB.");
        try (var reader = new JsonReader(new StringReader(content))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement json = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || !json.isJsonObject())
                throw new IllegalArgumentException();
            return json.getAsJsonObject();
        } catch (Exception invalid) {
            throw new IllegalArgumentException(
                    "Il file non contiene un modello visuale valido.", invalid);
        }
    }

    private static void validate(String content, boolean approve) {
        try {
            JsonObject envelope = parse(content);
            if (!envelope.get("schemaVersion").isJsonPrimitive()
                    || !envelope.getAsJsonPrimitive("schemaVersion").isNumber()
                    || !Set.of("2", "3", "4").contains(envelope.get("schemaVersion").getAsString()))
                throw new IllegalArgumentException();
            JsonPrimitive title = envelope.getAsJsonPrimitive("title");
            if (!title.isString()
                    || title.getAsString().isBlank()
                    || title.getAsString().length() > 150) throw new IllegalArgumentException();
            JsonObject page = envelope.getAsJsonObject("page");
            if (!Set.of("portrait", "landscape").contains(page.get("orientation").getAsString())
                    || !Set.of("10", "15", "20").contains(page.get("margin").getAsString()))
                throw new IllegalArgumentException();
            JsonObject doc = envelope.getAsJsonObject("content");
            if (!doc.get("type").getAsString().equals("doc")) throw new IllegalArgumentException();
            var present = new HashSet<String>();
            inspect(doc, present, 0, new int[] {0}, envelope.get("schemaVersion").getAsInt());
            if (approve
                    && !present.containsAll(
                            Set.of("identity", "press", "weighing", "mixtures", "totals")))
                throw new IllegalArgumentException(
                        "Per approvare servono tutti i blocchi operativi: intestazione, pressione,"
                                + " pesate, miscele e totali.");
        } catch (RuntimeException invalid) {
            if (invalid instanceof IllegalArgumentException && invalid.getMessage() != null)
                throw invalid;
            throw new IllegalArgumentException(
                    "Struttura del modello non supportata. Aprilo nell’editor e verifica il"
                            + " contenuto.",
                    invalid);
        }
    }

    private static void inspect(
            JsonObject node, Set<String> present, int depth, int[] count, int schema) {
        if (depth > 64 || ++count[0] > 100_000)
            throw new IllegalArgumentException("Il modello contiene troppi elementi annidati.");
        String type = node.get("type").getAsString();
        if (type.equals("tonColumns")) {
            JsonArray columns = node.getAsJsonArray("content");
            if (columns == null || columns.size() < 1 || columns.size() > 4)
                throw new IllegalArgumentException("La sezione deve avere da 1 a 4 colonne.");
            for (JsonElement column : columns)
                if (!column.getAsJsonObject().get("type").getAsString().equals("tonColumn"))
                    throw new IllegalArgumentException("Contenuto della sezione non valido.");
            if (schema >= 3) {
                JsonObject attrs = node.getAsJsonObject("attrs");
                JsonArray widths = attrs.getAsJsonArray("widths");
                if (widths.size() != columns.size())
                    throw new IllegalArgumentException("Larghezze delle colonne non valide.");
                double total = 0;
                for (JsonElement value : widths) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                        throw new IllegalArgumentException("Larghezze delle colonne non valide.");
                    double width = value.getAsDouble();
                    if (!Double.isFinite(width)
                            || width < (columns.size() == 1 ? 100 : 8)
                            || width > 100)
                        throw new IllegalArgumentException("Larghezze delle colonne non valide.");
                    total += width;
                }
                JsonPrimitive gap = attrs.getAsJsonPrimitive("gap");
                if (Math.abs(total - 100) >= 0.01
                        || !gap.isNumber()
                        || !Double.isFinite(gap.getAsDouble())
                        || gap.getAsDouble() < 0
                        || gap.getAsDouble() > 12)
                    throw new IllegalArgumentException(
                            "Le larghezze devono totalizzare il 100% e lo spazio deve essere da 0 a"
                                    + " 12 mm.");
            }
        }
        if (type.equals("tonBlock")) {
            JsonObject attrs = node.getAsJsonObject("attrs");
            present.add(attrs.get("kind").getAsString());
            if (schema >= 4) validateBlockOptions(attrs);
        }
        if (node.has("content"))
            for (JsonElement child : node.getAsJsonArray("content"))
                inspect(child.getAsJsonObject(), present, depth + 1, count, schema);
    }

    private static void validateBlockOptions(JsonObject attrs) {
        for (String name :
                List.of(
                        "showTitle",
                        "highlightTotals",
                        "pageBreakBefore",
                        "keepCardsTogether",
                        "repeatHeaders",
                        "hideZeroFormats",
                        "showDiameter",
                        "showNominal",
                        "hideZeroPowders",
                        "showPercent",
                        "emphasizeWeights",
                        "showPpm",
                        "showCoefficient",
                        "compact")) {
            if (attrs.has(name)
                    && (!attrs.get(name).isJsonPrimitive()
                            || !attrs.getAsJsonPrimitive(name).isBoolean()))
                throw new IllegalArgumentException("Opzioni del blocco non valide.");
        }
        if (attrs.has("headerColor")
                && !attrs.get("headerColor").getAsString().matches("|#[a-fA-F0-9]{6}"))
            throw new IllegalArgumentException("Colore delle intestazioni non valido.");
        if (attrs.has("formatOrder")
                && !Set.of("production", "heightAsc", "heightDesc", "code")
                        .contains(attrs.get("formatOrder").getAsString()))
            throw new IllegalArgumentException("Ordine dei formati non valido.");
        if (attrs.has("powderLabel")
                && !Set.of("code", "name", "codeName")
                        .contains(attrs.get("powderLabel").getAsString()))
            throw new IllegalArgumentException("Identificazione della polvere non valida.");
        if (attrs.has("fontSize")
                && !Set.of(
                                "3pt", "5pt", "7pt", "9pt", "10pt", "11pt", "12pt", "14pt", "18pt",
                                "24pt")
                        .contains(attrs.get("fontSize").getAsString()))
            throw new IllegalArgumentException("Dimensione del testo non valida.");
        if (attrs.has("tableWidths") && !attrs.get("tableWidths").isJsonNull()) {
            int expected =
                    switch (attrs.get("kind").getAsString()) {
                        case "weighing" ->
                                attrs.has("showPpm") && attrs.get("showPpm").getAsBoolean() ? 5 : 4;
                        case "mixtures" ->
                                !attrs.has("showPercent") || attrs.get("showPercent").getAsBoolean()
                                        ? 3
                                        : 2;
                        default -> 0;
                    };
            JsonArray widths = attrs.getAsJsonArray("tableWidths");
            if (expected == 0 || widths.size() != expected)
                throw new IllegalArgumentException("Larghezze della tabella non valide.");
            double total = 0;
            for (JsonElement value : widths) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                    throw new IllegalArgumentException("Larghezze della tabella non valide.");
                double width = value.getAsDouble();
                if (!Double.isFinite(width) || width < 8 || width > 100)
                    throw new IllegalArgumentException("Larghezze della tabella non valide.");
                total += width;
            }
            if (Math.abs(total - 100) >= 0.01)
                throw new IllegalArgumentException("Le larghezze devono totalizzare il 100%.");
        }
    }

    public static String stateLabel(State state) {
        return switch (state) {
            case BOZZA -> "Bozza";
            case APPROVATA -> "Approvata";
            case RITIRATA -> "Ritirata";
        };
    }
}

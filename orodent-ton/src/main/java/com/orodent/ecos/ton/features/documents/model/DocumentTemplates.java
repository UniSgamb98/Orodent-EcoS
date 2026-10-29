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
                    || !envelope.get("schemaVersion").getAsString().equals("2"))
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
            inspect(doc, present, 0, new int[] {0});
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

    private static void inspect(JsonObject node, Set<String> present, int depth, int[] count) {
        if (depth > 64 || ++count[0] > 100_000)
            throw new IllegalArgumentException("Il modello contiene troppi elementi annidati.");
        String type = node.get("type").getAsString();
        if (type.equals("tonBlock"))
            present.add(node.getAsJsonObject("attrs").get("kind").getAsString());
        if (node.has("content"))
            for (JsonElement child : node.getAsJsonArray("content"))
                inspect(child.getAsJsonObject(), present, depth + 1, count);
    }

    public static String stateLabel(State state) {
        return switch (state) {
            case BOZZA -> "Bozza";
            case APPROVATA -> "Approvata";
            case RITIRATA -> "Ritirata";
        };
    }
}

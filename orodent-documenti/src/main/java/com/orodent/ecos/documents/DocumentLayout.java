package com.orodent.ecos.documents;

import com.google.gson.*;

import java.util.Set;

/** Validates the physical canvas before a version can enter the shared database. */
public final class DocumentLayout {
    private DocumentLayout() {}

    public static void validate(JsonObject envelope) {
        var page = envelope.getAsJsonObject("page");
        var paper = DocumentPaper.from(page);
        int pages = page.has("count") ? integer(page, "count") : 1;
        if (pages < 1 || pages > 50)
            throw new IllegalArgumentException("Numero pagine: da 1 a 50.");
        if ("ETICHETTA".equals(envelope.has("purpose") ? envelope.get("purpose").getAsString() : "")
                && pages != 1)
            throw new IllegalArgumentException("Un’etichetta deve avere una sola pagina.");
        String layout = envelope.has("layout") ? envelope.get("layout").getAsString() : "flow";
        if (!Set.of("flow", "canvas").contains(layout))
            throw new IllegalArgumentException("Disposizione del documento non valida.");
        var children = envelope.getAsJsonObject("content").getAsJsonArray("content");
        if (children == null) return;
        var ids = new java.util.HashSet<String>();
        var groups = new java.util.HashMap<String, java.util.List<Integer>>();
        for (var child : children) {
            var node = child.getAsJsonObject();
            boolean object = "canvasObject".equals(node.get("type").getAsString());
            if (!layout.equals("canvas")) {
                if (object) throw new IllegalArgumentException("Disposizione oggetti non valida.");
                continue;
            }
            if (!object)
                throw new IllegalArgumentException(
                        "Tutti gli elementi devono appartenere a un oggetto.");
            var a = node.getAsJsonObject("attrs");
            String id = a.get("id").getAsString();
            if (!id.matches("obj-[a-zA-Z0-9_-]{1,64}") || !ids.add(id))
                throw new IllegalArgumentException(
                        "Identificatore oggetto duplicato o non valido.");
            if (!Set.of("text", "field", "image", "matrix", "table", "block", "group")
                    .contains(a.get("kind").getAsString()))
                throw new IllegalArgumentException("Tipo oggetto non valido.");
            var content = node.getAsJsonArray("content");
            if (a.get("kind").getAsString().equals("field")
                    && (content == null
                            || content.size() != 1
                            || !"labelValue"
                                    .equals(
                                            content.get(0)
                                                    .getAsJsonObject()
                                                    .get("type")
                                                    .getAsString())))
                throw new IllegalArgumentException(
                        "Un dato automatico deve contenere un solo campo.");
            if (content != null && !a.get("kind").getAsString().equals("field"))
                for (var item : content)
                    if ("labelValue".equals(item.getAsJsonObject().get("type").getAsString()))
                        throw new IllegalArgumentException(
                                "Il dato automatico deve essere autonomo.");
            double x = number(a, "x"),
                    y = number(a, "y"),
                    w = number(a, "width"),
                    h = number(a, "height");
            int p = integer(a, "page"), z = integer(a, "z");
            if (a.has("groupId")) {
                if (!a.getAsJsonPrimitive("groupId").isString())
                    throw new IllegalArgumentException("Identificatore del gruppo non valido.");
                String group = a.get("groupId").getAsString();
                if (!group.isEmpty()) {
                    if (!group.matches("grp-[a-zA-Z0-9_-]{1,64}")
                            || envelope.get("schemaVersion").getAsInt() < 7)
                        throw new IllegalArgumentException("Identificatore del gruppo non valido.");
                    groups.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(p);
                }
            }
            if (x < 0
                    || y < 0
                    || w < 8
                    || h < 8
                    || x + w > paper.width() * 96 / 25.4 + .1
                    || y + h > paper.height() * 96 / 25.4 + .1
                    || p < 0
                    || p >= pages
                    || z < 0
                    || z > 1_000_000)
                throw new IllegalArgumentException(
                        "Un oggetto supera i limiti della pagina oppure ha dimensioni non valide.");
            if (!Set.of("Arial", "Georgia", "Verdana", "Times New Roman")
                            .contains(a.get("fontFamily").getAsString())
                    || !a.get("fontSize").getAsString().matches("[0-9]+(\\.[0-9]+)?pt")
                    || !a.get("color").getAsString().matches("#[a-fA-F0-9]{6}")
                    || !Set.of("left", "center", "right", "justify")
                            .contains(a.get("textAlign").getAsString()))
                throw new IllegalArgumentException("Stile del riquadro non valido.");
            double font = Double.parseDouble(a.get("fontSize").getAsString().replace("pt", "")),
                    line = number(a, "lineHeight");
            if (font < 3 || font > 200 || line < .8 || line > 4)
                throw new IllegalArgumentException("Dimensione carattere o interlinea non valida.");
            for (String name : Set.of("bold", "italic", "underline", "keepRatio"))
                if (!a.getAsJsonPrimitive(name).isBoolean())
                    throw new IllegalArgumentException("Stile del riquadro non valido.");
        }
        for (var members : groups.values())
            if (members.size() < 2 || members.stream().distinct().count() != 1)
                throw new IllegalArgumentException(
                        "Un gruppo deve contenere almeno due elementi della stessa pagina.");
    }

    private static double number(JsonObject a, String key) {
        if (!a.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("Coordinate non valide.");
        double value = a.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Coordinate non valide.");
        return value;
    }

    private static int integer(JsonObject a, String key) {
        number(a, key);
        return a.get(key).getAsBigDecimal().intValueExact();
    }
}

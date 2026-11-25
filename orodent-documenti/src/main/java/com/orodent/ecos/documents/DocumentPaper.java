package com.orodent.ecos.documents;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Physical page dimensions belong to the immutable document version. */
public record DocumentPaper(double width, double height, double margin) {
    public DocumentPaper {
        if (!Double.isFinite(width)
                || !Double.isFinite(height)
                || !Double.isFinite(margin)
                || width < 20
                || height < 20
                || width > 1000
                || height > 1000
                || margin < 0
                || margin > 40
                || margin * 2 >= Math.min(width, height))
            throw new IllegalArgumentException(
                    "Formato carta non valido: dimensioni da 20 a 1000 mm e margini da 0 a 40 mm.");
    }

    public static DocumentPaper from(String content) {
        return from(JsonParser.parseString(content).getAsJsonObject().getAsJsonObject("page"));
    }

    public static DocumentPaper from(JsonObject page) {
        String format = page.has("format") ? page.get("format").getAsString() : "a4";
        String orientation = page.get("orientation").getAsString();
        if (!orientation.equals("portrait") && !orientation.equals("landscape"))
            throw new IllegalArgumentException("Orientamento carta non valido.");
        boolean landscape = orientation.equals("landscape");
        return switch (format) {
            case "a4" ->
                    new DocumentPaper(
                            landscape ? 297 : 210,
                            landscape ? 210 : 297,
                            page.get("margin").getAsDouble());
            case "custom" ->
                    new DocumentPaper(
                            page.get("width").getAsDouble(),
                            page.get("height").getAsDouble(),
                            page.get("margin").getAsDouble());
            default -> throw new IllegalArgumentException("Formato carta non supportato.");
        };
    }
}

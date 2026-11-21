package com.orodent.ecos.ton.features.catalog.bulk.model;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels;

import java.math.BigDecimal;
import java.util.*;

/** Typed code pieces shared by previews and the visual editor; no formula language. */
public record CodeComposition(List<Part> parts, String separator) {
    public enum Type {
        LINE("Linea"),
        COLOR("Colore"),
        DIAMETER("Diametro"),
        HEIGHT("Altezza"),
        PRODUCT("Prodotto"),
        TEXT("Testo fisso");
        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Part(Type type, String text) {
        public Part {
            Objects.requireNonNull(type);
            text = CatalogModels.text(text);
        }

        public Part(Type type) {
            this(type, "");
        }

        public String label() {
            return type == Type.TEXT ? text : type.label();
        }
    }

    public record Values(String line, String color, BigDecimal diameter, BigDecimal height) {}

    public CodeComposition {
        parts = List.copyOf(parts);
        separator = Objects.requireNonNullElse(separator, "");
        if (parts.isEmpty() || parts.size() > 20)
            throw new IllegalArgumentException("Scegli da 1 a 20 parti per il codice.");
        if (!List.of("", "-", "_").contains(separator))
            throw new IllegalArgumentException("Separatore non valido.");
        if (parts.stream().anyMatch(p -> p.type() == Type.TEXT && p.text().isBlank()))
            throw new IllegalArgumentException("Compila il testo fisso o rimuovi la parte vuota.");
    }

    public static CodeComposition defaults(CatalogKind kind) {
        return new CodeComposition(
                (kind == CatalogKind.PRODUCTS
                                ? List.of(Type.LINE, Type.COLOR)
                                : List.of(Type.DIAMETER, Type.HEIGHT, Type.PRODUCT))
                        .stream().map(Part::new).toList(),
                "");
    }

    public static List<Type> available(CatalogKind kind) {
        return kind == CatalogKind.PRODUCTS
                ? List.of(Type.LINE, Type.COLOR, Type.TEXT)
                : List.of(Type.DIAMETER, Type.HEIGHT, Type.PRODUCT, Type.TEXT);
    }

    public String code(Values values) {
        var codes = new ArrayList<String>();
        for (Part part : parts) {
            String value = value(part, values);
            // Only an explicit leading literal can move a prefix out of the product token.
            if (part.type() == Type.PRODUCT && parts.getFirst().type() == Type.TEXT) {
                String prefix = parts.getFirst().text().toUpperCase(Locale.ROOT);
                if (value.toUpperCase(Locale.ROOT).startsWith(prefix))
                    value = value.substring(prefix.length());
            }
            if (!value.isEmpty()) codes.add(value);
        }
        return String.join(separator, codes).toUpperCase(Locale.ROOT);
    }

    public static String value(Part part, Values v) {
        String value =
                switch (part.type()) {
                    case LINE -> v.line();
                    case PRODUCT -> articleProduct(v);
                    case COLOR ->
                            "SENZA_COLORE".equals(CatalogModels.commercialColor(v.color()))
                                    ? ""
                                    : v.color();
                    case DIAMETER -> number(v.diameter());
                    case HEIGHT -> number(v.height());
                    case TEXT -> part.text();
                };
        value = CatalogModels.text(value);
        if (value.isBlank() && part.type() != Type.COLOR)
            throw new IllegalArgumentException(
                    "Manca il valore di «"
                            + part.type().label()
                            + "». Completa i dati o modifica la composizione.");
        return value;
    }

    /** Article labels use line/color data; the stored product code and identity are unchanged. */
    private static String articleProduct(Values values) {
        String line = CatalogModels.text(values.line()).toUpperCase(Locale.ROOT);
        return CatalogModels.productCode(line, values.color());
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}

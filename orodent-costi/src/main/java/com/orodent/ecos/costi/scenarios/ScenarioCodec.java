package com.orodent.ecos.costi.scenarios;

import com.google.gson.*;
import com.google.gson.stream.*;
import com.orodent.ecos.costi.products.model.PowderPricing.Selection;
import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.scenarios.Scenarios.*;

import java.io.*;
import java.time.*;
import java.util.*;

/** Versioned JSON records, not Java object deserialization. Bases are stored once per snapshot. */
public final class ScenarioCodec {
    private static final int LIMIT = 32 * 1024 * 1024;
    private final Gson gson =
            new GsonBuilder()
                    .registerTypeAdapter(
                            Instant.class,
                            new TextAdapter<Instant>() {
                                Instant parse(String s) {
                                    return Instant.parse(s);
                                }
                            })
                    .registerTypeAdapter(
                            LocalDate.class,
                            new TextAdapter<LocalDate>() {
                                LocalDate parse(String s) {
                                    return LocalDate.parse(s);
                                }
                            })
                    .create();

    private abstract static class TextAdapter<T> extends TypeAdapter<T> {
        abstract T parse(String s);

        public void write(JsonWriter out, T value) throws IOException {
            if (value == null) out.nullValue();
            else out.value(value.toString());
        }

        public T read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return parse(in.nextString());
        }
    }

    private record BaseFile(int format, Base base) {}

    private record Edits(
            int format,
            UUID id,
            int version,
            String name,
            String notes,
            boolean archived,
            Selection selection,
            Set<Long> products,
            Map<Long, PriceChange> prices,
            Map<Long, Recipe> recipes,
            Map<Long, Model> models,
            Map<Long, Parameters> transforms) {}

    public String base(Base base) {
        return checked(gson.toJson(new BaseFile(1, base)));
    }

    public Base base(String json) {
        var file = gson.fromJson(checked(json), BaseFile.class);
        if (file == null || file.format() != 1 || file.base() == null)
            throw new IllegalArgumentException("Formato fotografia scenario non supportato.");
        return file.base();
    }

    public String edits(Draft d) {
        return checked(
                gson.toJson(
                        new Edits(
                                1,
                                d.id(),
                                d.version(),
                                d.name(),
                                d.notes(),
                                d.archived(),
                                d.selection(),
                                d.products(),
                                d.prices(),
                                d.recipes(),
                                d.models(),
                                d.transforms())));
    }

    public Draft edits(String json, Base base) {
        var e = gson.fromJson(checked(json), Edits.class);
        if (e == null || e.format() != 1)
            throw new IllegalArgumentException("Formato scenario non supportato.");
        return new Draft(
                e.id(),
                e.version(),
                e.name(),
                e.notes(),
                e.archived(),
                base,
                e.selection(),
                e.products(),
                e.prices(),
                e.recipes(),
                e.models(),
                e.transforms());
    }

    private static String checked(String json) {
        if (json == null || json.length() > LIMIT)
            throw new IllegalArgumentException(
                    "Fotografia scenario troppo grande o non leggibile (massimo 32 MiB di testo).");
        return json;
    }
}

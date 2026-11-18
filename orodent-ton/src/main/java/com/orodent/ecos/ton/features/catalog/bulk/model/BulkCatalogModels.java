package com.orodent.ecos.ton.features.catalog.bulk.model;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.Entry;

import java.util.*;

public final class BulkCatalogModels {
    private BulkCatalogModels() {}

    public static final int MAX_ROWS = 1000, PAGE_SIZE = 50;

    public record Parent(
            long id,
            CatalogKind kind,
            String code,
            String name,
            long lineId,
            String lineCode,
            String material,
            String color,
            int revision,
            int lineRevision,
            boolean available) {
        public Parent {
            name = CatalogModels.text(name);
            material = CatalogModels.text(material);
        }

        @Override
        public String toString() {
            return code + (name.isBlank() ? "" : " · " + name);
        }
    }

    public record ParentPage(List<Parent> rows, boolean more, int offset) {
        public ParentPage {
            rows = List.copyOf(rows);
        }
    }

    /** Parent identity is explicit and independent of the editable article code. */
    public record Seed(
            int key,
            Parent parent,
            String code,
            String color,
            String diameter,
            String height,
            boolean selected) {
        public Seed {
            code = Objects.requireNonNullElse(code, "");
            color = Objects.requireNonNullElse(color, "");
            diameter = Objects.requireNonNullElse(diameter, "");
            height = Objects.requireNonNullElse(height, "");
        }
    }

    public record Request(CatalogKind kind, String notes, List<Seed> rows) {
        public Request {
            if (kind != CatalogKind.PRODUCTS && kind != CatalogKind.ARTICLES)
                throw new IllegalArgumentException("Seleziona prodotti o articoli.");
            notes = Objects.requireNonNullElse(notes, "");
            rows = List.copyOf(rows);
            if (rows.size() > MAX_ROWS)
                throw new IllegalArgumentException(
                        "Genera al massimo " + MAX_ROWS + " righe per gruppo.");
            if (rows.stream().map(Seed::key).distinct().count() != rows.size())
                throw new IllegalArgumentException("Righe non univoche.");
        }
    }

    public enum State {
        NEW,
        EXISTING,
        INVALID,
        EXCLUDED
    }

    public record RowResult(int key, State state, String message, Long existingId) {}

    public record Preview(Request request, List<RowResult> rows) {
        public Preview {
            rows = List.copyOf(rows);
        }

        public long creating() {
            return rows.stream().filter(r -> r.state() == State.NEW).count();
        }

        public long existing() {
            return rows.stream().filter(r -> r.state() == State.EXISTING).count();
        }

        public long invalid() {
            return rows.stream().filter(r -> r.state() == State.INVALID).count();
        }

        public boolean savable() {
            return creating() > 0 && invalid() == 0;
        }
    }

    public record Created(List<Entry> entries, long skipped) {
        public Created {
            entries = List.copyOf(entries);
        }
    }
}

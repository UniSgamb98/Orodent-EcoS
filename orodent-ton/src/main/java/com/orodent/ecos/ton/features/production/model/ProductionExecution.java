package com.orodent.ecos.ton.features.production.model;

import java.time.LocalDate;
import java.util.*;

/** Declared physical output, kept separate from the frozen preparation quantities. */
public final class ProductionExecution {
    private ProductionExecution() {}

    public record Row(int number, Production.Format format, int produced) {
        public int remaining() {
            return format.quantity() - produced;
        }
    }

    public record Workspace(Production.Saved prepared, List<Row> rows) {
        public Workspace {
            rows = List.copyOf(rows);
        }

        public int remaining() {
            return rows.stream().mapToInt(Row::remaining).sum();
        }
    }

    public record Request(LocalDate date, Map<Long, Integer> quantities, String notes) {
        public Request {
            if (date == null) throw new IllegalArgumentException("Indica la data di produzione.");
            notes = Objects.requireNonNullElse(notes, "").strip();
            if (notes.length() > 1000)
                throw new IllegalArgumentException("Note: massimo 1000 caratteri.");
            quantities = Collections.unmodifiableMap(new TreeMap<>(quantities));
            long total = 0;
            for (var e : quantities.entrySet()) {
                if (e.getKey() <= 0 || e.getValue() == null || e.getValue() < 0)
                    throw new IllegalArgumentException("Usa quantità intere non negative.");
                total += e.getValue();
            }
            if (total == 0 || total > 10000)
                throw new IllegalArgumentException("Registra da 1 a 10000 dischi per volta.");
        }
    }

    public record Receipt(Production.Saved prepared, int count, long executionId) {}
}

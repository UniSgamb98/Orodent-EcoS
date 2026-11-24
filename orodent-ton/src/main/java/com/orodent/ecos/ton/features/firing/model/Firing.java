package com.orodent.ecos.ton.features.firing.model;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Individual green discs and their immutable furnace/lot assignment. Batch is assigned later. */
public final class Firing {
    private Firing() {}

    public record Disc(
            long id,
            long executionId,
            long planId,
            int row,
            int ordinal,
            String article,
            String product,
            String line,
            String color,
            BigDecimal diameter,
            BigDecimal height,
            LocalDate producedAt) {
        public String identity() {
            return "D-" + String.format(Locale.ROOT, "%08d", id);
        }

        public String production() {
            return "PR-" + String.format(Locale.ROOT, "%06d", planId);
        }

        public String format() {
            return number(diameter) + " × " + number(height) + " mm";
        }
    }

    public record Furnace(long id, int revision, String code, String name) {
        @Override
        public String toString() {
            return code + " · " + name;
        }
    }

    public record Load(
            long id,
            long furnaceId,
            String furnaceCode,
            String furnaceName,
            int furnaceRevision,
            String lot,
            LocalDate date,
            LocalDateTime recordedAt,
            String notes,
            int count) {
        public String number() {
            return "CF-" + String.format(Locale.ROOT, "%06d", id);
        }

        public String furnace() {
            return furnaceCode + " · " + furnaceName;
        }
    }

    public record Workspace(List<Disc> pending, List<Furnace> furnaces, List<Load> loads) {
        public Workspace {
            pending = List.copyOf(pending);
            furnaces = List.copyOf(furnaces);
            loads = List.copyOf(loads);
        }
    }

    public record Request(
            Furnace furnace, String lot, LocalDate date, String notes, List<Long> discs) {
        public Request {
            if (furnace == null || furnace.id() <= 0)
                throw new IllegalArgumentException("Scegli un forno attivo.");
            lot = Objects.requireNonNullElse(lot, "").strip().toUpperCase(Locale.ROOT);
            if (lot.isEmpty() || lot.length() > 50)
                throw new IllegalArgumentException("Lotto: inserisci da 1 a 50 caratteri.");
            if (date == null) throw new IllegalArgumentException("Indica la data del carico.");
            notes = Objects.requireNonNullElse(notes, "").strip();
            if (notes.length() > 1000)
                throw new IllegalArgumentException("Note: massimo 1000 caratteri.");
            discs = List.copyOf(discs);
            if (discs.isEmpty()
                    || discs.size() > 10000
                    || discs.stream().anyMatch(id -> id <= 0)
                    || new HashSet<>(discs).size() != discs.size())
                throw new IllegalArgumentException(
                        "Seleziona da 1 a 10000 dischi distinti da caricare.");
        }
    }

    public static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}

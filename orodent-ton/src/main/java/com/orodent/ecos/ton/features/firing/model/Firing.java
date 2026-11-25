package com.orodent.ecos.ton.features.firing.model;

import com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms;
import com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms.Program;

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

    public record GroupKey(long planId, int row) {}

    public record Group(GroupKey key, Disc reference, int count) {}

    public record Quantity(GroupKey key, int count) {
        public Quantity {
            if (key == null || key.planId() <= 0 || key.row() <= 0 || count <= 0)
                throw new IllegalArgumentException(
                        "Indica una quantità positiva per ogni articolo scelto.");
        }
    }

    public static List<Group> group(List<Disc> discs) {
        var grouped = new LinkedHashMap<GroupKey, Group>();
        for (var disc : discs) {
            var key = new GroupKey(disc.planId(), disc.row());
            var previous = grouped.get(key);
            grouped.put(
                    key,
                    new Group(
                            key,
                            previous == null ? disc : previous.reference(),
                            previous == null ? 1 : previous.count() + 1));
        }
        return List.copyOf(grouped.values());
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
            int count,
            Program program,
            BigDecimal finalTemperature,
            Program previousProgram) {
        public String number() {
            return "CF-" + String.format(Locale.ROOT, "%06d", id);
        }

        public String furnace() {
            return furnaceCode + " · " + furnaceName;
        }

        public boolean programChanged() {
            return previousProgram != null
                    && program != null
                    && previousProgram.id() != program.id();
        }

        public List<FiringPrograms.Step> resolvedSteps() {
            return program == null || finalTemperature == null
                    ? List.of()
                    : program.resolve(finalTemperature);
        }

        public LocalDate availableDate() {
            return Firing.availableDate(date, program);
        }
    }

    public record Workspace(
            List<Disc> pending, List<Furnace> furnaces, List<Load> loads, List<Program> programs) {
        public Workspace {
            pending = List.copyOf(pending);
            furnaces = List.copyOf(furnaces);
            loads = List.copyOf(loads);
            programs = List.copyOf(programs);
        }

        public Map<Long, Program> lastPrograms() {
            var result = new HashMap<Long, Program>();
            loads.stream()
                    .sorted(
                            Comparator.comparing(Load::recordedAt)
                                    .thenComparingLong(Load::id)
                                    .reversed())
                    .filter(l -> l.program() != null)
                    .forEach(l -> result.putIfAbsent(l.furnaceId(), l.program()));
            return Map.copyOf(result);
        }

        /** Date-only planning: partial days occupy the whole day; warnings never reject loads. */
        public List<Load> overlappingLoads(Furnace furnace, LocalDate start, Program program) {
            if (furnace == null || start == null) return List.of();
            LocalDate end = availableDate(start, program);
            return loads.stream()
                    .filter(l -> l.furnaceId() == furnace.id())
                    .filter(
                            l ->
                                    l.availableDate() != null
                                            && l.availableDate().isAfter(l.date())
                                            && l.availableDate().isAfter(start))
                    .filter(
                            l ->
                                    end == null || end.equals(start)
                                            ? !l.date().isAfter(start)
                                            : l.date().isBefore(end))
                    .sorted(Comparator.comparing(Load::date).thenComparingLong(Load::id))
                    .toList();
        }
    }

    public record Request(
            Furnace furnace,
            String lot,
            LocalDate date,
            String notes,
            List<Quantity> quantities,
            Program program,
            BigDecimal finalTemperature) {
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
            quantities = List.copyOf(quantities);
            if (quantities.isEmpty()
                    || quantities.stream().mapToLong(Quantity::count).sum() > 10000
                    || quantities.stream().map(Quantity::key).distinct().count()
                            != quantities.size())
                throw new IllegalArgumentException(
                        "Indica da 1 a 10000 dischi da caricare, senza ripetere gli articoli della"
                            + " stessa produzione.");
            if (program == null || program.id() <= 0)
                throw new IllegalArgumentException("Scegli un programma di cottura approvato.");
            finalTemperature = FiringPrograms.requireTemperature(finalTemperature);
        }
    }

    public static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    public static LocalDate availableDate(LocalDate start, Program program) {
        if (start == null || program == null || program.steps().isEmpty()) return null;
        long minutes = program.steps().stream().mapToLong(FiringPrograms.Step::minutes).sum();
        return start.plusDays((minutes + 1439) / 1440);
    }
}

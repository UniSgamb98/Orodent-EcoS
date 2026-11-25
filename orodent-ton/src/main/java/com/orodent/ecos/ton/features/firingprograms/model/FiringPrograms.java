package com.orodent.ecos.ton.features.firingprograms.model;

import java.math.*;
import java.time.LocalDateTime;
import java.util.*;

/** Thermal setpoints and exact integer durations. Displayed h.mm is never a decimal hour. */
public final class FiringPrograms {
    private FiringPrograms() {}

    public record Step(BigDecimal temperature, int rampMinutes, int holdMinutes) {
        public Step {
            try {
                temperature =
                        Objects.requireNonNull(temperature).setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException | NullPointerException invalid) {
                throw new IllegalArgumentException(
                        "Temperatura: inserisci un valore con massimo due decimali.");
            }
            if (temperature.signum() <= 0 || temperature.precision() > 7)
                throw new IllegalArgumentException(
                        "Temperatura finale: usa un valore positivo inferiore a 100000 °C.");
            if (rampMinutes < 0 || holdMinutes < 0)
                throw new IllegalArgumentException("I tempi non possono essere negativi.");
            temperature = temperature.stripTrailingZeros();
        }

        public long minutes() {
            return (long) rampMinutes + holdMinutes;
        }
    }

    public record Entry(long id, String code, int token, Snapshot latest) {}

    public record Snapshot(
            long id,
            long familyId,
            int number,
            int revision,
            String state,
            String name,
            String notes,
            boolean active,
            List<Step> steps,
            LocalDateTime savedAt,
            boolean current) {
        public Snapshot {
            steps = List.copyOf(steps);
        }

        public boolean editable() {
            return current && state.equals("BOZZA");
        }

        public String label() {
            return "v" + number + " · r" + revision;
        }

        public String stateLabel() {
            return switch (state) {
                case "APPROVATA" -> "Approvata";
                case "RITIRATA" -> "Ritirata";
                default -> "Bozza";
            };
        }

        public long minutes() {
            return steps.stream().mapToLong(Step::minutes).sum();
        }
    }

    public record Detail(Entry entry, Snapshot selected, List<Snapshot> versions) {
        public Detail {
            versions = List.copyOf(versions);
        }
    }

    public record Draft(String code, String name, String notes, boolean active, List<Step> steps) {
        public Draft {
            code =
                    required(
                            Objects.requireNonNullElse(code, "").toUpperCase(Locale.ROOT),
                            "Codice",
                            80);
            name = required(name, "Nome", 200);
            notes = Objects.requireNonNullElse(notes, "").strip();
            if (notes.length() > 1000)
                throw new IllegalArgumentException("Note: massimo 1000 caratteri.");
            steps = List.copyOf(steps);
            if (steps.size() > 200)
                throw new IllegalArgumentException("Sono ammessi al massimo 200 step.");
        }

        public void requireApprovable() {
            if (steps.isEmpty())
                throw new IllegalArgumentException(
                        "Aggiungi almeno uno step prima di approvare il programma.");
        }
    }

    private static String required(String text, String label, int max) {
        var value = Objects.requireNonNullElse(text, "").strip();
        if (value.isEmpty() || value.length() > max)
            throw new IllegalArgumentException(label + ": inserisci da 1 a " + max + " caratteri.");
        return value;
    }

    public static int parseTime(String text, String label) {
        String value = Objects.requireNonNullElse(text, "").strip();
        if (!value.matches("[0-9]+(?:\\.[0-5][0-9])?"))
            throw new IllegalArgumentException(
                    label + ": usa ore.minuti, per esempio 1.30 oppure 0.05 (minuti da 00 a 59).");
        String[] parts = value.split("\\.");
        try {
            long minutes =
                    Math.addExact(
                            Math.multiplyExact(Long.parseLong(parts[0]), 60),
                            parts.length == 1 ? 0 : Integer.parseInt(parts[1]));
            return Math.toIntExact(minutes);
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IllegalArgumentException(label + ": durata troppo grande.");
        }
    }

    public static String time(long minutes) {
        if (minutes < 0) throw new IllegalArgumentException("Durata negativa.");
        return String.format(Locale.ROOT, "%d.%02d", minutes / 60, minutes % 60);
    }

    public static final class Conflict extends IllegalStateException {
        public Conflict() {
            super(
                    "Il programma è stato modificato da un’altra postazione. Aggiorna i dati prima"
                        + " di salvare.");
        }
    }
}

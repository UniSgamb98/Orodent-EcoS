package com.orodent.ecos.ton.features.availability.model;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

public final class Availability {
    private Availability() {}

    public enum Kind {
        DISPONIBILITA("Disponibilità"),
        ORDINI("Ordini da evadere");
        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Period {
        DAYS_30(30),
        DAYS_90(90),
        DAYS_180(180),
        DAYS_365(365);
        private final int days;

        Period(int days) {
            this.days = days;
        }

        public int days() {
            return days;
        }

        @Override
        public String toString() {
            return "Ultimi " + days + " giorni";
        }
    }

    public record Article(
            long id,
            String code,
            long productId,
            String productCode,
            String color,
            Long lineId,
            String lineCode,
            String lineName,
            BigDecimal diameter,
            BigDecimal height,
            boolean active) {
        public String lineLabel() {
            return lineName == null || lineName.isBlank() ? lineCode : lineName;
        }
    }

    public record Row(Article article, BigDecimal stock, BigDecimal orders, BigDecimal sold) {}

    public record Source(
            Kind kind,
            int revision,
            Long importId,
            String file,
            LocalDate date,
            LocalDateTime imported,
            boolean missingZero,
            Map<String, BigDecimal> values) {
        public Source {
            values = Map.copyOf(values);
        }

        public boolean present() {
            return importId != null;
        }
    }

    public record Snapshot(
            List<Row> rows,
            Map<Kind, Source> sources,
            LocalDate from,
            LocalDate through,
            LocalDate lastSale,
            int unmatchedStock,
            int unmatchedOrders) {
        public Snapshot {
            rows = List.copyOf(rows);
            sources = Map.copyOf(sources);
        }

        public BigDecimal total(Function<Row, BigDecimal> amount) {
            var known = rows.stream().map(amount).filter(Objects::nonNull).toList();
            return known.isEmpty() ? null : known.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public enum Numbers {
        ITALIAN("Virgola decimale: 1.234,5"),
        POINT("Punto decimale: 1234.5");
        private final String label;

        Numbers(String label) {
            this.label = label;
        }

        public BigDecimal parse(String text) {
            String s = Objects.requireNonNullElse(text, "").strip();
            if (this == ITALIAN) {
                if (!s.matches("[+-]?(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)(?:,[0-9]+)?"))
                    throw new IllegalArgumentException("Quantità non valida: " + text);
                s = s.replace(".", "").replace(',', '.');
            } else if (!s.matches("[+-]?[0-9]+(?:\\.[0-9]+)?"))
                throw new IllegalArgumentException("Quantità non valida: " + text);
            return quantity(new BigDecimal(s));
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Preview(
            Kind kind,
            int expectedRevision,
            String file,
            LocalDate date,
            boolean missingZero,
            Map<String, BigDecimal> values,
            int inputRows,
            int duplicates,
            Map<String, String> matches,
            List<String> errors) {
        public Preview {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(date);
            file = Objects.requireNonNull(file);
            if (file.isBlank() || file.length() > 300)
                throw new IllegalArgumentException("Nome file non valido.");
            var checked = new LinkedHashMap<String, BigDecimal>();
            values.forEach(
                    (code, value) -> {
                        String key = code(code);
                        BigDecimal n = quantity(value);
                        if (kind == Kind.ORDINI && n.signum() < 0)
                            throw new IllegalArgumentException(
                                    "Gli ordini da evadere non possono essere negativi.");
                        if (checked.put(key, n) != null)
                            throw new IllegalArgumentException("Codice duplicato: " + key);
                    });
            values = Collections.unmodifiableMap(checked);
            matches = Map.copyOf(matches);
            errors = List.copyOf(errors);
            if (inputRows < 0 || expectedRevision < 1)
                throw new IllegalArgumentException("Anteprima non valida.");
        }

        public long unmatched() {
            return values.keySet().stream().filter(k -> !matches.containsKey(k)).count();
        }
    }

    public static String code(String s) {
        String value = Objects.requireNonNullElse(s, "").strip().toUpperCase(Locale.ROOT);
        if (value.isBlank() || value.length() > 80)
            throw new IllegalArgumentException("Codice obbligatorio, massimo 80 caratteri.");
        return value;
    }

    public static BigDecimal quantity(BigDecimal n) {
        if (n == null) throw new IllegalArgumentException("Quantità mancante.");
        try {
            if (n.setScale(3, RoundingMode.UNNECESSARY).precision() > 15)
                throw new ArithmeticException();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Quantità troppo grande o con più di 3 decimali.");
        }
        return n.stripTrailingZeros();
    }

    public static final class Conflict extends IllegalArgumentException {
        public Conflict() {
            super(
                    "Questa fonte è stata aggiornata da un altro operatore. Ricarica l’anteprima"
                        + " prima di importare.");
        }
    }
}

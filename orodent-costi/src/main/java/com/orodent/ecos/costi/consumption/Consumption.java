package com.orodent.ecos.costi.consumption;

import com.orodent.ecos.costi.products.model.ProductCosts.*;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Immutable manual-stock and theoretical-consumption snapshots. All masses are kg. */
public final class Consumption {
    private Consumption() {}

    public enum Basis {
        PRODUZIONI,
        VENDITE,
        CONFRONTO;

        @Override
        public String toString() {
            return switch (this) {
                case PRODUZIONI -> "Da produzioni";
                case VENDITE -> "Da vendite";
                case CONFRONTO -> "Confronto";
            };
        }
    }

    public enum CalendarMode {
        CALENDARIO,
        LUNEDI_VENERDI;

        public boolean includes(LocalDate date) {
            return this == CALENDARIO || date.getDayOfWeek().getValue() <= 5;
        }

        @Override
        public String toString() {
            return this == CALENDARIO
                    ? "Giorni di calendario"
                    : "Lunedì–venerdì (festività incluse)";
        }
    }

    public enum Metric {
        CONSUMO,
        DISPONIBILITA
    }

    public enum Unit {
        KG,
        GIORNI
    }

    public record Settings(int version, Unit unit, BigDecimal value) {
        public Settings {
            Objects.requireNonNull(unit);
            Objects.requireNonNull(value);
            if (version < 0
                    || value.signum() < 0
                    || value.setScale(3, RoundingMode.UNNECESSARY).precision() > 15)
                throw new IllegalArgumentException(
                        "Scorta minima: valore non negativo, massimo 3 decimali.");
        }
    }

    public record Powder(
            long id,
            String code,
            String name,
            String color,
            BigDecimal stock,
            LocalDate stockDate,
            Settings settings) {}

    public record Movement(
            LocalDate date,
            Basis basis,
            long articleId,
            Long specificationId,
            BigDecimal quantity) {}

    public record Arrival(
            long orderId,
            long powderId,
            LocalDate expected,
            BigDecimal quantity,
            String order,
            int position) {}

    public record Snapshot(
            Catalogue catalogue,
            List<Powder> powders,
            List<Movement> movements,
            List<String> warnings,
            Set<Long> approved,
            List<Arrival> arrivals) {
        public Snapshot(
                Catalogue c, List<Powder> p, List<Movement> m, List<String> w, Set<Long> a) {
            this(c, p, m, w, a, List.of());
        }

        public Snapshot {
            powders = List.copyOf(powders);
            movements = List.copyOf(movements);
            warnings = List.copyOf(warnings);
            approved = Set.copyOf(approved);
            arrivals = List.copyOf(arrivals);
        }
    }

    public record Request(
            LocalDate from,
            LocalDate through,
            LocalDate today,
            int horizon,
            CalendarMode calendar,
            boolean includeArrivals) {
        public Request(
                LocalDate from,
                LocalDate through,
                LocalDate today,
                int horizon,
                CalendarMode calendar) {
            this(from, through, today, horizon, calendar, false);
        }

        public Request {
            if (from == null || through == null || today == null || calendar == null)
                throw new IllegalArgumentException(
                        "Seleziona le date del periodo e il calendario.");
            if (through.isBefore(from)
                    || !through.isBefore(today)
                    || ChronoUnit.DAYS.between(from, through) > 1095
                    || horizon < 1
                    || horizon > 365)
                throw new IllegalArgumentException(
                        "Usa un periodo storico concluso di massimo 3 anni e una previsione da 1 a"
                                + " 365 giorni.");
        }
    }

    public record Series(
            Powder powder,
            Basis basis,
            NavigableMap<LocalDate, BigDecimal> daily,
            BigDecimal rate,
            BigDecimal projected,
            boolean complete,
            NavigableMap<LocalDate, BigDecimal> arrivals) {
        public Series(
                Powder p,
                Basis b,
                NavigableMap<LocalDate, BigDecimal> d,
                BigDecimal r,
                BigDecimal projected,
                boolean complete) {
            this(p, b, d, r, projected, complete, new TreeMap<>());
        }

        public Series {
            daily = Collections.unmodifiableNavigableMap(new TreeMap<>(daily));
            arrivals = Collections.unmodifiableNavigableMap(new TreeMap<>(arrivals));
        }
    }

    public record Analysis(
            Snapshot snapshot, Request request, List<Series> series, List<String> warnings) {
        public Analysis {
            series = List.copyOf(series);
            warnings = List.copyOf(warnings);
        }
    }

    public record PlanLine(long articleId, long specificationId, int quantity) {
        public PlanLine {
            if (quantity < 1 || quantity > 1_000_000)
                throw new IllegalArgumentException("Quantità: da 1 a 1.000.000 articoli.");
        }
    }

    public record Contribution(
            int row, String article, long specification, int layer, String powder, BigDecimal kg) {}

    public record Planned(Map<Long, BigDecimal> required, List<Contribution> details) {
        public Planned {
            required = Map.copyOf(required);
            details = List.copyOf(details);
        }
    }

    public record Balance(
            Powder powder,
            Basis basis,
            BigDecimal current,
            BigDecimal required,
            BigDecimal residual,
            BigDecimal rate,
            BigDecimal beforeDays,
            BigDecimal afterDays,
            BigDecimal minimumKg,
            BigDecimal buy,
            BigDecimal daysToMinimum,
            LocalDate firstShortage) {
        public Balance(
                Powder p,
                Basis b,
                BigDecimal c,
                BigDecimal q,
                BigDecimal r,
                BigDecimal rate,
                BigDecimal before,
                BigDecimal after,
                BigDecimal min,
                BigDecimal buy,
                BigDecimal days) {
            this(p, b, c, q, r, rate, before, after, min, buy, days, null);
        }
    }

    public static long days(LocalDate from, LocalDate through, CalendarMode mode) {
        if (through.isBefore(from)) return 0;
        long total = ChronoUnit.DAYS.between(from, through) + 1;
        if (mode == CalendarMode.CALENDARIO) return total;
        long result = (total / 7) * 5;
        for (int i = 0; i < total % 7; i++) if (mode.includes(from.plusDays(i))) result++;
        return result;
    }

    public static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return denominator == null || denominator.signum() <= 0
                ? null
                : numerator.divide(denominator, 12, RoundingMode.HALF_UP);
    }

    public static BigDecimal stockOn(Series series, LocalDate date, CalendarMode calendar) {
        Powder p = series.powder();
        if (p.stock() == null || p.stockDate() == null || date.isBefore(p.stockDate())) return null;
        if (date.equals(p.stockDate())) return p.stock().add(receipts(series, date));
        if (series.rate() == null || !series.complete()) return null;
        return p.stock()
                .subtract(
                        series.rate()
                                .multiply(
                                        BigDecimal.valueOf(
                                                days(p.stockDate().plusDays(1), date, calendar))))
                .add(receipts(series, date));
    }

    public static BigDecimal receipts(Series s, LocalDate date) {
        if (s.powder().stockDate() == null || date.isBefore(s.powder().stockDate()))
            return BigDecimal.ZERO;
        return s.arrivals().subMap(s.powder().stockDate(), true, date, true).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static Balance balance(
            Series s, LocalDate today, CalendarMode mode, BigDecimal required) {
        BigDecimal current = stockOn(s, today, mode),
                residual = current == null || required == null ? null : current.subtract(required);
        Settings setting = s.powder().settings();
        BigDecimal minimum =
                setting == null
                        ? null
                        : setting.unit() == Unit.KG
                                ? setting.value()
                                : s.rate() == null ? null : setting.value().multiply(s.rate());
        BigDecimal buy =
                residual == null
                        ? null
                        : minimum == null
                                ? (setting == null ? residual.negate().max(BigDecimal.ZERO) : null)
                                : minimum.subtract(residual).max(BigDecimal.ZERO);
        return new Balance(
                s.powder(),
                s.basis(),
                current,
                required,
                residual,
                s.rate(),
                current == null ? null : divide(current.max(BigDecimal.ZERO), s.rate()),
                residual == null ? null : divide(residual.max(BigDecimal.ZERO), s.rate()),
                minimum,
                buy,
                residual == null || minimum == null
                        ? null
                        : divide(residual.subtract(minimum).max(BigDecimal.ZERO), s.rate()));
    }
}

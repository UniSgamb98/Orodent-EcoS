package com.orodent.ecos.statistiche.features.sales.projection.service;

import com.orodent.ecos.statistiche.features.sales.projection.model.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Allocates an existing annual residual; never changes the annual estimate or past actuals. */
public final class MonthlyProjectionCalculator {
    public MonthlyProjection calculate(MetricProjection annual, List<DailyMetricValue> history) {
        return calculate(annual, history, annual == null ? null : annual.dataFrom());
    }

    /** Archive coverage is independent of the current-year coverage carried by the annual estimate. */
    public MonthlyProjection calculate(MetricProjection annual, List<DailyMetricValue> history, LocalDate archiveFrom) {
        if (annual == null || annual.method() == ProjectionMethod.ACTUAL
                || annual.dataThrough().getYear() != annual.year()
                || annual.dataThrough().equals(LocalDate.of(annual.year(), 12, 31))) return null;
        LocalDate cutoff = annual.dataThrough();
        int first = cutoff.getMonthValue();
        Map<Integer, List<DailyMetricValue>> years = new TreeMap<>();
        history.forEach(value -> years.computeIfAbsent(value.date().getYear(), ignored -> new ArrayList<>()).add(value));
        List<BigDecimal[]> shares = new ArrayList<>();
        for (var entry : years.entrySet()) {
            if (entry.getKey() >= annual.year()) continue;
            LocalDate historicalCutoff = cutoff.equals(YearMonth.from(cutoff).atEndOfMonth())
                    ? YearMonth.of(entry.getKey(), first).atEndOfMonth() : cutoff.withYear(entry.getKey());
            // Incomplete archive years cannot describe a full seasonal tail.
            if (archiveFrom.isAfter(LocalDate.of(entry.getKey(), 1, 1))) continue;
            BigDecimal[] tail = zeros();
            entry.getValue().stream().filter(value -> value.date().isAfter(historicalCutoff))
                    .forEach(value -> tail[value.date().getMonthValue()] = tail[value.date().getMonthValue()].add(value.value()));
            BigDecimal total = Arrays.stream(tail).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() <= 0 || Arrays.stream(tail).anyMatch(value -> value.signum() < 0)) continue;
            for (int month = first; month <= 12; month++) tail[month] = tail[month].divide(total, 12, RoundingMode.HALF_UP);
            shares.add(tail);
        }
        boolean seasonal = shares.size() >= 2;
        BigDecimal[] weights = zeros();
        for (int month = first; month <= 12; month++) {
            if (seasonal) {
                final int index = month;
                List<BigDecimal> sorted = shares.stream().map(value -> value[index]).sorted().toList();
                int middle = sorted.size() / 2;
                weights[month] = sorted.size() % 2 == 1 ? sorted.get(middle)
                        : sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2));
            } else {
                int days = YearMonth.of(annual.year(), month).lengthOfMonth();
                weights[month] = BigDecimal.valueOf(month == first ? days - cutoff.getDayOfMonth() : days);
            }
        }
        BigDecimal weightTotal = Arrays.stream(weights).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightTotal.signum() == 0) {
            // Medians can all be zero when sparse orders fall in different months each year.
            seasonal = false;
            for (int month = first; month <= 12; month++) {
                int days = YearMonth.of(annual.year(), month).lengthOfMonth();
                weights[month] = BigDecimal.valueOf(month == first ? days - cutoff.getDayOfMonth() : days);
            }
            weightTotal = Arrays.stream(weights).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal[] actual = zeros();
        history.stream().filter(value -> value.date().getYear() == annual.year() && !value.date().isAfter(cutoff))
                .forEach(value -> actual[value.date().getMonthValue()] = actual[value.date().getMonthValue()].add(value.value()));
        int lastWeighted = 12;
        while (weights[lastWeighted].signum() == 0) lastWeighted--;
        int scale = Math.max(0, annual.projectedRemainingValue().scale());
        BigDecimal allocated = BigDecimal.ZERO;
        List<MonthlyProjection.Point> points = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            BigDecimal remaining = BigDecimal.ZERO;
            if (month >= first && weights[month].signum() > 0) {
                remaining = month == lastWeighted ? annual.projectedRemainingValue().subtract(allocated)
                        : annual.projectedRemainingValue().multiply(weights[month]).divide(weightTotal, scale, RoundingMode.DOWN);
                allocated = allocated.add(remaining);
            }
            points.add(new MonthlyProjection.Point(month, actual[month], remaining));
        }
        return new MonthlyProjection(annual, points, seasonal);
    }
    private BigDecimal[] zeros() {
        BigDecimal[] values = new BigDecimal[13];
        Arrays.fill(values, BigDecimal.ZERO);
        return values;
    }
}

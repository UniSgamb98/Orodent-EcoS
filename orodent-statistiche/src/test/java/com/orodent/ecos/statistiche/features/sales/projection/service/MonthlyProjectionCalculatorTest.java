package com.orodent.ecos.statistiche.features.sales.projection.service;

import com.orodent.ecos.statistiche.features.sales.projection.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MonthlyProjectionCalculatorTest {
    private final MonthlyProjectionCalculator calculator = new MonthlyProjectionCalculator();
    private DailyMetricValue value(int year, int month, int day, String amount) {
        return new DailyMetricValue(LocalDate.of(year, month, day), new BigDecimal(amount));
    }
    private MetricProjection annual(int year, int month, int day, String actual, String remaining) {
        BigDecimal a = new BigDecimal(actual), r = new BigDecimal(remaining);
        return new MetricProjection(year, a, r, a.add(r), LocalDate.of(2020, 1, 1), LocalDate.of(year, month, day),
                ProjectionMethod.SEASONAL, ProjectionConfidence.HIGH, 5);
    }
    private void reconciles(MonthlyProjection result) {
        assertEquals(0, result.annual().projectedRemainingValue().compareTo(result.points().stream()
                .map(MonthlyProjection.Point::remaining).reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(0, result.annual().projectedAnnualValue().compareTo(result.points().stream()
                .map(MonthlyProjection.Point::total).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }
    @Test void partialOctoberUsesOnlyHistoricalRemainingDaysAndPreservesAnnualForecast() {
        List<DailyMetricValue> history = new ArrayList<>();
        for (int year : List.of(2024, 2025)) {
            history.add(value(year, 10, 1, "9000")); // already elapsed; must not influence the tail
            history.add(value(year, 10, 8, "60"));
            history.add(value(year, 11, 1, "30"));
            history.add(value(year, 12, 1, "10"));
        }
        history.add(value(2026, 9, 1, "80.00"));
        history.add(value(2026, 10, 7, "20.00"));
        history.add(value(2026, 10, 8, "10000")); // beyond archive cutoff
        var result = calculator.calculate(annual(2026, 10, 7, "100.00", "50.00"), history);
        assertTrue(result.seasonal());
        assertEquals(new BigDecimal("50.00"), result.points().get(9).total());
        assertEquals(new BigDecimal("15.00"), result.points().get(10).remaining());
        assertEquals(new BigDecimal("5.00"), result.points().get(11).remaining());
        assertEquals(BigDecimal.ZERO, result.points().get(8).remaining());
        reconciles(result);
    }
    @Test void sparseHistoryFallsBackToRemainingDaysAndReconcilesFractionalQuantities() {
        var result = calculator.calculate(annual(2026, 10, 7, "1.125", "3.777"), List.of(value(2026, 10, 7, "1.125")));
        assertFalse(result.seasonal());
        assertEquals(new BigDecimal("1.066"), result.points().get(9).remaining()); // 24 / 85 remaining days
        reconciles(result);
    }
    @Test void completedMonthGetsNoExtraEstimateAndLeapDayIsHandled() {
        var result = calculator.calculate(annual(2024, 2, 29, "100.00", "1.00"), List.of(value(2024, 2, 29, "100.00")));
        assertEquals(BigDecimal.ZERO, result.points().get(1).remaining());
        assertTrue(result.points().get(2).remaining().signum() > 0);
        reconciles(result);
    }
    @Test void lastDayBeforeYearEndAssignsAllResidualToDecember() {
        var result = calculator.calculate(annual(2026, 12, 30, "100.00", "0.01"), List.of(value(2026, 12, 30, "100.00")));
        assertEquals(new BigDecimal("100.01"), result.points().getLast().total());
        reconciles(result);
        assertNull(calculator.calculate(annual(2026, 12, 31, "100.00", "0.00"), List.of()));
        assertNull(calculator.calculate(null, List.of()));
    }
    @Test void incompleteArchiveYearsAndNegativeTailBucketsDoNotCreateASeasonalPattern() {
        var projection = annual(2026, 10, 7, "20.00", "20.00");
        var history = List.of(value(2019, 11, 1, "100"), value(2024, 11, 1, "100"),
                value(2025, 11, 1, "-10"), value(2025, 12, 1, "100"), value(2026, 10, 7, "20"));
        assertFalse(calculator.calculate(projection, history).seasonal());
        reconciles(calculator.calculate(projection, history));
    }
    @Test void sparseSeasonalMediansFallBackAndCompletedFebruaryExcludesHistoricalLeapDay() {
        var sparse = List.of(value(2023, 10, 8, "10"), value(2024, 11, 1, "10"),
                value(2025, 12, 1, "10"), value(2026, 10, 7, "10"));
        var result = calculator.calculate(annual(2026, 10, 7, "10.00", "1.00"), sparse);
        assertFalse(result.seasonal());
        reconciles(result);
        var leapHistory = List.of(value(2020, 2, 29, "1000"), value(2020, 3, 1, "1"),
                value(2024, 2, 29, "1000"), value(2024, 3, 1, "1"), value(2026, 2, 28, "10"));
        var february = calculator.calculate(annual(2026, 2, 28, "10.00", "1.00"), leapHistory);
        assertEquals(BigDecimal.ZERO, february.points().get(1).remaining());
        assertEquals(new BigDecimal("1.00"), february.points().get(2).remaining());
        reconciles(february);
    }
}

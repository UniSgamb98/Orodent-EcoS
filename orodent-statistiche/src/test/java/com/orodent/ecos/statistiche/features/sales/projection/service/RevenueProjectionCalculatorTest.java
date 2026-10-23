package com.orodent.ecos.statistiche.features.sales.projection.service;

import com.orodent.ecos.statistiche.features.sales.projection.model.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RevenueProjectionCalculatorTest {
    private final RevenueProjectionCalculator calculator = new RevenueProjectionCalculator();

    @Test
    void usesMedianHistoricalSeasonalityAndReportsCoverage() {
        SalesDataCoverage coverage = new SalesDataCoverage(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        List<DailyRevenueValue> history = List.of(
                value(2023, 6, 30, "40"), value(2023, 12, 31, "60"),
                value(2024, 6, 30, "50"), value(2024, 12, 31, "50"),
                value(2025, 6, 30, "60"), value(2025, 12, 31, "40"));

        RevenueProjection result = calculator.calculate(2026, new BigDecimal("60"), coverage, history, true);

        assertEquals(ProjectionMethod.SEASONAL, result.method());
        assertEquals(ProjectionConfidence.HIGH, result.confidence());
        assertEquals(new BigDecimal("120.00"), result.projectedAnnualRevenue());
        assertEquals(new BigDecimal("60.00"), result.projectedRemainingRevenue());
        assertEquals(coverage.through(), result.dataThrough());
    }

    @Test
    void fallsBackToLinearProjectionWithoutEnoughHistory() {
        SalesDataCoverage coverage = new SalesDataCoverage(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));

        RevenueProjection result = calculator.calculate(
                2026, new BigDecimal("60"), coverage, List.of(), true);

        assertEquals(ProjectionMethod.LINEAR, result.method());
        assertEquals(ProjectionConfidence.LOW, result.confidence());
    }

    @Test
    void doesNotProjectAClosedHistoricalYear() {
        SalesDataCoverage coverage = new SalesDataCoverage(
                LocalDate.of(2025, 1, 2), LocalDate.of(2025, 12, 20));

        RevenueProjection result = calculator.calculate(
                2025, new BigDecimal("100"), coverage, List.of(), false);

        assertEquals(ProjectionMethod.ACTUAL, result.method());
        assertEquals(new BigDecimal("100.00"), result.projectedAnnualRevenue());
        assertEquals(BigDecimal.ZERO.setScale(2), result.projectedRemainingRevenue());
    }

    @Test
    void ignoresUnusableHistoricalSharesFromReturnsOrMissingEarlySales() {
        SalesDataCoverage coverage = new SalesDataCoverage(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        List<DailyRevenueValue> history = List.of(
                value(2023, 6, 30, "100"), value(2023, 12, 31, "-50"),
                value(2024, 12, 31, "100"),
                value(2025, 6, 30, "-50"));
        RevenueProjection result = calculator.calculate(2026, new BigDecimal("60"), coverage, history, true);
        assertEquals(ProjectionMethod.LINEAR, result.method());
        assertEquals(0, result.historicalYears());
    }

    @Test
    void treatsCompleteCurrentYearAsActual() {
        SalesDataCoverage coverage = new SalesDataCoverage(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        RevenueProjection result = calculator.calculate(2026, new BigDecimal("100"), coverage, List.of(), true);
        assertEquals(ProjectionMethod.ACTUAL, result.method());
        assertEquals(new BigDecimal("0.00"), result.projectedRemainingRevenue());
    }

    private DailyRevenueValue value(int year, int month, int day, String revenue) {
        return new DailyRevenueValue(LocalDate.of(year, month, day), new BigDecimal(revenue));
    }
}

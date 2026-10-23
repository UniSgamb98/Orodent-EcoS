package com.orodent.ecos.statistiche.features.sales.customers.service;

import com.orodent.ecos.statistiche.features.sales.customers.model.*;
import com.orodent.ecos.statistiche.features.sales.projection.model.SalesDataCoverage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomerTrendCalculatorTest {
    private final CustomerTrendCalculator calculator = new CustomerTrendCalculator();
    private final CustomerTrendPeriodResolver resolver = new CustomerTrendPeriodResolver();
    private final SalesDataCoverage coverage = new SalesDataCoverage(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 12, 31));

    @Test
    void identifiesAProgressiveOrderDeclineAcrossEquivalentPeriods() {
        List<CustomerMonthlyValue> months = new ArrayList<>();
        for (int month = 1; month <= 24; month++) {
            int orders = month <= 12 ? 10 : 7;
            months.add(new CustomerMonthlyValue(month <= 12 ? 2025 : 2026, (month - 1) % 12 + 1,
                    BigDecimal.valueOf(orders * 100L), BigDecimal.valueOf(orders), orders));
        }
        var period = resolver.rolling(LocalDate.of(2026, 12, 31));
        var comparison = calculator.compare(period, totals(8400, 84, 84), totals(12000, 120, 120), coverage, false);
        var result = calculator.calculate(months, comparison, comparison);
        assertEquals(CustomerTrendStatus.DECLINING, result.summary().status());
        assertEquals(new BigDecimal("-30.0"), comparison.orderChangePercentage());
        assertEquals(new BigDecimal("7.0"), result.points().getLast().movingAverage());
    }

    @Test
    void doesNotTurnIncompleteHistoricalCoverageIntoArtificialGrowth() {
        var period = resolver.rolling(LocalDate.of(2026, 10, 7));
        var incomplete = new SalesDataCoverage(LocalDate.of(2025, 1, 1), coverage.through());
        var comparison = calculator.compare(period, totals(100, 10, 5), totals(10, 1, 1), incomplete, false);
        assertFalse(comparison.completeHistory());
        assertNull(comparison.revenueChangePercentage());
        assertNull(comparison.quantityChangePercentage());
        assertEquals(CustomerTrendStatus.INSUFFICIENT_DATA,
                calculator.calculate(List.of(), comparison, comparison).summary().status());
    }

    @Test
    void comparesTotalQuantityIndependentlyOfOrdersAndRevenue() {
        var comparison = calculator.compare(resolver.rolling(LocalDate.of(2026, 12, 31)),
                totals(960, 180, 60), totals(1200, 120, 60), coverage, false);
        var result = calculator.calculate(List.of(), comparison, comparison);
        assertEquals(CustomerTrendStatus.STABLE, result.summary().status());
        assertEquals(new BigDecimal("50.0"), comparison.quantityChangePercentage());
        assertEquals(new BigDecimal("-20.0"), comparison.revenueChangePercentage());
        assertEquals("Il fatturato cala sia sui 12 mesi sia nell’anno a pari data; la quantità cresce sia sui 12 mesi sia nell’anno a pari data.",
                result.summary().explanation());
    }

    @Test
    void leavesPercentageUnavailableWhenPreviousValueIsZero() {
        var comparison = calculator.compare(resolver.rolling(LocalDate.of(2026, 12, 31)),
                totals(100, 10, 5), totals(100, 0, 5), coverage, false);
        assertTrue(comparison.completeHistory());
        assertNull(comparison.quantityChangePercentage());
        assertEquals(BigDecimal.valueOf(0, 1), comparison.revenueChangePercentage());
    }

    @Test
    void explainsOppositeRollingAndAnnualDirectionsWithoutAveragingThem() {
        var rolling = calculator.compare(resolver.rolling(LocalDate.of(2026, 10, 7)),
                totals(170, 180, 40), totals(100, 100, 38), coverage, false);
        var annual = calculator.compare(resolver.annual(LocalDate.of(2026, 10, 7)),
                totals(80, 100, 30), totals(100, 100, 30), coverage, true);
        var result = calculator.calculate(List.of(), rolling, annual);
        assertEquals(CustomerTrendStatus.STABLE, result.summary().status());
        assertEquals(new BigDecimal("70.0"), rolling.revenueChangePercentage());
        assertEquals(new BigDecimal("-20.0"), annual.revenueChangePercentage());
        assertEquals("Il fatturato cresce sui 12 mesi, ma cala nell’anno a pari data; la quantità cresce sui 12 mesi, ma rimane stabile nell’anno a pari data.",
                result.summary().explanation());
    }

    @Test
    void marksShortAnnualPeriodsAndSparsePurchasesAsPreliminary() {
        var january = calculator.compare(resolver.annual(LocalDate.of(2026, 1, 15)),
                totals(150, 15, 4), totals(100, 10, 4), coverage, true);
        assertTrue(january.preliminary());
        var sparse = calculator.compare(resolver.annual(LocalDate.of(2026, 10, 7)),
                totals(150, 15, 1), totals(100, 10, 4), coverage, true);
        assertTrue(sparse.preliminary());
        assertTrue(calculator.calculate(List.of(), january, january).summary().explanation().contains("confronto annuale è preliminare"));
    }

    private CustomerPeriodTotals totals(int revenue, int quantity, int orders) {
        return new CustomerPeriodTotals(BigDecimal.valueOf(revenue), BigDecimal.valueOf(quantity), orders);
    }
}

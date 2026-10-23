package com.orodent.ecos.statistiche.features.sales.customers.service;

import com.orodent.ecos.statistiche.features.sales.analysis.model.CustomerAnalysisItem;
import com.orodent.ecos.statistiche.features.sales.customers.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CustomerFollowUpCalculatorTest {
    private final CustomerFollowUpCalculator calculator = new CustomerFollowUpCalculator();
    private LocalDate date(String value) { return LocalDate.parse(value); }
    private CustomerAnalysisItem customer(String code) {
        return new CustomerAnalysisItem(code, "Cliente " + code, BigDecimal.TEN, BigDecimal.ONE, 3, date("2026-01-01"));
    }
    @Test void sharedFrequencyUsesDistinctSortedPurchaseDaysAndMedianInsteadOfMean() {
        var frequency = new CustomerFrequencyCalculator().calculate(List.of(date("2026-02-01"), date("2026-01-01"),
                date("2026-01-02"), date("2026-01-03"), date("2026-01-02")));
        assertEquals(3, frequency.intervals());
        assertEquals(new BigDecimal("10.3"), frequency.averageDays());
        assertEquals(BigDecimal.ONE, frequency.typicalDays());
        assertNull(new CustomerFrequencyCalculator().calculate(List.of(date("2026-01-01"))).typicalDays());
    }
    @Test void usesOnlySelectedYearAndAvailableDatesAndExcludesCustomersWithoutAnInterval() {
        var reference = date("2026-01-25");
        var data = calculator.calculate(2026, reference, List.of(customer("A"), customer("B"), customer("C")), Map.of(
                "A", List.of(date("2025-12-01"), date("2026-01-01"), date("2026-01-11"), date("2026-02-01")),
                "B", List.of(date("2026-01-01"))));
        var row = calculator.select(data, CustomerFrequencyBasis.TYPICAL, 0, "").getFirst();
        assertEquals("A", row.customer().code());
        assertEquals(date("2026-01-11"), row.lastPurchase());
        assertEquals(14, row.recencyDays());
        assertEquals(0, new BigDecimal("4").compareTo(row.delay(CustomerFrequencyBasis.TYPICAL)));
        assertEquals(1, calculator.select(data, CustomerFrequencyBasis.TYPICAL, 0, "").size());
        assertTrue(calculator.select(data, CustomerFrequencyBasis.TYPICAL, 7, "").isEmpty());
        assertTrue(calculator.select(data, CustomerFrequencyBasis.TYPICAL, 0, "Cliente B").isEmpty());
        assertEquals(1, calculator.select(data, CustomerFrequencyBasis.TYPICAL, 0, " a ").size());
    }
    @Test void frequencyChoiceChangesWhoIsOverdueAndBoundaryIsStrict() {
        var data = calculator.calculate(2026, date("2026-02-10"), List.of(customer("A")), Map.of("A",
                List.of(date("2026-01-01"), date("2026-01-02"), date("2026-01-03"), date("2026-02-01"))));
        assertEquals(1, calculator.select(data, CustomerFrequencyBasis.TYPICAL, 0, "").size());
        assertTrue(calculator.select(data, CustomerFrequencyBasis.AVERAGE, 0, "").isEmpty());
        var exact = calculator.calculate(2026, date("2026-01-21"), List.of(customer("A")),
                Map.of("A", List.of(date("2026-01-01"), date("2026-01-11"))));
        assertTrue(calculator.select(exact, CustomerFrequencyBasis.TYPICAL, 0, "").isEmpty());
    }
    @Test void priorityAccountsForTheCustomersHabitInsteadOfOnlyAbsoluteDelay() {
        var data = new CustomerFollowUpData(2026, date("2026-10-01"), List.of(
                new CustomerFollowUpItem(customer("Slow"), date("2026-01-01"), 50, new CustomerFrequency(new BigDecimal("25"),new BigDecimal("25"),4)),
                new CustomerFollowUpItem(customer("Fast"), date("2026-01-01"), 30, new CustomerFrequency(new BigDecimal("5"),new BigDecimal("5"),4))));
        assertEquals(List.of("Fast", "Slow"), calculator.select(data, CustomerFrequencyBasis.TYPICAL, 0, "").stream()
                .map(item -> item.customer().code()).toList());
    }
    @Test void referenceIsCappedByArchiveDateTodayAndSelectedYear() {
        assertEquals(date("2026-10-07"), calculator.referenceDate(2026, date("2026-10-07"), date("2026-10-19")));
        assertEquals(date("2026-10-19"), calculator.referenceDate(2026, date("2026-12-31"), date("2026-10-19")));
        assertEquals(date("2025-12-31"), calculator.referenceDate(2025, date("2026-10-07"), date("2026-10-19")));
        assertEquals(date("2026-10-19"), calculator.referenceDate(2026, null, date("2026-10-19")));
    }
}

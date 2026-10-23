package com.orodent.ecos.statistiche.features.sales.customers.service;

import com.orodent.ecos.statistiche.features.sales.projection.model.SalesDataCoverage;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class CustomerTrendPeriodResolverTest {
    private final CustomerTrendPeriodResolver resolver = new CustomerTrendPeriodResolver();
    private final SalesDataCoverage coverage = new SalesDataCoverage(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 10, 7));

    @Test
    void usesTheArchiveDateAndDoesNotIncludeThePartialMonthInRollingTotals() {
        var reference = resolver.referenceDate(2026, coverage, LocalDate.of(2026, 10, 12));
        assertEquals(LocalDate.of(2026, 10, 7), reference);
        var rolling = resolver.rolling(reference);
        assertEquals(LocalDate.of(2025, 10, 1), rolling.from());
        assertEquals(LocalDate.of(2026, 9, 30), rolling.through());
        assertEquals(LocalDate.of(2024, 10, 1), rolling.previousFrom());
        assertEquals(LocalDate.of(2025, 9, 30), rolling.previousThrough());
        var annual = resolver.annual(reference);
        assertEquals(LocalDate.of(2026, 1, 1), annual.from());
        assertEquals(reference, annual.through());
        assertEquals(LocalDate.of(2025, 10, 7), annual.previousThrough());
    }

    @Test
    void includesDecemberForHistoricalYearsAndHandlesTheJanuaryBoundary() {
        assertEquals(LocalDate.of(2025, 12, 31), resolver.referenceDate(2025, coverage, LocalDate.of(2026, 10, 12)));
        var january = resolver.rolling(LocalDate.of(2026, 1, 15));
        assertEquals(LocalDate.of(2025, 1, 1), january.from());
        assertEquals(LocalDate.of(2025, 12, 31), january.through());
        assertEquals(LocalDate.of(2024, 12, 31), january.previousThrough());
    }

    @Test
    void handlesLeapDaysAndDoesNotIncludeFutureDatedSales() {
        var annual = resolver.annual(LocalDate.of(2024, 2, 29));
        assertEquals(LocalDate.of(2023, 2, 28), annual.previousThrough());
        assertEquals(LocalDate.of(2024, 2, 29), resolver.rolling(annual.through()).through());
        assertEquals(LocalDate.of(2026, 9, 20), resolver.referenceDate(2026, coverage, LocalDate.of(2026, 9, 20)));
    }
}

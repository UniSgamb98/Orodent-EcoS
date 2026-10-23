package com.orodent.ecos.statistiche.features.sales.customers.service;

import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerMonthlyValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerMonthlySeriesNormalizerTest {
    private final CustomerMonthlySeriesNormalizer normalizer = new CustomerMonthlySeriesNormalizer();

    @Test
    void fillsMissingMonthsOfHistoricalYearsWithZero() {
        List<CustomerMonthlyValue> result = normalizer.fillMissingMonths(List.of(
                value(2023, 7, "120"),
                value(2023, 9, "90"),
                value(2024, 3, "50")
        ), 2023, 2024);

        assertEquals(15, result.size());
        CustomerMonthlyValue august2023 = result.get(7);
        assertEquals(2023, august2023.year());
        assertEquals(8, august2023.month());
        assertEquals(BigDecimal.ZERO, august2023.revenue());
        assertEquals(BigDecimal.ZERO, august2023.quantity());
        assertEquals(0, august2023.documents());
    }

    @Test
    void doesNotInventFutureMonthsForSelectedYear() {
        List<CustomerMonthlyValue> result = normalizer.fillMissingMonths(
                List.of(value(2025, 12, "100"), value(2026, 4, "80")), 2025, 2026);

        assertEquals(16, result.size());
        assertEquals(4, result.getLast().month());
        assertEquals(2026, result.getLast().year());
    }

    private CustomerMonthlyValue value(int year, int month, String revenue) {
        return new CustomerMonthlyValue(year, month, new BigDecimal(revenue), BigDecimal.ONE, 1);
    }
}

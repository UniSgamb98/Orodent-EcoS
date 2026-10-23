package com.orodent.ecos.statistiche.features.sales.customers.service;

import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerMonthlyValue;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CustomerMonthlySeriesNormalizer {
    public List<CustomerMonthlyValue> fillMissingMonths(
            List<CustomerMonthlyValue> values, int fromYear, int toYear
    ) {
        Map<YearMonth, CustomerMonthlyValue> valuesByMonth = new HashMap<>();
        for (CustomerMonthlyValue value : values) {
            valuesByMonth.put(YearMonth.of(value.year(), value.month()), value);
        }

        int selectedYearLastMonth = values.stream()
                .filter(value -> value.year() == toYear)
                .mapToInt(CustomerMonthlyValue::month)
                .max()
                .orElse(0);
        List<CustomerMonthlyValue> normalized = new ArrayList<>();
        for (int year = fromYear; year <= toYear; year++) {
            int lastMonth = year < toYear ? 12 : selectedYearLastMonth;
            for (int month = 1; month <= lastMonth; month++) {
                YearMonth key = YearMonth.of(year, month);
                normalized.add(valuesByMonth.getOrDefault(key, zero(year, month)));
            }
        }
        return List.copyOf(normalized);
    }

    /** Trend charts extend to the common reference month, including months without purchases. */
    public List<CustomerMonthlyValue> fillThroughMonth(List<CustomerMonthlyValue> values, YearMonth through) {
        Map<YearMonth, CustomerMonthlyValue> byMonth = new HashMap<>();
        for (CustomerMonthlyValue value : values) {
            byMonth.put(YearMonth.of(value.year(), value.month()), value);
        }
        List<CustomerMonthlyValue> result = new ArrayList<>();
        for (YearMonth month = through.minusMonths(23); !month.isAfter(through); month = month.plusMonths(1)) {
            result.add(byMonth.getOrDefault(month, zero(month.getYear(), month.getMonthValue())));
        }
        return List.copyOf(result);
    }

    private CustomerMonthlyValue zero(int year, int month) {
        return new CustomerMonthlyValue(year, month, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }
}

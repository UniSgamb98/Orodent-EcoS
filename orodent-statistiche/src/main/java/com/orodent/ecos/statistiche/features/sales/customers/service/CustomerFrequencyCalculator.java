package com.orodent.ecos.statistiche.features.sales.customers.service;

import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerFrequency;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Shared definition of purchase frequency for the detail and follow-up list. */
public final class CustomerFrequencyCalculator {
    public CustomerFrequency calculate(List<LocalDate> purchaseDates) {
        List<LocalDate> dates = purchaseDates.stream().distinct().sorted().toList();
        List<Long> intervals = new ArrayList<>();
        for (int index = 1; index < dates.size(); index++)
            intervals.add(ChronoUnit.DAYS.between(dates.get(index - 1), dates.get(index)));
        if (intervals.isEmpty()) return new CustomerFrequency(null, null, 0);
        BigDecimal average = BigDecimal.valueOf(intervals.stream().mapToLong(Long::longValue).sum())
                .divide(BigDecimal.valueOf(intervals.size()), 1, RoundingMode.HALF_UP);
        intervals.sort(Long::compareTo);
        int middle = intervals.size() / 2;
        BigDecimal typical = intervals.size() % 2 == 1 ? BigDecimal.valueOf(intervals.get(middle))
                : BigDecimal.valueOf(intervals.get(middle - 1)).add(BigDecimal.valueOf(intervals.get(middle)))
                        .divide(BigDecimal.valueOf(2), 1, RoundingMode.HALF_UP);
        return new CustomerFrequency(average, typical, intervals.size());
    }
}

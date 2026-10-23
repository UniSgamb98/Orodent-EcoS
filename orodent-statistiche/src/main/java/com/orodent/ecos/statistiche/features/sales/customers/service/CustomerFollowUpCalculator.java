package com.orodent.ecos.statistiche.features.sales.customers.service;

import com.orodent.ecos.statistiche.features.sales.analysis.model.CustomerAnalysisItem;
import com.orodent.ecos.statistiche.features.sales.customers.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure calculation and filtering; no database or JavaFX dependencies. */
public final class CustomerFollowUpCalculator {
    private final CustomerFrequencyCalculator frequencies = new CustomerFrequencyCalculator();

    public LocalDate referenceDate(int year, LocalDate archiveThrough, LocalDate today) {
        LocalDate reference = LocalDate.of(year, 12, 31);
        if (today.isBefore(reference)) reference = today;
        if (archiveThrough != null && archiveThrough.isBefore(reference)) reference = archiveThrough;
        return reference;
    }

    public CustomerFollowUpData calculate(int year, LocalDate reference, List<CustomerAnalysisItem> customers,
                                         Map<String, List<LocalDate>> purchases) {
        List<CustomerFollowUpItem> items = customers.stream().map(customer -> {
            List<LocalDate> dates = purchases.getOrDefault(customer.code(), List.of()).stream()
                    .filter(date -> date.getYear() == year && !date.isAfter(reference)).distinct().sorted().toList();
            LocalDate last = dates.isEmpty() ? null : dates.getLast();
            return new CustomerFollowUpItem(customer, last,
                    last == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(last, reference)), frequencies.calculate(dates));
        }).toList();
        return new CustomerFollowUpData(year, reference, items);
    }

    public List<CustomerFollowUpItem> select(CustomerFollowUpData data, CustomerFrequencyBasis basis,
                                              int minimumDelay, String search) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return data.items().stream().filter(item -> item.delay(basis) != null && item.delay(basis).signum() > 0
                        && item.delay(basis).compareTo(BigDecimal.valueOf(Math.max(0, minimumDelay))) >= 0)
                .filter(item -> term.isEmpty() || item.customer().code().toLowerCase(Locale.ROOT).contains(term)
                        || item.customer().name().toLowerCase(Locale.ROOT).contains(term))
                .sorted(Comparator.comparing((CustomerFollowUpItem item) -> item.priority(basis)).reversed()
                        .thenComparing(item -> item.delay(basis), Comparator.reverseOrder())
                        .thenComparing(item -> item.customer().name(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(item -> item.customer().code()))
                .toList();
    }
}

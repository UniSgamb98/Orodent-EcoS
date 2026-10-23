package com.orodent.ecos.statistiche.features.sales.customers.service;

import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerTrendPeriod;
import com.orodent.ecos.statistiche.features.sales.projection.model.SalesDataCoverage;

import java.time.LocalDate;
import java.time.YearMonth;

/** Uses archive coverage rather than the last purchase of an individual customer. */
public final class CustomerTrendPeriodResolver {
    public LocalDate referenceDate(int year, SalesDataCoverage coverage, LocalDate today) {
        LocalDate reference = LocalDate.of(year, 12, 31);
        if (coverage.through().isBefore(reference)) reference = coverage.through();
        if (today.isBefore(reference)) reference = today;
        return reference;
    }

    public CustomerTrendPeriod rolling(LocalDate reference) {
        YearMonth month = YearMonth.from(reference);
        LocalDate through = reference.equals(month.atEndOfMonth())
                ? reference : month.minusMonths(1).atEndOfMonth();
        LocalDate from = YearMonth.from(through).minusMonths(11).atDay(1);
        return new CustomerTrendPeriod(from, through, from.minusYears(1), from.minusDays(1));
    }

    public CustomerTrendPeriod annual(LocalDate reference) {
        LocalDate from = reference.withDayOfYear(1);
        return new CustomerTrendPeriod(from, reference, from.minusYears(1), reference.minusYears(1));
    }
}

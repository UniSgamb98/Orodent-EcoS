package com.orodent.ecos.statistiche.features.sales.customers.service;

import com.orodent.ecos.statistiche.features.sales.customers.model.*;
import com.orodent.ecos.statistiche.features.sales.projection.model.SalesDataCoverage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class CustomerTrendCalculator {
    private static final int CHART_MONTHS = 24;
    private final CustomerTrendNarrative narrative = new CustomerTrendNarrative();

    public CustomerTrendData calculate(List<CustomerMonthlyValue> chronologicalValues,
                                       CustomerTrendComparison rolling, CustomerTrendComparison annual) {
        List<CustomerMonthlyValue> values = chronologicalValues.size() <= CHART_MONTHS
                ? chronologicalValues
                : chronologicalValues.subList(chronologicalValues.size() - CHART_MONTHS, chronologicalValues.size());
        CustomerTrendStatus status = status(rolling.orderChangePercentage());
        CustomerTrendSummary summary = new CustomerTrendSummary(status, rolling, annual,
                narrative.describe(rolling, annual));
        return new CustomerTrendData(summary, points(values));
    }

    public CustomerTrendComparison compare(CustomerTrendPeriod period, CustomerPeriodTotals current,
                                           CustomerPeriodTotals previous,
                                           SalesDataCoverage coverage,
                                           boolean annual) {
        boolean complete = !coverage.from().isAfter(period.previousFrom())
                && !coverage.through().isBefore(period.through());
        boolean preliminary = annual && (period.through().isBefore(period.from().plusMonths(3))
                || current.orders() < 3 || previous.orders() < 3);
        return new CustomerTrendComparison(period, current, previous,
                complete ? percentage(current.revenue(), previous.revenue()) : null,
                complete ? percentage(current.quantity(), previous.quantity()) : null,
                complete ? percentage(current.orders(), previous.orders()) : null,
                complete, preliminary);
    }

    private List<CustomerTrendPoint> points(List<CustomerMonthlyValue> values) {
        List<CustomerTrendPoint> points = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            int from = Math.max(0, index - 2);
            BigDecimal total = BigDecimal.ZERO;
            for (int cursor = from; cursor <= index; cursor++) {
                total = total.add(BigDecimal.valueOf(values.get(cursor).documents()));
            }
            CustomerMonthlyValue value = values.get(index);
            points.add(new CustomerTrendPoint(value.year(), value.month(), value.documents(),
                    total.divide(BigDecimal.valueOf(index - from + 1L), 1, RoundingMode.HALF_UP)));
        }
        return List.copyOf(points);
    }

    private BigDecimal percentage(int current, int previous) {
        return percentage(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    private BigDecimal percentage(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) return null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    private CustomerTrendStatus status(BigDecimal change) {
        if (change == null) return CustomerTrendStatus.INSUFFICIENT_DATA;
        if (CustomerTrendDirection.forOrders(change) == CustomerTrendDirection.UP) return CustomerTrendStatus.GROWING;
        if (change.compareTo(BigDecimal.valueOf(-20)) <= 0) return CustomerTrendStatus.DECLINING;
        if (CustomerTrendDirection.forOrders(change) == CustomerTrendDirection.DOWN) return CustomerTrendStatus.SLOWING;
        return CustomerTrendStatus.STABLE;
    }

}

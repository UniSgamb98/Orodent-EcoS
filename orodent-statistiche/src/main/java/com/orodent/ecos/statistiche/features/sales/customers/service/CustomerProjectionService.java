package com.orodent.ecos.statistiche.features.sales.customers.service;

import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerDetail;
import com.orodent.ecos.statistiche.features.sales.customers.repository.CustomerAnalysisRepository;
import com.orodent.ecos.statistiche.features.sales.projection.model.*;
import com.orodent.ecos.statistiche.features.sales.projection.service.*;
import java.time.LocalDate;
import java.util.List;

/** Coordinates monthly forecasts with the existing customer annual revenue projection. */
public final class CustomerProjectionService {
    public MonthlyProjections load(CustomerAnalysisRepository repository, CustomerDetail detail, int year,
                                   int fromYear, SalesDataCoverage coverage, RevenueProjection revenue,
                                   List<DailyRevenueValue> revenueHistory) {
        if (year != LocalDate.now().getYear() || coverage.through().isAfter(LocalDate.now())) return MonthlyProjections.empty();
        List<DailyMetricValue> quantityHistory = detail.quantity().signum() <= 0 ? List.of()
                : repository.loadDailyQuantityHistory(detail.code(), fromYear, year);
        MetricProjection quantity = detail.quantity().signum() <= 0 ? null
                : new SeasonalProjectionCalculator(3).calculate(year, detail.quantity(), coverage, quantityHistory, true);
        MonthlyProjectionCalculator monthly = new MonthlyProjectionCalculator();
        return new MonthlyProjections(
                monthly.calculate(MetricProjection.fromRevenue(revenue), revenueHistory.stream()
                        .map(value -> new DailyMetricValue(value.date(), value.revenue())).toList()),
                monthly.calculate(quantity, quantityHistory));
    }
}

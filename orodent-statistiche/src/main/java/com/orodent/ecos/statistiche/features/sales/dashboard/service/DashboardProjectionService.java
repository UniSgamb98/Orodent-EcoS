package com.orodent.ecos.statistiche.features.sales.dashboard.service;

import com.orodent.ecos.statistiche.features.sales.dashboard.model.*;
import com.orodent.ecos.statistiche.features.sales.dashboard.repository.SalesStatisticsRepository;
import com.orodent.ecos.statistiche.features.sales.projection.model.*;
import com.orodent.ecos.statistiche.features.sales.projection.service.SeasonalProjectionCalculator;
import com.orodent.ecos.statistiche.features.sales.projection.service.MonthlyProjectionCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

/** Coordinates per-measure forecasts only for the current whole-year KPIs and monthly chart. */
final class DashboardProjectionService {
    Result load(SalesStatisticsRepository repository, int year, SalesFilter filter,
                              SalesSummary actual, List<AnnualSalesComparison> annual, List<Integer> years) {
        if (year != LocalDate.now().getYear() || !filter.equals(SalesFilter.wholeYear(year))) return Result.empty();
        RevenueProjection revenue = annual.stream().filter(value -> value.year() == year)
                .map(AnnualSalesComparison::projection).filter(Objects::nonNull).findFirst().orElse(null);
        var coverage = revenue == null ? repository.loadDataCoverage(year)
                : Optional.of(new SalesDataCoverage(revenue.dataFrom(), revenue.dataThrough()));
        if (coverage.isEmpty() || coverage.get().through().isAfter(LocalDate.now())
                || coverage.get().through().equals(LocalDate.of(year, 12, 31))) return Result.empty();
        int fromYear = years.stream().min(Integer::compareTo).orElse(year);
        LocalDate archiveFrom = repository.loadDataCoverage(fromYear)
                .map(SalesDataCoverage::from).orElse(coverage.get().from());
        List<DailyMetricValue> quantityHistory = actual.quantity().signum() <= 0 ? List.of()
                : repository.loadDailyQuantityHistory(fromYear, year);
        MetricProjection quantity = actual.quantity().signum() <= 0 ? null
                : new SeasonalProjectionCalculator(3).calculate(year, actual.quantity(), coverage.get(),
                        quantityHistory, true);
        MetricProjection documents = actual.documents() <= 0 ? null
                : new SeasonalProjectionCalculator(0).calculate(year, BigDecimal.valueOf(actual.documents()), coverage.get(),
                        repository.loadDailyDocumentHistory(fromYear, year), true);
        DashboardProjections annualProjections = new DashboardProjections(MetricProjection.fromRevenue(revenue), quantity, documents);
        MonthlyProjectionCalculator monthly = new MonthlyProjectionCalculator();
        MonthlyProjections monthlyProjections = new MonthlyProjections(
                revenue == null ? null : monthly.calculate(annualProjections.revenue(),
                        repository.loadDailyRevenueHistory(fromYear, year).stream()
                                .map(value -> new DailyMetricValue(value.date(), value.revenue())).toList(), archiveFrom),
                monthly.calculate(quantity, quantityHistory, archiveFrom));
        return new Result(annualProjections, monthlyProjections);
    }
    record Result(DashboardProjections annual, MonthlyProjections monthly) {
        static Result empty() { return new Result(DashboardProjections.empty(), MonthlyProjections.empty()); }
    }
}

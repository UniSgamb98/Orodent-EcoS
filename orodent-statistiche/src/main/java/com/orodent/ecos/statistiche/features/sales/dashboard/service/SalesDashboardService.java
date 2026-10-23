package com.orodent.ecos.statistiche.features.sales.dashboard.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesDashboardData;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.AnnualSalesComparison;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.MonthlySales;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesFilter;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesRankingItem;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesSummary;
import com.orodent.ecos.statistiche.features.sales.dashboard.repository.SalesStatisticsRepository;
import com.orodent.ecos.statistiche.features.sales.dashboard.repository.SalesStatisticsRepositoryImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import com.orodent.ecos.statistiche.features.sales.projection.model.*;
import com.orodent.ecos.statistiche.features.sales.projection.model.DailyRevenueValue;
import com.orodent.ecos.statistiche.features.sales.projection.service.RevenueProjectionCalculator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public record SalesDashboardService(ConnectionProvider connectionProvider, Executor executor,
                                    Function<Connection, SalesStatisticsRepository> repositoryFactory) {

    private static final int RANKING_LIMIT = 10;
    private static final int HISTORY_CUSTOMER_LIMIT = 5;

    public SalesDashboardService(ConnectionProvider connectionProvider, Executor executor) {
        this(connectionProvider, executor, SalesStatisticsRepositoryImpl::new);
    }

    public SalesDashboardService(
            ConnectionProvider connectionProvider,
            Executor executor,
            Function<Connection, SalesStatisticsRepository> repositoryFactory
    ) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.repositoryFactory = Objects.requireNonNull(repositoryFactory, "repositoryFactory");
    }

    public CompletableFuture<SalesDashboardData> load(Integer year, LocalDate from, LocalDate to) {
        return CompletableFuture.supplyAsync(() -> loadSync(year, from, to), executor);
    }

    private SalesDashboardData loadSync(Integer requestedYear, LocalDate from, LocalDate to) {
        return connectionProvider.withConnection(connection -> {
            SalesStatisticsRepository repository = repositoryFactory.apply(connection);
            List<Integer> years = repository.findAvailableYears();
            int selectedYear = selectYear(requestedYear, years);
            SalesFilter filter = createFilter(selectedYear, from, to);
            SalesSummary summary = repository.loadSummary(filter);
            SalesFilter previousFilter = previousYearFilter(filter);
            SalesSummary previousSummary = years.contains(selectedYear - 1)
                    ? repository.loadSummary(previousFilter)
                    : SalesSummary.empty();
            List<MonthlySales> previousMonthly = years.contains(selectedYear - 1)
                    ? repository.loadMonthlySales(previousFilter)
                    : List.of();
            List<SalesRankingItem> customers = withPercentages(
                    repository.loadTopCustomers(filter, RANKING_LIMIT),
                    summary.netRevenue()
            );
            List<SalesRankingItem> products = withPercentages(
                    repository.loadTopProducts(filter, RANKING_LIMIT),
                    summary.netRevenue()
            );
            List<AnnualSalesComparison> annual = loadAnnualComparisons(repository, years);
            var projections = new DashboardProjectionService().load(repository, selectedYear, filter, summary, annual, years);
            return new SalesDashboardData(
                    years,
                    selectedYear,
                    filter,
                    summary,
                    previousSummary,
                    annual,
                    repository.loadMonthlySales(filter),
                    previousMonthly,
                    customers,
                    products,
                    repository.loadTopCustomerHistory(selectedYear, HISTORY_CUSTOMER_LIMIT),
                    projections.annual(), projections.monthly()
            );
        });
    }

    private List<AnnualSalesComparison> loadAnnualComparisons(
            SalesStatisticsRepository repository,
            List<Integer> years
    ) {
        List<Integer> chronologicalYears = years.stream().sorted().toList();
        List<AnnualSalesComparison> result = new ArrayList<>();
        SalesSummary previous = null;
        int currentYear = Year.now().getValue();
        RevenueProjectionCalculator calculator = new RevenueProjectionCalculator();
        List<DailyRevenueValue> history =
                years.contains(currentYear) ? repository.loadDailyRevenueHistory(
                        chronologicalYears.getFirst(), currentYear) : List.of();
        for (int year : chronologicalYears) {
            SalesSummary current = repository.loadSummary(SalesFilter.wholeYear(year));
            RevenueProjection projection = null;
            if (year == currentYear && current.netRevenue().signum() > 0) {
                projection = repository.loadDataCoverage(year)
                        .filter(coverage -> !coverage.through().isAfter(LocalDate.now()))
                        .map(coverage -> calculator.calculate(year, current.netRevenue(), coverage, history, true))
                        .orElse(null);
            }
            result.add(new AnnualSalesComparison(
                    year,
                    current,
                    percentageChange(current.netRevenue(), previous == null ? null : previous.netRevenue()),
                    percentageChange(current.quantity(), previous == null ? null : previous.quantity()),
                    projection
            ));
            previous = current;
        }
        return List.copyOf(result);
    }

    private BigDecimal percentageChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 1, RoundingMode.HALF_UP);
    }

    private SalesFilter previousYearFilter(SalesFilter filter) {
        return new SalesFilter(filter.from().minusYears(1), filter.to().minusYears(1));
    }

    private int selectYear(Integer requestedYear, List<Integer> years) {
        if (requestedYear != null && years.contains(requestedYear)) {
            return requestedYear;
        }
        return years.isEmpty() ? Year.now().getValue() : years.getFirst();
    }

    private SalesFilter createFilter(int year, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.getYear() != year || to.getYear() != year) {
            return SalesFilter.wholeYear(year);
        }
        return new SalesFilter(from, to);
    }

    private List<SalesRankingItem> withPercentages(
            List<SalesRankingItem> values,
            BigDecimal total
    ) {
        if (total.signum() == 0) {
            return values;
        }
        return values.stream()
                .map(value -> value.withPercentage(
                        value.netRevenue()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(total, 2, RoundingMode.HALF_UP)
                ))
                .toList();
    }
}

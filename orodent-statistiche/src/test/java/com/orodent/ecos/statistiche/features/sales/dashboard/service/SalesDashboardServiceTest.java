package com.orodent.ecos.statistiche.features.sales.dashboard.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.MonthlySales;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesDashboardData;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesFilter;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesRankingItem;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesSummary;
import com.orodent.ecos.statistiche.features.sales.dashboard.repository.SalesStatisticsRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.TopCustomerHistory;
import java.time.Year;
import java.util.Map;
import com.orodent.ecos.statistiche.features.sales.projection.model.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class SalesDashboardServiceTest {

    @Test
    void loadsLatestYearAndCalculatesRankingPercentages() {
        FakeRepository repository = new FakeRepository();
        SalesDashboardService service = new SalesDashboardService(
                connectionProvider(),
                Runnable::run,
                connection -> repository
        );

        SalesDashboardData data = service.load(null, null, null).join();

        assertEquals(2026, data.selectedYear());
        assertEquals(LocalDate.of(2026, 1, 1), data.filter().from());
        assertEquals(LocalDate.of(2026, 12, 31), data.filter().to());
        assertEquals(new BigDecimal("25.00"), data.topCustomers().getFirst().percentage());
        assertEquals(2026, repository.summaryFilter.from().getYear());
        assertEquals(List.of(2025, 2026), data.annualComparisons().stream()
                .map(comparison -> comparison.year()).toList());
        assertEquals(new BigDecimal("0.0"), data.annualComparisons().get(1).revenueChangePercentage());
        assertEquals(2025, data.previousMonthlySales().isEmpty() ? 0 : data.selectedYear() - 1);
    }

    @Test
    void preservesCustomRangeInsideSelectedYear() {
        FakeRepository repository = new FakeRepository();
        SalesDashboardService service = new SalesDashboardService(
                connectionProvider(),
                Runnable::run,
                connection -> repository
        );

        SalesDashboardData data = service.load(
                2025,
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 3, 31)
        ).join();

        assertEquals(2025, data.selectedYear());
        assertEquals(LocalDate.of(2025, 2, 1), data.filter().from());
        assertEquals(LocalDate.of(2025, 3, 31), data.filter().to());
    }

    @Test
    void returnsEmptyDashboardWhenDatabaseHasNoSales() {
        FakeRepository repository = new FakeRepository();
        repository.years = List.of();
        repository.summary = SalesSummary.empty();
        SalesDashboardService service = new SalesDashboardService(
                connectionProvider(),
                Runnable::run,
                connection -> repository
        );

        SalesDashboardData data = service.load(null, null, null).join();

        assertTrue(data.empty());
        assertTrue(data.availableYears().isEmpty());
    }

    @Test
    void projectsAnnualSeasonalityIndependentlyOfMonthlyFilter() {
        int year = Year.now().getValue();
        FakeRepository repository = projectionRepository(year);
        SalesDashboardService service = new SalesDashboardService(connectionProvider(), Runnable::run, connection -> repository);
        SalesDashboardData wholeYear = service.load(year, null, null).join();
        SalesDashboardData filtered = service.load(year, LocalDate.of(year, 3, 1), LocalDate.of(year, 3, 31)).join();
        RevenueProjection projection = filtered.annualComparisons().getLast().projection();
        assertEquals(ProjectionMethod.SEASONAL, projection.method());
        assertEquals(ProjectionConfidence.MEDIUM, projection.confidence());
        assertEquals(new BigDecimal("120.00"), projection.projectedAnnualRevenue());
        assertEquals(new BigDecimal("60.00"), projection.projectedRemainingRevenue());
        assertEquals(wholeYear.annualComparisons().getLast().projection(), projection);
        assertNull(filtered.annualComparisons().getFirst().projection());
        assertEquals(wholeYear.projections().revenue(), wholeYear.monthlyProjections().revenue().annual());
        assertEquals(0, wholeYear.monthlyProjections().revenue().points().stream()
                .map(MonthlyProjection.Point::remaining).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(projection.projectedRemainingRevenue()));
        assertNull(filtered.monthlyProjections().revenue());
        assertNull(filtered.monthlyProjections().quantity());
    }

    @Test
    void usesLinearProjectionWithoutHistoricalSeasonality() {
        int year = Year.now().getValue();
        FakeRepository repository = projectionRepository(year);
        repository.history = List.of();
        SalesDashboardService service = new SalesDashboardService(connectionProvider(), Runnable::run, connection -> repository);
        assertEquals(ProjectionMethod.LINEAR, service.load(year, null, null).join()
                .annualComparisons().getLast().projection().method());
    }

    @Test
    void leavesProjectionUnavailableWithoutCoverageOrWithFutureDates() {
        int year = Year.now().getValue();
        FakeRepository repository = projectionRepository(year);
        SalesDashboardService service = new SalesDashboardService(connectionProvider(), Runnable::run, connection -> repository);
        repository.coverage = Optional.empty();
        assertNull(service.load(year, null, null).join().annualComparisons().getLast().projection());
        repository.coverage = Optional.of(new SalesDataCoverage(LocalDate.of(year, 1, 1), LocalDate.now().plusDays(1)));
        assertNull(service.load(year, null, null).join().annualComparisons().getLast().projection());
    }

    @Test
    void doesNotExtrapolateNonPositiveNetRevenue() {
        int year = Year.now().getValue();
        FakeRepository repository = projectionRepository(year);
        repository.annualSummaries = Map.of(year, new SalesSummary(new BigDecimal("-10"), BigDecimal.ZERO, 1, 2, BigDecimal.ZERO));
        SalesDashboardService service = new SalesDashboardService(connectionProvider(), Runnable::run, connection -> repository);
        assertNull(service.load(year, null, null).join().annualComparisons().getLast().projection());
    }

    @Test
    void loadsFiveAnnualLeadersRegardlessOfMonthlyFilter() {
        FakeRepository repository = new FakeRepository();
        SalesDashboardService service = new SalesDashboardService(connectionProvider(), Runnable::run, connection -> repository);
        SalesDashboardData result = service.load(2025, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31)).join();
        assertEquals(2025, repository.historyYear);
        assertEquals(5, repository.historyLimit);
        assertEquals("C1", result.topCustomerHistory().getFirst().customerCode());
    }

    @Test
    void wholeYearKpisUseIndependentForecastsAndReuseTheRevenueChartEstimate() {
        int year = Year.now().getValue();
        FakeRepository repository = projectionRepository(year);
        repository.quantityHistory = List.of(
                new DailyMetricValue(LocalDate.of(year - 2, 1, 1), new BigDecimal("8")),
                new DailyMetricValue(LocalDate.of(year - 2, 12, 31), new BigDecimal("2")),
                new DailyMetricValue(LocalDate.of(year - 1, 1, 1), new BigDecimal("8")),
                new DailyMetricValue(LocalDate.of(year - 1, 12, 31), new BigDecimal("2")));
        repository.documentHistory = List.of(
                new DailyMetricValue(LocalDate.of(year - 2, 1, 1), BigDecimal.ONE),
                new DailyMetricValue(LocalDate.of(year - 2, 12, 31), new BigDecimal("3")),
                new DailyMetricValue(LocalDate.of(year - 1, 1, 1), BigDecimal.ONE),
                new DailyMetricValue(LocalDate.of(year - 1, 12, 31), new BigDecimal("3")));
        SalesDashboardService service = new SalesDashboardService(connectionProvider(), Runnable::run, connection -> repository);
        var data = service.load(year, null, null).join();
        assertEquals(new BigDecimal("120.00"), data.projections().revenue().projectedAnnualValue());
        assertEquals(data.annualComparisons().getLast().projection().projectedAnnualRevenue(), data.projections().revenue().projectedAnnualValue());
        assertEquals(new BigDecimal("12.500"), data.projections().quantity().projectedAnnualValue());
        assertEquals(new BigDecimal("8"), data.projections().documents().projectedAnnualValue());
        assertEquals(new BigDecimal("60"), data.summary().netRevenue());
        assertEquals(2, data.summary().documents());
        var filtered = service.load(year, LocalDate.of(year, 3, 1), LocalDate.of(year, 3, 31)).join();
        assertNull(filtered.projections().revenue());
        assertNull(filtered.projections().quantity());
        assertNull(filtered.projections().documents());
        assertNull(service.load(year - 1, null, null).join().projections().documents());
    }

    @Test
    void kpiProjectionIsUnavailableForFutureCoverageAndForNonpositiveMeasures() {
        int year = Year.now().getValue();
        FakeRepository repository = projectionRepository(year);
        SalesDashboardService service = new SalesDashboardService(connectionProvider(), Runnable::run, connection -> repository);
        repository.coverage = Optional.of(new SalesDataCoverage(LocalDate.of(year, 1, 1), LocalDate.now().plusDays(1)));
        assertEquals(com.orodent.ecos.statistiche.features.sales.dashboard.model.DashboardProjections.empty(), service.load(year, null, null).join().projections());
        repository.coverage = Optional.of(new SalesDataCoverage(LocalDate.of(year, 1, 1), LocalDate.of(year, 1, 1)));
        repository.annualSummaries = Map.of(year, new SalesSummary(new BigDecimal("-1"), new BigDecimal("-2"), 1, 0, BigDecimal.ZERO));
        assertEquals(com.orodent.ecos.statistiche.features.sales.dashboard.model.DashboardProjections.empty(), service.load(year, null, null).join().projections());
    }

    @Test
    void monthlySeasonalityUsesArchiveCoverageInsteadOfCurrentYearCoverage() {
        int year = Year.now().getValue();
        FakeRepository repository = projectionRepository(year);
        repository.coverageByYear = Map.of(year - 2, new SalesDataCoverage(
                LocalDate.of(year - 2, 1, 1), LocalDate.of(year - 2, 12, 31)));
        var history = new java.util.ArrayList<DailyRevenueValue>();
        for (int historicalYear : List.of(year - 2, year - 1)) {
            history.add(new DailyRevenueValue(LocalDate.of(historicalYear, 1, 1), new BigDecimal("50")));
            history.add(new DailyRevenueValue(LocalDate.of(historicalYear, 1, 2), new BigDecimal("30")));
            history.add(new DailyRevenueValue(LocalDate.of(historicalYear, 11, 1), new BigDecimal("10")));
            history.add(new DailyRevenueValue(LocalDate.of(historicalYear, 12, 1), new BigDecimal("10")));
        }
        history.add(new DailyRevenueValue(LocalDate.of(year, 1, 1), new BigDecimal("60")));
        repository.history = List.copyOf(history);
        repository.quantityHistory = history.stream().map(value -> new DailyMetricValue(value.date(),
                value.date().getYear() == year ? BigDecimal.TEN : value.revenue())).toList();
        var data = new SalesDashboardService(connectionProvider(), Runnable::run, connection -> repository)
                .load(year, null, null).join();
        var revenue = data.monthlyProjections().revenue();
        var quantity = data.monthlyProjections().quantity();
        assertTrue(revenue.seasonal());
        assertTrue(quantity.seasonal());
        assertEquals(LocalDate.of(year, 1, 1), revenue.annual().dataFrom());
        assertEquals(data.projections().revenue(), revenue.annual());
        assertEquals(new BigDecimal("120.00"), revenue.annual().projectedAnnualValue());
        assertEquals(new BigDecimal("36.00"), revenue.points().getFirst().remaining());
        assertEquals(new BigDecimal("12.00"), revenue.points().get(10).remaining());
        assertEquals(new BigDecimal("12.00"), revenue.points().getLast().remaining());
        assertEquals(new BigDecimal("6.000"), quantity.points().getFirst().remaining());
        for (var projection : List.of(revenue, quantity)) {
            assertEquals(0, projection.points().stream().map(MonthlyProjection.Point::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(projection.annual().projectedAnnualValue()));
        }
        // One incomplete archive year leaves only one usable year: retain the explicit fallback.
        repository.coverageByYear = Map.of(year - 2, new SalesDataCoverage(
                LocalDate.of(year - 2, 1, 2), LocalDate.of(year - 2, 12, 31)));
        var sparse = new SalesDashboardService(connectionProvider(), Runnable::run, connection -> repository)
                .load(year, null, null).join();
        assertTrue(!sparse.monthlyProjections().revenue().seasonal());
    }

    private FakeRepository projectionRepository(int year) {
        FakeRepository repository = new FakeRepository();
        repository.years = List.of(year, year - 1, year - 2);
        SalesSummary historical = new SalesSummary(new BigDecimal("100"), BigDecimal.TEN, 1, 2, BigDecimal.ZERO);
        SalesSummary current = new SalesSummary(new BigDecimal("60"), BigDecimal.TEN, 1, 2, BigDecimal.ZERO);
        repository.annualSummaries = Map.of(year, current, year - 1, historical, year - 2, historical);
        repository.coverage = Optional.of(new SalesDataCoverage(LocalDate.of(year, 1, 1), LocalDate.of(year, 1, 1)));
        repository.history = List.of(
                new DailyRevenueValue(LocalDate.of(year - 2, 1, 1), new BigDecimal("50")),
                new DailyRevenueValue(LocalDate.of(year - 2, 12, 31), new BigDecimal("50")),
                new DailyRevenueValue(LocalDate.of(year - 1, 1, 1), new BigDecimal("50")),
                new DailyRevenueValue(LocalDate.of(year - 1, 12, 31), new BigDecimal("50")));
        return repository;
    }

    private ConnectionProvider connectionProvider() {
        return () -> (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                }
        );
    }

    private static final class FakeRepository implements SalesStatisticsRepository {
        private List<Integer> years = List.of(2026, 2025);
        private SalesSummary summary = new SalesSummary(
                new BigDecimal("1000"),
                new BigDecimal("20"),
                4,
                5,
                new BigDecimal("100")
        );
        private SalesFilter summaryFilter;
        private int historyYear;
        private int historyLimit;
        private Optional<SalesDataCoverage> coverage = Optional.empty();
        private Map<Integer, SalesDataCoverage> coverageByYear = Map.of();
        private List<DailyRevenueValue> history = List.of();
        private List<DailyMetricValue> quantityHistory = List.of();
        private List<DailyMetricValue> documentHistory = List.of();
        private Map<Integer, SalesSummary> annualSummaries = Map.of();

        @Override
        public List<TopCustomerHistory> loadTopCustomerHistory(int selectedYear, int limit) {
            historyYear = selectedYear;
            historyLimit = limit;
            return List.of(new TopCustomerHistory("C1", "Cliente", selectedYear, new BigDecimal("250")));
        }

        @Override
        public Optional<SalesDataCoverage> loadDataCoverage(int year) {
            return coverageByYear.containsKey(year) ? Optional.of(coverageByYear.get(year)) : coverage;
        }

        @Override
        public List<DailyRevenueValue> loadDailyRevenueHistory(int fromYear, int toYear) {
            return history;
        }

        @Override public List<DailyMetricValue> loadDailyQuantityHistory(int fromYear, int toYear) { return quantityHistory; }
        @Override public List<DailyMetricValue> loadDailyDocumentHistory(int fromYear, int toYear) { return documentHistory; }

        @Override
        public List<Integer> findAvailableYears() {
            return years;
        }

        @Override
        public SalesSummary loadSummary(SalesFilter filter) {
            summaryFilter = filter;
            return annualSummaries.getOrDefault(filter.from().getYear(), summary);
        }

        @Override
        public List<MonthlySales> loadMonthlySales(SalesFilter filter) {
            return List.of(new MonthlySales(1, summary.netRevenue(), summary.quantity(), summary.documents()));
        }

        @Override
        public List<SalesRankingItem> loadTopCustomers(SalesFilter filter, int limit) {
            return List.of(new SalesRankingItem(
                    "C1", null, new BigDecimal("250"), BigDecimal.ONE, 1, BigDecimal.ZERO
            ));
        }

        @Override
        public List<SalesRankingItem> loadTopProducts(SalesFilter filter, int limit) {
            return List.of();
        }
    }
}

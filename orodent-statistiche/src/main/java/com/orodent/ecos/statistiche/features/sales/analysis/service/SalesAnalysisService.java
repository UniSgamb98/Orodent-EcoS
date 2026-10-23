package com.orodent.ecos.statistiche.features.sales.analysis.service;

import com.orodent.ecos.statistiche.features.sales.projection.model.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.statistiche.features.sales.analysis.model.*;
import com.orodent.ecos.statistiche.features.sales.analysis.repository.SalesAnalysisRepository;
import com.orodent.ecos.statistiche.features.sales.analysis.repository.SalesAnalysisRepositoryImpl;
import com.orodent.ecos.statistiche.features.sales.customers.model.*;
import com.orodent.ecos.statistiche.features.sales.customers.repository.CustomerAnalysisRepository;
import com.orodent.ecos.statistiche.features.sales.customers.repository.CustomerAnalysisRepositoryImpl;
import com.orodent.ecos.statistiche.features.sales.customers.service.CustomerMonthlySeriesNormalizer;
import com.orodent.ecos.statistiche.features.sales.customers.service.CustomerTrendCalculator;
import com.orodent.ecos.statistiche.features.sales.customers.service.CustomerTrendPeriodResolver;
import com.orodent.ecos.statistiche.features.sales.projection.service.RevenueProjectionCalculator;
import com.orodent.ecos.statistiche.features.sales.customers.service.CustomerProjectionService;

import com.orodent.ecos.statistiche.features.sales.customers.service.CustomerFrequencyCalculator;
import com.orodent.ecos.statistiche.features.sales.customers.service.CustomerFollowUpCalculator;
import java.sql.Connection;
import java.time.Year;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public final class SalesAnalysisService {
    private static final int ARCHIVE_LIMIT = 500;
    private final ConnectionProvider connectionProvider;
    private final Executor executor;
    private final Function<Connection, SalesAnalysisRepository> repositoryFactory;
    private final Function<Connection, CustomerAnalysisRepository> customerRepositoryFactory;
    private final CustomerMonthlySeriesNormalizer monthlySeriesNormalizer = new CustomerMonthlySeriesNormalizer();
    private final CustomerTrendPeriodResolver trendPeriodResolver = new CustomerTrendPeriodResolver();
    private final CustomerTrendCalculator customerTrendCalculator = new CustomerTrendCalculator();
    private final RevenueProjectionCalculator projectionCalculator = new RevenueProjectionCalculator();

    public SalesAnalysisService(ConnectionProvider connectionProvider, Executor executor) {
        this(connectionProvider, executor, SalesAnalysisRepositoryImpl::new, CustomerAnalysisRepositoryImpl::new);
    }

    SalesAnalysisService(ConnectionProvider connectionProvider, Executor executor,
                         Function<Connection, SalesAnalysisRepository> repositoryFactory) {
        this(connectionProvider, executor, repositoryFactory, CustomerAnalysisRepositoryImpl::new);
    }

    SalesAnalysisService(ConnectionProvider connectionProvider, Executor executor,
                         Function<Connection, SalesAnalysisRepository> repositoryFactory,
                         Function<Connection, CustomerAnalysisRepository> customerRepositoryFactory) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
        this.executor = Objects.requireNonNull(executor);
        this.repositoryFactory = Objects.requireNonNull(repositoryFactory);
        this.customerRepositoryFactory = Objects.requireNonNull(customerRepositoryFactory);
    }

    public CompletableFuture<AnalysisPageData<CustomerAnalysisItem>> loadCustomers(Integer year) {
        return CompletableFuture.supplyAsync(() -> connectionProvider.withConnection(
                connection -> loadCustomerPage(connection, year)), executor);
    }

    private AnalysisPageData<CustomerAnalysisItem> loadCustomerPage(Connection connection, Integer year) {
        SalesAnalysisRepository repository = repositoryFactory.apply(connection);
        List<Integer> years = repository.findAvailableYears();
        int selected = selectYear(year, years);
        List<CustomerAnalysisItem> items = repository.loadCustomers(selected);
        AnalysisSummary summary = new AnalysisSummary(
                items.stream().map(CustomerAnalysisItem::revenue).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                items.stream().map(CustomerAnalysisItem::quantity).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                java.math.BigDecimal.ZERO, items.size());
        return new AnalysisPageData<>(years, selected, summary, items);
    }

    public CompletableFuture<CustomerOverviewData> loadCustomerOverview(Integer year) {
        return CompletableFuture.supplyAsync(() -> connectionProvider.withConnection(connection -> {
            AnalysisPageData<CustomerAnalysisItem> page = loadCustomerPage(connection, year);
            CustomerAnalysisRepository repository = customerRepositoryFactory.apply(connection);
            CustomerFollowUpCalculator calculator = new CustomerFollowUpCalculator();
            java.time.LocalDate through = page.items().isEmpty() ? null : repository.loadDataCoverage().through();
            java.time.LocalDate reference = calculator.referenceDate(page.selectedYear(), through, java.time.LocalDate.now());
            CustomerFollowUpData followUp = calculator.calculate(page.selectedYear(), reference, page.items(),
                    page.items().isEmpty() ? java.util.Map.of() : repository.loadPurchaseDates(page.selectedYear(), reference));
            return new CustomerOverviewData(page, followUp);
        }), executor);
    }

    public CompletableFuture<CustomerDetailData> loadCustomerDetail(
            String customerCode, int year, int previousYears
    ) {
        return CompletableFuture.supplyAsync(() -> connectionProvider.withConnection(connection -> {
            CustomerAnalysisRepository repository = customerRepositoryFactory.apply(connection);
            CustomerDetail base = repository.loadBaseDetail(customerCode, year);
            List<java.time.LocalDate> dates = repository.loadPurchaseDates(customerCode, year);
            CustomerFrequency frequency = new CustomerFrequencyCalculator().calculate(dates);
            SalesDataCoverage coverage = repository.loadDataCoverage();
            java.time.LocalDate reference = new CustomerFollowUpCalculator().referenceDate(year, coverage.through(), java.time.LocalDate.now());
            long recency = java.time.temporal.ChronoUnit.DAYS.between(base.lastPurchase(), reference);
            CustomerDetail detail = new CustomerDetail(base.code(), base.name(), base.country(), base.category(),
                    base.priceList(), base.agent(), base.customerType(), base.revenue(), base.quantity(), base.documents(),
                    base.averageDocumentValue(), base.averageDocumentQuantity(), frequency.averageDays(), frequency.typicalDays(),
                    base.firstPurchase(), base.lastPurchase(), Math.max(recency, 0));
            int fromYear = Math.max(year - previousYears, 0);
            java.time.LocalDate trendReference = trendPeriodResolver.referenceDate(year, coverage, java.time.LocalDate.now());
            int trendFromYear = Math.max(trendReference.getYear() - 2, 0);
            List<CustomerYearSummary> yearly = repository.loadYearlyHistory(customerCode);
            List<CustomerMonthlyValue> trendMonths = monthlySeriesNormalizer.fillThroughMonth(
                    repository.loadMonthlyHistory(customerCode, trendFromYear, year), java.time.YearMonth.from(trendReference));
            CustomerTrendComparison rolling = loadTrendComparison(repository, customerCode,
                    trendPeriodResolver.rolling(trendReference), coverage, false);
            CustomerTrendComparison annual = loadTrendComparison(repository, customerCode,
                    trendPeriodResolver.annual(trendReference), coverage, true);
            int historyFromYear = yearly.isEmpty() ? year : yearly.getFirst().year();
            List<DailyRevenueValue> revenueHistory = repository.loadDailyRevenueHistory(customerCode, historyFromYear, year);
            RevenueProjection projection = projectionCalculator.calculate(year, detail.revenue(),
                    coverage,
                    revenueHistory,
                    year == java.time.Year.now().getValue());
            MonthlyProjections monthlyProjections = new CustomerProjectionService().load(repository, detail, year,
                    historyFromYear, coverage, projection, revenueHistory);
            return new CustomerDetailData(detail, yearly,
                    monthlySeriesNormalizer.fillMissingMonths(
                            repository.loadMonthlyHistory(customerCode, fromYear, year), fromYear, year),
                    repository.loadProducts(customerCode, year), customerTrendCalculator.calculate(trendMonths, rolling, annual),
                    projection, monthlyProjections);
        }), executor);
    }

    public CompletableFuture<List<CustomerMonthlyValue>> loadCustomerMonthlyHistory(
            String customerCode, int year, int previousYears
    ) {
        return CompletableFuture.supplyAsync(() -> connectionProvider.withConnection(connection -> {
            int fromYear = Math.max(year - previousYears, 0);
            List<CustomerMonthlyValue> values = customerRepositoryFactory.apply(connection)
                    .loadMonthlyHistory(customerCode, fromYear, year);
            return monthlySeriesNormalizer.fillMissingMonths(values, fromYear, year);
        }), executor);
    }

    private CustomerTrendComparison loadTrendComparison(CustomerAnalysisRepository repository, String customerCode,
                                                        CustomerTrendPeriod period, SalesDataCoverage coverage,
                                                        boolean annual) {
        return customerTrendCalculator.compare(period,
                repository.loadPeriodTotals(customerCode, period.from(), period.through()),
                repository.loadPeriodTotals(customerCode, period.previousFrom(), period.previousThrough()),
                coverage, annual);
    }

    public CompletableFuture<AnalysisPageData<ProductAnalysisItem>> loadProducts(Integer year) {
        return CompletableFuture.supplyAsync(() -> connectionProvider.withConnection(connection -> {
            SalesAnalysisRepository repository = repositoryFactory.apply(connection);
            List<Integer> years = repository.findAvailableYears();
            int selected = selectYear(year, years);
            List<ProductAnalysisItem> items = repository.loadProducts(selected);
            AnalysisSummary summary = new AnalysisSummary(
                    items.stream().map(ProductAnalysisItem::revenue).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                    items.stream().map(ProductAnalysisItem::quantity).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                    java.math.BigDecimal.ZERO, items.size());
            return new AnalysisPageData<>(years, selected, summary, items);
        }), executor);
    }

    public CompletableFuture<AnalysisPageData<DiscountAnalysisItem>> loadDiscounts(Integer year) {
        return CompletableFuture.supplyAsync(() -> connectionProvider.withConnection(connection -> {
            SalesAnalysisRepository repository = repositoryFactory.apply(connection);
            List<Integer> years = repository.findAvailableYears();
            int selected = selectYear(year, years);
            return new AnalysisPageData<>(years, selected, repository.loadDiscountSummary(selected),
                    repository.loadDiscountsByCustomer(selected));
        }), executor);
    }

    public CompletableFuture<AnalysisPageData<ReturnAnalysisItem>> loadReturns(Integer year) {
        return CompletableFuture.supplyAsync(() -> connectionProvider.withConnection(connection -> {
            SalesAnalysisRepository repository = repositoryFactory.apply(connection);
            List<Integer> years = repository.findAvailableYears();
            int selected = selectYear(year, years);
            return new AnalysisPageData<>(years, selected, repository.loadReturnSummary(selected),
                    repository.loadReturnsByCustomer(selected));
        }), executor);
    }

    public CompletableFuture<AnalysisPageData<SalesArchiveItem>> loadArchive(Integer year, String search) {
        return CompletableFuture.supplyAsync(() -> connectionProvider.withConnection(connection -> {
            SalesAnalysisRepository repository = repositoryFactory.apply(connection);
            List<Integer> years = repository.findAvailableYears();
            int selected = selectYear(year, years);
            List<SalesArchiveItem> items = repository.loadArchive(selected, search, ARCHIVE_LIMIT);
            return new AnalysisPageData<>(years, selected,
                    new AnalysisSummary(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                            java.math.BigDecimal.ZERO, items.size()), items);
        }), executor);
    }

    private int selectYear(Integer requested, List<Integer> years) {
        if (requested != null && years.contains(requested)) return requested;
        return years.isEmpty() ? Year.now().getValue() : years.getFirst();
    }
}

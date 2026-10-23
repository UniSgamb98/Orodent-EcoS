package com.orodent.ecos.statistiche.features.sales.analysis.service;

import com.orodent.ecos.statistiche.features.sales.projection.model.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.statistiche.features.sales.analysis.model.*;
import com.orodent.ecos.statistiche.features.sales.analysis.repository.SalesAnalysisRepository;
import com.orodent.ecos.statistiche.features.sales.customers.model.*;
import com.orodent.ecos.statistiche.features.sales.customers.repository.CustomerAnalysisRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalesAnalysisServiceTest {
    @Test
    void selectsLatestYearAndLoadsRequestedAnalysis() {
        FakeRepository repository = new FakeRepository();
        SalesAnalysisService service = new SalesAnalysisService(connectionProvider(), Runnable::run, c -> repository);

        AnalysisPageData<CustomerAnalysisItem> data = service.loadCustomers(null).join();

        assertEquals(2026, data.selectedYear());
        assertEquals("Cliente Uno", data.items().getFirst().name());
        assertEquals(2026, repository.loadedYear);
    }

    @Test
    void loadsCustomerOverviewWithoutLoadingIndividualCustomerHistory() {
        FakeRepository repository = new FakeRepository();
        FakeCustomerRepository customers = new FakeCustomerRepository();
        SalesAnalysisService service = new SalesAnalysisService(connectionProvider(), Runnable::run,
                c -> repository, c -> customers);
        CustomerOverviewData result = service.loadCustomerOverview(2025).join();
        assertEquals(2025, result.customers().selectedYear());
        assertEquals("Cliente Uno", result.customers().items().getFirst().name());
        assertEquals(1, customers.batchRequests);
        assertEquals(0, customers.individualRequests);
        assertEquals(0, customers.historyRequests);
        assertEquals(0, customers.periodStarts.size());
    }

    @Test
    void passesSearchTextToArchiveRepository() {
        FakeRepository repository = new FakeRepository();
        SalesAnalysisService service = new SalesAnalysisService(connectionProvider(), Runnable::run, c -> repository);

        service.loadArchive(2025, "cliente uno").join();

        assertEquals(2025, repository.loadedYear);
        assertEquals("cliente uno", repository.search);
    }

    @Test
    void calculatesAverageTypicalFrequencyAndAveragePurchaseQuantity() {
        FakeRepository repository = new FakeRepository();
        FakeCustomerRepository customers = new FakeCustomerRepository();
        SalesAnalysisService service = new SalesAnalysisService(
                connectionProvider(), Runnable::run, c -> repository, c -> customers);

        CustomerDetail detail = service.loadCustomerDetail("C1", 2025, 2).join().detail();

        assertEquals(new BigDecimal("10.0"), detail.averageFrequencyDays());
        assertEquals(new BigDecimal("10.0"), detail.typicalFrequencyDays());
        assertEquals(new BigDecimal("4.00"), detail.averageDocumentQuantity());
        assertEquals(344, detail.daysSinceLastPurchase());
    }

    @Test
    void exposesTheCoverageOfTheWholeSalesArchive() {
        FakeRepository repository = new FakeRepository();
        FakeCustomerRepository customers = new FakeCustomerRepository();
        SalesAnalysisService service = new SalesAnalysisService(
                connectionProvider(), Runnable::run, c -> repository, c -> customers);

        RevenueProjection projection = service.loadCustomerDetail("C1", 2025, 2)
                .join().projection();

        assertEquals(java.time.LocalDate.of(2020, 1, 1), projection.dataFrom());
        assertEquals(java.time.LocalDate.of(2025, 12, 31), projection.dataThrough());
    }

    @Test
    void loadsOnlyRequestedMonthlyComparisonRange() {
        FakeRepository repository = new FakeRepository();
        FakeCustomerRepository customers = new FakeCustomerRepository();
        SalesAnalysisService service = new SalesAnalysisService(
                connectionProvider(), Runnable::run, c -> repository, c -> customers);

        service.loadCustomerMonthlyHistory("C1", 2026, 3).join();

        assertEquals("C1", customers.monthlyCustomerCode);
        assertEquals(2023, customers.monthlyFromYear);
        assertEquals(2026, customers.monthlyToYear);
    }

    @Test
    void usesCommonArchiveCoverageEvenWhenTheCustomerLastBoughtInJanuary() {
        FakeCustomerRepository customers = new FakeCustomerRepository();
        SalesAnalysisService service = new SalesAnalysisService(connectionProvider(), Runnable::run,
                c -> new FakeRepository(), c -> customers);
        var data = service.loadCustomerDetail("C1", 2025, 2).join();
        assertEquals(java.time.LocalDate.of(2025, 12, 31), data.trend().summary().annual().period().through());
        assertEquals(java.time.LocalDate.of(2025, 12, 31), data.trend().summary().rolling().period().through());
        assertEquals(24, data.trend().points().size());
        assertEquals(12, data.trend().points().getLast().month());
        assertEquals(0, data.trend().points().getLast().orders());
        assertEquals(List.of(
                java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2024, 1, 1),
                java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2024, 1, 1)), customers.periodStarts);
    }

    @Test
    void overviewLoadsFollowUpDatesInOneBatchAndUsesArchiveReference() {
        FakeCustomerRepository customers = new FakeCustomerRepository();
        customers.through = java.time.LocalDate.of(2025, 1, 31);
        SalesAnalysisService service = new SalesAnalysisService(connectionProvider(), Runnable::run,
                connection -> new FakeRepository(), connection -> customers);
        var overview = service.loadCustomerOverview(2025).join();
        assertEquals(customers.through, overview.followUp().reference());
        assertEquals(1, customers.batchRequests);
        assertEquals(0, customers.individualRequests);
        assertEquals(2025, customers.batchYear);
        assertEquals(customers.through, customers.batchThrough);
        var item = overview.followUp().items().getFirst();
        assertEquals(10, item.recencyDays());
        assertEquals(new BigDecimal("10.0"), item.frequency().averageDays());
        assertEquals(new BigDecimal("10.0"), item.frequency().typicalDays());
        assertEquals(overview.customers().items().getFirst(), item.customer());
        assertEquals(10, service.loadCustomerDetail("C1", 2025, 1).join().detail().daysSinceLastPurchase());
    }

    private ConnectionProvider connectionProvider() {
        return () -> (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> null);
    }

    private static final class FakeRepository implements SalesAnalysisRepository {
        int loadedYear;
        String search;
        public List<Integer> findAvailableYears() { return List.of(2026, 2025); }
        public List<CustomerAnalysisItem> loadCustomers(int year) {
            loadedYear = year;
            return List.of(new CustomerAnalysisItem("C1", "Cliente Uno", BigDecimal.TEN,
                    BigDecimal.ONE, 1, java.time.LocalDate.of(year, 1, 1)));
        }
        public List<ProductAnalysisItem> loadProducts(int year) { loadedYear = year; return List.of(); }
        public AnalysisSummary loadDiscountSummary(int year) { return AnalysisSummary.empty(); }
        public List<DiscountAnalysisItem> loadDiscountsByCustomer(int year) { loadedYear = year; return List.of(); }
        public AnalysisSummary loadReturnSummary(int year) { return AnalysisSummary.empty(); }
        public List<ReturnAnalysisItem> loadReturnsByCustomer(int year) { loadedYear = year; return List.of(); }
        public List<SalesArchiveItem> loadArchive(int year, String search, int limit) {
            loadedYear = year; this.search = search; return List.of();
        }
    }

    private static final class FakeCustomerRepository implements CustomerAnalysisRepository {
        final java.util.List<java.time.LocalDate> periodStarts = new java.util.ArrayList<>();
        int batchRequests, individualRequests, batchYear, historyRequests;
        java.time.LocalDate batchThrough;
        java.time.LocalDate through = java.time.LocalDate.of(2025, 12, 31);
        String monthlyCustomerCode;
        int monthlyFromYear;
        int monthlyToYear;
        public CustomerDetail loadBaseDetail(String code, int year) {
            return new CustomerDetail(code, "Cliente Uno", "IT", null, null, null, "Nazionale",
                    new BigDecimal("100"), new BigDecimal("12"), 3, new BigDecimal("33.33"),
                    new BigDecimal("4.00"), null, null, java.time.LocalDate.of(2025, 1, 1),
                    java.time.LocalDate.of(2025, 1, 21), 0);
        }
        public java.util.Map<String, List<java.time.LocalDate>> loadPurchaseDates(int year, java.time.LocalDate through) {
            batchRequests++; batchYear = year; batchThrough = through;
            return java.util.Map.of("C1", List.of(java.time.LocalDate.of(2025, 1, 1),
                    java.time.LocalDate.of(2025, 1, 11), java.time.LocalDate.of(2025, 1, 21)));
        }
        public List<java.time.LocalDate> loadPurchaseDates(String code, int year) {
            individualRequests++;
            return List.of(java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2025, 1, 11),
                    java.time.LocalDate.of(2025, 1, 21));
        }
        public List<CustomerYearSummary> loadYearlyHistory(String code) { historyRequests++; return List.of(); }
        public List<CustomerMonthlyValue> loadMonthlyHistory(String code, int fromYear, int toYear) {
            monthlyCustomerCode = code;
            monthlyFromYear = fromYear;
            monthlyToYear = toYear;
            return List.of();
        }
        public CustomerPeriodTotals loadPeriodTotals(String code, java.time.LocalDate from, java.time.LocalDate through) {
            periodStarts.add(from);
            return CustomerPeriodTotals.empty();
        }
        public List<CustomerProductItem> loadProducts(String code, int year) { return List.of(); }
        public SalesDataCoverage loadDataCoverage() {
            return new SalesDataCoverage(java.time.LocalDate.of(2020, 1, 1), through);
        }
        public List<com.orodent.ecos.statistiche.features.sales.projection.model.DailyMetricValue> loadDailyQuantityHistory(String code, int fromYear, int toYear) { return List.of(); }
        public List<DailyRevenueValue> loadDailyRevenueHistory(String code, int fromYear, int toYear) {
            historyRequests++;
            return List.of();
        }
    }
}

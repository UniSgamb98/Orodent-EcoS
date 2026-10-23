package com.orodent.ecos.statistiche.features.sales.dashboard.repository;

import com.orodent.ecos.statistiche.features.sales.dashboard.model.MonthlySales;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesFilter;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesRankingItem;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesSummary;

import com.orodent.ecos.statistiche.features.sales.dashboard.model.TopCustomerHistory;
import java.util.List;
import java.util.Optional;
import com.orodent.ecos.statistiche.features.sales.projection.model.DailyRevenueValue;
import com.orodent.ecos.statistiche.features.sales.projection.model.DailyMetricValue;
import com.orodent.ecos.statistiche.features.sales.projection.model.SalesDataCoverage;

public interface SalesStatisticsRepository {

    List<Integer> findAvailableYears();

    List<TopCustomerHistory> loadTopCustomerHistory(int selectedYear, int limit);

    Optional<SalesDataCoverage> loadDataCoverage(int year);

    List<DailyRevenueValue> loadDailyRevenueHistory(int fromYear, int toYear);

    List<DailyMetricValue> loadDailyQuantityHistory(int fromYear, int toYear);

    List<DailyMetricValue> loadDailyDocumentHistory(int fromYear, int toYear);

    SalesSummary loadSummary(SalesFilter filter);

    List<MonthlySales> loadMonthlySales(SalesFilter filter);

    List<SalesRankingItem> loadTopCustomers(SalesFilter filter, int limit);

    List<SalesRankingItem> loadTopProducts(SalesFilter filter, int limit);
}

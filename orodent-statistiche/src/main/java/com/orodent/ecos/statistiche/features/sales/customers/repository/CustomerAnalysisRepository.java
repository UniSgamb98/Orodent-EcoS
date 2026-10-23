package com.orodent.ecos.statistiche.features.sales.customers.repository;

import com.orodent.ecos.statistiche.features.sales.projection.model.*;

import com.orodent.ecos.statistiche.features.sales.customers.model.*;
import java.time.LocalDate;
import java.util.List;

public interface CustomerAnalysisRepository {
    CustomerDetail loadBaseDetail(String customerCode, int year);
    java.util.Map<String, List<LocalDate>> loadPurchaseDates(int year, LocalDate through);
    List<LocalDate> loadPurchaseDates(String customerCode, int year);
    List<CustomerYearSummary> loadYearlyHistory(String customerCode);
    List<CustomerMonthlyValue> loadMonthlyHistory(String customerCode, int fromYear, int toYear);
    CustomerPeriodTotals loadPeriodTotals(String customerCode, LocalDate from, LocalDate through);
    List<CustomerProductItem> loadProducts(String customerCode, int year);
    SalesDataCoverage loadDataCoverage();
    List<DailyMetricValue> loadDailyQuantityHistory(String customerCode, int fromYear, int toYear);
    List<DailyRevenueValue> loadDailyRevenueHistory(String customerCode, int fromYear, int toYear);
}

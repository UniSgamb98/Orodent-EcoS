package com.orodent.ecos.statistiche.features.sales.analysis.repository;

import com.orodent.ecos.statistiche.features.sales.analysis.model.*;

import java.util.List;

public interface SalesAnalysisRepository {
    List<Integer> findAvailableYears();
    List<CustomerAnalysisItem> loadCustomers(int year);
    List<ProductAnalysisItem> loadProducts(int year);
    AnalysisSummary loadDiscountSummary(int year);
    List<DiscountAnalysisItem> loadDiscountsByCustomer(int year);
    AnalysisSummary loadReturnSummary(int year);
    List<ReturnAnalysisItem> loadReturnsByCustomer(int year);
    List<SalesArchiveItem> loadArchive(int year, String search, int limit);
}

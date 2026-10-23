package com.orodent.ecos.statistiche.features.sales.products.repository;

import com.orodent.ecos.statistiche.features.sales.products.model.ProductSale;
import com.orodent.ecos.statistiche.features.sales.projection.model.SalesDataCoverage;
import java.time.LocalDate;
import java.util.List;

public interface ProductAnalysisRepository {
    List<Integer> findYears();
    SalesDataCoverage loadCoverage();
    List<ProductSale> loadSales(LocalDate from, LocalDate through);
}

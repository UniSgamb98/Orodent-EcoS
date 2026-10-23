package com.orodent.ecos.statistiche.features.sales.products.model;

import java.util.List;
public record ProductOverview(ProductSalesSnapshot snapshot, ProductGrouping grouping,
                              List<ProductGroupSummary> groups, ProductTotals classifiedTotals,
                              List<UnclassifiedProduct> unclassified, ProductTotals unclassifiedTotals) {
    public ProductOverview { groups = List.copyOf(groups); unclassified = List.copyOf(unclassified); }
}

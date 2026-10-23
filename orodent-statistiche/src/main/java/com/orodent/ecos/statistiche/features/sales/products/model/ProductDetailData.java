package com.orodent.ecos.statistiche.features.sales.products.model;

import java.util.List;
import com.orodent.ecos.statistiche.features.sales.projection.model.MonthlyProjections;
import java.util.Map;
public record ProductDetailData(ProductGroupSummary group, ProductSalesSnapshot snapshot,
                                List<ProductMonthlyValue> monthly, List<ProductCustomerValue> customers,
                                Map<ProductVariantDimension, List<ProductVariantValue>> variants, MonthlyProjections monthlyProjections) {
    public ProductDetailData { monthly = List.copyOf(monthly); customers = List.copyOf(customers); variants = Map.copyOf(variants); }
    public ProductDetailData(ProductGroupSummary group, ProductSalesSnapshot snapshot,
                             List<ProductMonthlyValue> monthly, List<ProductCustomerValue> customers,
                             Map<ProductVariantDimension, List<ProductVariantValue>> variants) {
        this(group, snapshot, monthly, customers, variants, MonthlyProjections.empty());
    }
}

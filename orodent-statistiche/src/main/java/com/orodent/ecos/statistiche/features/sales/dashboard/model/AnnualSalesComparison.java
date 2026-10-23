package com.orodent.ecos.statistiche.features.sales.dashboard.model;

import java.math.BigDecimal;
import com.orodent.ecos.statistiche.features.sales.projection.model.RevenueProjection;

public record AnnualSalesComparison(
        int year,
        SalesSummary summary,
        BigDecimal revenueChangePercentage,
        BigDecimal quantityChangePercentage,
        RevenueProjection projection
) {
}

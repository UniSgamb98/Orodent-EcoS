package com.orodent.ecos.statistiche.features.sales.customers.model;

import java.math.BigDecimal;

public record CustomerTrendComparison(
        CustomerTrendPeriod period,
        CustomerPeriodTotals current,
        CustomerPeriodTotals previous,
        BigDecimal revenueChangePercentage,
        BigDecimal quantityChangePercentage,
        BigDecimal orderChangePercentage,
        boolean completeHistory,
        boolean preliminary
) { }

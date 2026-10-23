package com.orodent.ecos.statistiche.features.sales.projection.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueProjection(
        int year,
        BigDecimal actualRevenue,
        BigDecimal projectedRemainingRevenue,
        BigDecimal projectedAnnualRevenue,
        LocalDate dataFrom,
        LocalDate dataThrough,
        ProjectionMethod method,
        ProjectionConfidence confidence,
        int historicalYears
) { }

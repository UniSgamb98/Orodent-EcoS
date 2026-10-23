package com.orodent.ecos.statistiche.features.sales.projection.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MetricProjection(int year, BigDecimal actualValue, BigDecimal projectedRemainingValue,
                               BigDecimal projectedAnnualValue, LocalDate dataFrom, LocalDate dataThrough,
                               ProjectionMethod method, ProjectionConfidence confidence, int historicalYears) {
    public static MetricProjection fromRevenue(RevenueProjection revenue) {
        return revenue == null ? null : new MetricProjection(revenue.year(), revenue.actualRevenue(),
                revenue.projectedRemainingRevenue(), revenue.projectedAnnualRevenue(), revenue.dataFrom(),
                revenue.dataThrough(), revenue.method(), revenue.confidence(), revenue.historicalYears());
    }
}

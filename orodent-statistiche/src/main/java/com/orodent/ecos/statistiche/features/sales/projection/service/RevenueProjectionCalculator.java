package com.orodent.ecos.statistiche.features.sales.projection.service;

import com.orodent.ecos.statistiche.features.sales.projection.model.*;
import java.math.BigDecimal;
import java.util.List;

/** Revenue-facing adapter preserves the projection API used by customer and dashboard charts. */
public final class RevenueProjectionCalculator {
    private final SeasonalProjectionCalculator calculator = new SeasonalProjectionCalculator(2);

    public RevenueProjection calculate(int year, BigDecimal actualRevenue, SalesDataCoverage coverage,
                                       List<DailyRevenueValue> history, boolean yearInProgress) {
        MetricProjection value = calculator.calculate(year, actualRevenue, coverage,
                history.stream().map(day -> new DailyMetricValue(day.date(), day.revenue())).toList(), yearInProgress);
        return new RevenueProjection(value.year(), value.actualValue(), value.projectedRemainingValue(),
                value.projectedAnnualValue(), value.dataFrom(), value.dataThrough(), value.method(),
                value.confidence(), value.historicalYears());
    }
}

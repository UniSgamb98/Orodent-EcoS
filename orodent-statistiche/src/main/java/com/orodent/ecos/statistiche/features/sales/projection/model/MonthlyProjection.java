package com.orodent.ecos.statistiche.features.sales.projection.model;

import java.math.BigDecimal;
import java.util.List;

/** Monthly totals: actual plus only the still unregistered part of each month. */
public record MonthlyProjection(MetricProjection annual, List<Point> points, boolean seasonal) {
    public MonthlyProjection { points = List.copyOf(points); }
    public record Point(int month, BigDecimal actual, BigDecimal remaining) {
        public BigDecimal total() { return actual.add(remaining); }
    }
}

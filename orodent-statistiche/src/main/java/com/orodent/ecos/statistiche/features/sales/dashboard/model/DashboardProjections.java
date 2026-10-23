package com.orodent.ecos.statistiche.features.sales.dashboard.model;

import com.orodent.ecos.statistiche.features.sales.projection.model.MetricProjection;

/** Null fields identify measures for which no forecast is applicable or available. */
public record DashboardProjections(MetricProjection revenue, MetricProjection quantity, MetricProjection documents) {
    public static DashboardProjections empty() { return new DashboardProjections(null, null, null); }
}

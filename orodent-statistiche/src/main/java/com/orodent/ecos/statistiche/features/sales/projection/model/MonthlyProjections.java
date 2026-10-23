package com.orodent.ecos.statistiche.features.sales.projection.model;

public record MonthlyProjections(MonthlyProjection revenue, MonthlyProjection quantity) {
    public static MonthlyProjections empty() { return new MonthlyProjections(null, null); }
}

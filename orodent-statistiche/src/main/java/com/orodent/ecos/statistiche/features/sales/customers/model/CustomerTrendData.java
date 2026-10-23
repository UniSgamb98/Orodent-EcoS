package com.orodent.ecos.statistiche.features.sales.customers.model;

import java.util.List;

public record CustomerTrendData(CustomerTrendSummary summary, List<CustomerTrendPoint> points) {
    public CustomerTrendData { points = List.copyOf(points); }
}

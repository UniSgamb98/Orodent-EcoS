package com.orodent.ecos.statistiche.features.sales.products.model;

import java.math.BigDecimal;
public enum ProductMetric {
    QUANTITY("Quantità"), REVENUE("Fatturato");
    private final String label;
    ProductMetric(String label) { this.label = label; }
    public BigDecimal value(ProductTotals totals) { return this == QUANTITY ? totals.quantity() : totals.revenue(); }
    @Override public String toString() { return label; }
}

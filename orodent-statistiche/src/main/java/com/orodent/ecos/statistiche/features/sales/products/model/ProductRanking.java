package com.orodent.ecos.statistiche.features.sales.products.model;

public enum ProductRanking {
    QUANTITY("Quantità"), REVENUE("Fatturato"), CUSTOMERS("Clienti");
    private final String label;
    ProductRanking(String label) { this.label = label; }
    public ProductMetric metric() { return this == REVENUE ? ProductMetric.REVENUE : ProductMetric.QUANTITY; }
    @Override public String toString() { return label; }
}

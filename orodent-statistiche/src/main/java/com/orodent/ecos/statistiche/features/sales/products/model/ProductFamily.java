package com.orodent.ecos.statistiche.features.sales.products.model;

public enum ProductFamily {
    H("H", false), T("T senza colore", false), T_COLORED("T con colore", true),
    T4("T4", false), MS("MS", true), MST("MST", true), UMS("UMS", true), G1200("G1200", false);
    private final String label;
    private final boolean colored;
    ProductFamily(String label, boolean colored) { this.label = label; this.colored = colored; }
    public boolean colored() { return colored; }
    @Override public String toString() { return label; }
}

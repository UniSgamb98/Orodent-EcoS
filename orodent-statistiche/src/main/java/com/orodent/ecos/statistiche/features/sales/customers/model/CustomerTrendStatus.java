package com.orodent.ecos.statistiche.features.sales.customers.model;

public enum CustomerTrendStatus {
    GROWING("In crescita"),
    STABLE("Stabile"),
    SLOWING("In rallentamento"),
    DECLINING("In calo"),
    INSUFFICIENT_DATA("Dati insufficienti");

    private final String label;

    CustomerTrendStatus(String label) { this.label = label; }
    public String label() { return label; }
}

package com.orodent.ecos.statistiche.features.sales.products.model;

public enum ProductGrouping {
    FAMILY("Famiglia"), FAMILY_COLOR("Famiglia e colore"), MATERIAL("Materia prima");
    private final String label;
    ProductGrouping(String label) { this.label = label; }
    @Override public String toString() { return label; }
}

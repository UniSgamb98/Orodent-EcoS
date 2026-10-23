package com.orodent.ecos.statistiche.features.sales.products.model;

public enum ProductVariantDimension {
    FAMILY("Famiglia"), COLOR("Colore"), HEIGHT("Altezza"), DIAMETER("Diametro");
    private final String label;
    ProductVariantDimension(String label) { this.label = label; }
    public String value(ProductAttributes attributes) {
        return switch (this) {
            case FAMILY -> attributes.family().toString();
            case COLOR -> attributes.color().isEmpty() ? "Non previsto" : attributes.color();
            case HEIGHT -> Integer.toString(attributes.height());
            case DIAMETER -> Integer.toString(attributes.diameter());
        };
    }
    public boolean availableFor(ProductGroupKey group) {
        return switch (this) {
            case FAMILY -> group.allFamilies();
            case COLOR -> group.color().isEmpty() && (group.allFamilies() || group.family().colored());
            case HEIGHT, DIAMETER -> true;
        };
    }
    @Override public String toString() { return label; }
}

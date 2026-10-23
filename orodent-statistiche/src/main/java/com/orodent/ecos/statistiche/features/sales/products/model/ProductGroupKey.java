package com.orodent.ecos.statistiche.features.sales.products.model;

/** A null family denotes an aggregate across all classified families of the material. */
public record ProductGroupKey(String material, ProductFamily family, String color) {
    public static ProductGroupKey of(ProductAttributes attributes, ProductGrouping grouping) {
        return new ProductGroupKey(attributes.material(), grouping == ProductGrouping.MATERIAL ? null : attributes.family(),
                grouping == ProductGrouping.FAMILY_COLOR ? attributes.color() : "");
    }
    public boolean matches(ProductAttributes attributes) {
        return material.equals(attributes.material()) && (allFamilies() || family == attributes.family())
                && (color.isEmpty() || color.equals(attributes.color()));
    }
    public boolean allFamilies() { return family == null; }
    public String label() { return material + (allFamilies() ? "" : " · " + family) + (color.isEmpty() ? "" : " · " + color); }
    public boolean relatedTo(ProductGroupKey other) {
        return material.equals(other.material()) && (allFamilies() || other.allFamilies() || family == other.family());
    }
}

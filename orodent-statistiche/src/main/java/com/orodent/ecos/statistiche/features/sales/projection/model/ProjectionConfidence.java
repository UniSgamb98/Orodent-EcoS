package com.orodent.ecos.statistiche.features.sales.projection.model;

public enum ProjectionConfidence {
    HIGH("Alta"), MEDIUM("Media"), LOW("Bassa"), ACTUAL("Consuntivo");
    private final String label;
    ProjectionConfidence(String label) { this.label = label; }
    public String label() { return label; }
}

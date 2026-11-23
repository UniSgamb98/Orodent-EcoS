package com.orodent.ecos.misurazioni.model;

public enum Instrument {
    MICROMETER("Micrometro"),
    SCALE("Bilancia");

    private final String label;

    Instrument(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

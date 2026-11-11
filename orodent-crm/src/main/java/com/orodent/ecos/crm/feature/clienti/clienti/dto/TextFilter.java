package com.orodent.ecos.crm.feature.clienti.clienti.dto;

public record TextFilter(String value, String label) {
    public static TextFilter empty(String label) {
        return new TextFilter("", label);
    }

    public boolean isEmptyOption() {
        return value == null || value.isBlank();
    }

    @Override
    public String toString() {
        return label;
    }
}

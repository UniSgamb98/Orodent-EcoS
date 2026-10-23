package com.orodent.ecos.statistiche.features.sales.customers.model;

import java.math.BigDecimal;

public enum CustomerFrequencyBasis {
    TYPICAL("Frequenza tipica"), AVERAGE("Frequenza media");
    private final String label;
    CustomerFrequencyBasis(String label) { this.label = label; }
    public BigDecimal days(CustomerFrequency frequency) {
        return this == TYPICAL ? frequency.typicalDays() : frequency.averageDays();
    }
    @Override public String toString() { return label; }
}

package com.orodent.ecos.statistiche.features.sales.dashboard.model;

import java.time.LocalDate;
import java.util.Objects;

public record SalesFilter(LocalDate from, LocalDate to) {

    public SalesFilter {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("La data iniziale non può essere successiva a quella finale");
        }
    }

    public static SalesFilter wholeYear(int year) {
        return new SalesFilter(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }
}

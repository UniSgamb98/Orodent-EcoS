package com.orodent.ecos.statistiche.features.sales.customers.model;

import java.math.BigDecimal;

public enum CustomerTrendDirection {
    UP, STABLE, DOWN, UNAVAILABLE;

    public static CustomerTrendDirection forValue(BigDecimal change) {
        return classify(change, BigDecimal.valueOf(3));
    }

    public static CustomerTrendDirection forOrders(BigDecimal change) {
        return classify(change, BigDecimal.TEN);
    }

    private static CustomerTrendDirection classify(BigDecimal change, BigDecimal threshold) {
        if (change == null) return UNAVAILABLE;
        if (change.compareTo(threshold) >= 0) return UP;
        if (change.compareTo(threshold.negate()) <= 0) return DOWN;
        return STABLE;
    }
}

package com.orodent.ecos.statistiche.features.sales.customers.model;

import java.math.BigDecimal;

public record CustomerPeriodTotals(BigDecimal revenue, BigDecimal quantity, int orders) {
    public static CustomerPeriodTotals empty() {
        return new CustomerPeriodTotals(BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }
}

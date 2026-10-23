package com.orodent.ecos.statistiche.features.sales.dashboard.model;

import java.math.BigDecimal;

public record SalesSummary(
        BigDecimal netRevenue,
        BigDecimal quantity,
        int customers,
        int documents,
        BigDecimal totalDiscount
) {
    public static SalesSummary empty() {
        return new SalesSummary(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO);
    }

    public BigDecimal averageDocumentValue() {
        if (documents == 0) {
            return BigDecimal.ZERO;
        }
        return netRevenue.divide(BigDecimal.valueOf(documents), 2, java.math.RoundingMode.HALF_UP);
    }
}

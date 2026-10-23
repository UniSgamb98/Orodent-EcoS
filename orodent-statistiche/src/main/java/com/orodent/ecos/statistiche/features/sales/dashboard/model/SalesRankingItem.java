package com.orodent.ecos.statistiche.features.sales.dashboard.model;

import java.math.BigDecimal;

public record SalesRankingItem(
        String code,
        String description,
        BigDecimal netRevenue,
        BigDecimal quantity,
        int documents,
        BigDecimal percentage
) {
    public SalesRankingItem withPercentage(BigDecimal value) {
        return new SalesRankingItem(code, description, netRevenue, quantity, documents, value);
    }
}

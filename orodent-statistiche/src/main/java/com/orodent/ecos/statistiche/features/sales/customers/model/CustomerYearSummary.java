package com.orodent.ecos.statistiche.features.sales.customers.model;

import java.math.BigDecimal;

public record CustomerYearSummary(int year, BigDecimal revenue, BigDecimal quantity, int documents) {
    public BigDecimal averageDocumentValue() {
        return documents == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(documents), 2, java.math.RoundingMode.HALF_UP);
    }
    public BigDecimal averageDocumentQuantity() {
        return documents == 0 ? BigDecimal.ZERO : quantity.divide(BigDecimal.valueOf(documents), 2, java.math.RoundingMode.HALF_UP);
    }
}

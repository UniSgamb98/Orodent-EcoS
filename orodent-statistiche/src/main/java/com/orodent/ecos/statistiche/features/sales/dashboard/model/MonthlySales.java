package com.orodent.ecos.statistiche.features.sales.dashboard.model;

import java.math.BigDecimal;

public record MonthlySales(int month, BigDecimal netRevenue, BigDecimal quantity, int documents) {
}

package com.orodent.ecos.statistiche.features.sales.products.model;

import java.math.BigDecimal;
public record ProductTotals(BigDecimal revenue, BigDecimal quantity, int customers, int orders,
                            BigDecimal returnedRevenue, BigDecimal returnedQuantity) { }

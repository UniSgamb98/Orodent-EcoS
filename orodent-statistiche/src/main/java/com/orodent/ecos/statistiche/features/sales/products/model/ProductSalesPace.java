package com.orodent.ecos.statistiche.features.sales.products.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Historical positive sales; this is neither stock on hand nor a depletion forecast. */
public record ProductSalesPace(LocalDate from, LocalDate through, long days,
                               BigDecimal soldQuantity, int documents,
                               BigDecimal weeklyQuantity, BigDecimal meanDocumentQuantity,
                               BigDecimal medianDocumentQuantity,
                               LocalDate recentFrom, long recentDays, BigDecimal recentWeeklyQuantity) { }

package com.orodent.ecos.statistiche.features.sales.products.model;

import java.math.BigDecimal;
import java.time.LocalDate;
/** One document/product/date/operation aggregate; signed values are already net of corrections. */
public record ProductSale(String code, String description, LocalDate date, String customerCode,
                          String customerName, String document, boolean sale, BigDecimal revenue, BigDecimal quantity) { }

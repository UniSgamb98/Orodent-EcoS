package com.orodent.ecos.statistiche.features.sales.customers.model;
import java.math.BigDecimal;
public record CustomerProductItem(String code, String description, BigDecimal revenue, BigDecimal quantity) { }

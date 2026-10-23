package com.orodent.ecos.statistiche.features.sales.customers.model;
import java.math.BigDecimal;
public record CustomerMonthlyValue(int year, int month, BigDecimal revenue, BigDecimal quantity, int documents) { }

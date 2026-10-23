package com.orodent.ecos.statistiche.features.sales.dashboard.model;
import java.math.BigDecimal;
public record TopCustomerHistory(String customerCode, String customerName, int year, BigDecimal revenue) { }

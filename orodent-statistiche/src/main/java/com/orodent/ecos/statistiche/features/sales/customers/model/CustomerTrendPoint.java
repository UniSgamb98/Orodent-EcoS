package com.orodent.ecos.statistiche.features.sales.customers.model;

import java.math.BigDecimal;

public record CustomerTrendPoint(int year, int month, int orders, BigDecimal movingAverage) { }

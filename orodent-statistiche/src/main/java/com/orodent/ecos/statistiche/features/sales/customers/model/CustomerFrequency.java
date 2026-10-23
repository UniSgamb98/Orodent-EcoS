package com.orodent.ecos.statistiche.features.sales.customers.model;

import java.math.BigDecimal;

public record CustomerFrequency(BigDecimal averageDays, BigDecimal typicalDays, int intervals) { }

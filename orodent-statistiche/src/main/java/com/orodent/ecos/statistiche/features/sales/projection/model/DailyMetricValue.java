package com.orodent.ecos.statistiche.features.sales.projection.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyMetricValue(LocalDate date, BigDecimal value) { }

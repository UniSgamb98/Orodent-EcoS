package com.orodent.ecos.statistiche.features.sales.analysis.model;

import java.math.BigDecimal;

public record DiscountAnalysisItem(String code, String name, BigDecimal revenue,
                                   BigDecimal discount, BigDecimal averagePercentage) { }

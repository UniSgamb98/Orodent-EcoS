package com.orodent.ecos.statistiche.features.sales.analysis.model;

import java.math.BigDecimal;

public record AnalysisSummary(BigDecimal primaryValue, BigDecimal secondaryValue,
                              BigDecimal tertiaryValue, int count) {
    public static AnalysisSummary empty() {
        return new AnalysisSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }
}

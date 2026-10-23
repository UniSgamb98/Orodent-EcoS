package com.orodent.ecos.statistiche.features.sales.analysis.model;

import java.math.BigDecimal;

public record ReturnAnalysisItem(String code, String name, BigDecimal amount,
                                 BigDecimal quantity, int documents) { }

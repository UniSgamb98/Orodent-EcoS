package com.orodent.ecos.statistiche.features.sales.analysis.model;

import java.math.BigDecimal;

public record ProductAnalysisItem(String code, String description, BigDecimal revenue,
                                  BigDecimal quantity, int customers, int documents) { }

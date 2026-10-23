package com.orodent.ecos.statistiche.features.sales.analysis.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerAnalysisItem(String code, String name, BigDecimal revenue, BigDecimal quantity,
                                   int documents, LocalDate lastSale, String priceList) {
    public CustomerAnalysisItem(String code, String name, BigDecimal revenue, BigDecimal quantity,
                                int documents, LocalDate lastSale) {
        this(code, name, revenue, quantity, documents, lastSale, null);
    }
}

package com.orodent.ecos.statistiche.features.sales.customers.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerDetail(
        String code, String name, String country, String category, String priceList, String agent, String customerType,
        BigDecimal revenue, BigDecimal quantity, int documents, BigDecimal averageDocumentValue,
        BigDecimal averageDocumentQuantity, BigDecimal averageFrequencyDays, BigDecimal typicalFrequencyDays,
        LocalDate firstPurchase, LocalDate lastPurchase, long daysSinceLastPurchase
) { }

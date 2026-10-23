package com.orodent.ecos.statistiche.features.sales.analysis.model;

import com.orodent.ecos.database.model.TipoOperazione;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesArchiveItem(LocalDate date, String document, String customerCode, String customerName,
                               String productCode, String productDescription, BigDecimal quantity,
                               BigDecimal discountPercentage, BigDecimal netAmount, TipoOperazione operation) { }

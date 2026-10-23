package com.orodent.ecos.statistiche.features.sales.customers.model;

import com.orodent.ecos.statistiche.features.sales.analysis.model.CustomerAnalysisItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public record CustomerFollowUpItem(CustomerAnalysisItem customer, LocalDate lastPurchase,
                                   long recencyDays, CustomerFrequency frequency) {
    public BigDecimal delay(CustomerFrequencyBasis basis) {
        BigDecimal days = basis.days(frequency);
        return days == null ? null : BigDecimal.valueOf(recencyDays).subtract(days);
    }
    public BigDecimal priority(CustomerFrequencyBasis basis) {
        BigDecimal days = basis.days(frequency);
        return days == null || days.signum() <= 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(recencyDays).divide(days, 8, RoundingMode.HALF_UP);
    }
}

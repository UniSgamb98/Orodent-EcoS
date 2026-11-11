package com.orodent.ecos.crm.feature.calendario.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record CalendarioMonth(
        String periodLabel,
        int firstColumn,
        int dayCount,
        Integer todayDay,
        Map<LocalDate, List<CalendarioCall>> callsByDate) {
    public CalendarioMonth {
        callsByDate = callsByDate == null ? Map.of() : Map.copyOf(callsByDate);
    }
}

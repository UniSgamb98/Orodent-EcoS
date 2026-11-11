package com.orodent.ecos.crm.feature.calendario.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record CalendarioWeek(
        String periodLabel,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate today,
        Map<LocalDate, List<CalendarioCall>> callsByDate) {
    public CalendarioWeek {
        callsByDate = callsByDate == null ? Map.of() : Map.copyOf(callsByDate);
    }
}

package com.orodent.ecos.statistiche.features.sales.projection.service;

import com.orodent.ecos.statistiche.features.sales.projection.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SeasonalProjectionCalculatorTest {
    @Test void retainsFractionalUnitsAndRoundsDocumentForecastsToWholeNumbers() {
        var coverage = new SalesDataCoverage(LocalDate.of(2026,1,1),LocalDate.of(2026,1,1));
        var history = List.of(new DailyMetricValue(LocalDate.of(2024,1,1),BigDecimal.ONE),
                new DailyMetricValue(LocalDate.of(2024,12,31),BigDecimal.ONE),
                new DailyMetricValue(LocalDate.of(2025,1,1),BigDecimal.ONE),
                new DailyMetricValue(LocalDate.of(2025,12,31),BigDecimal.ONE));
        var units = new SeasonalProjectionCalculator(3).calculate(2026,new BigDecimal("5.125"),coverage,history,true);
        assertEquals(new BigDecimal("10.250"),units.projectedAnnualValue());
        var docs = new SeasonalProjectionCalculator(0).calculate(2026,new BigDecimal("3"),coverage,history,true);
        assertEquals(new BigDecimal("6"),docs.projectedAnnualValue());
        assertEquals(ProjectionMethod.SEASONAL,docs.method());
        assertEquals(new BigDecimal("3"),docs.projectedRemainingValue());
        assertEquals(ProjectionMethod.ACTUAL,new SeasonalProjectionCalculator(0).calculate(2025,new BigDecimal("3"),coverage,history,false).method());
    }
}

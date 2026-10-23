package com.orodent.ecos.statistiche.features.sales.products.service;

import com.orodent.ecos.statistiche.features.sales.products.model.*;
import com.orodent.ecos.statistiche.features.sales.projection.model.*;
import com.orodent.ecos.statistiche.features.sales.projection.service.*;
import java.time.LocalDate;
import java.util.List;

/** Forecasts only the selected product group; the snapshot retains the archive history. */
final class ProductProjectionService {
    MonthlyProjections calculate(ProductSalesSnapshot snapshot, ProductGroupSummary group, List<ProductSale> history) {
        int year = snapshot.year();
        if (year != LocalDate.now().getYear() || snapshot.reference().getYear() != year) return MonthlyProjections.empty();
        SalesDataCoverage coverage = new SalesDataCoverage(snapshot.dataFrom(), snapshot.reference());
        List<DailyMetricValue> revenue = history.stream().map(value -> new DailyMetricValue(value.date(), value.revenue())).toList();
        List<DailyMetricValue> quantity = history.stream().map(value -> new DailyMetricValue(value.date(), value.quantity())).toList();
        MonthlyProjectionCalculator monthly = new MonthlyProjectionCalculator();
        return new MonthlyProjections(
                group.current().revenue().signum() <= 0 ? null : monthly.calculate(
                        new SeasonalProjectionCalculator(2).calculate(year, group.current().revenue(), coverage, revenue, true), revenue),
                group.current().quantity().signum() <= 0 ? null : monthly.calculate(
                        new SeasonalProjectionCalculator(3).calculate(year, group.current().quantity(), coverage, quantity, true), quantity));
    }
}

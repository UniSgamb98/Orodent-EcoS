package com.orodent.ecos.statistiche.features.sales.customers.model;

import com.orodent.ecos.statistiche.features.sales.projection.model.RevenueProjection;
import java.util.List;
import com.orodent.ecos.statistiche.features.sales.projection.model.MonthlyProjections;
public record CustomerDetailData(CustomerDetail detail, List<CustomerYearSummary> yearly,
                                 List<CustomerMonthlyValue> monthly, List<CustomerProductItem> products,
                                 CustomerTrendData trend, RevenueProjection projection, MonthlyProjections monthlyProjections) {
    public CustomerDetailData { yearly=List.copyOf(yearly); monthly=List.copyOf(monthly); products=List.copyOf(products); }
    public CustomerDetailData(CustomerDetail detail, List<CustomerYearSummary> yearly,
                              List<CustomerMonthlyValue> monthly, List<CustomerProductItem> products,
                              CustomerTrendData trend, RevenueProjection projection) {
        this(detail, yearly, monthly, products, trend, projection, MonthlyProjections.empty());
    }
}

package com.orodent.ecos.statistiche.features.sales.customers.model;

import com.orodent.ecos.statistiche.features.sales.analysis.model.AnalysisPageData;
import com.orodent.ecos.statistiche.features.sales.analysis.model.CustomerAnalysisItem;
import java.time.LocalDate;
import java.util.List;

public record CustomerOverviewData(AnalysisPageData<CustomerAnalysisItem> customers, CustomerFollowUpData followUp) {
    public CustomerOverviewData(AnalysisPageData<CustomerAnalysisItem> customers) {
        this(customers, new CustomerFollowUpData(customers.selectedYear(),
                LocalDate.of(customers.selectedYear(), 12, 31), List.of()));
    }
}

package com.orodent.ecos.statistiche.internal;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.service.ClientiCsvImportService;
import com.orodent.ecos.database.service.VenditeCsvImportService;
import com.orodent.ecos.statistiche.features.sales.analysis.service.SalesAnalysisService;
import com.orodent.ecos.statistiche.features.sales.dashboard.service.SalesDashboardService;
import com.orodent.ecos.statistiche.features.sales.products.service.ProductAnalysisService;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Services composed from ecosystem-owned connections and workers. */
public record StatisticheServices(
        SalesDashboardService salesDashboardService,
        SalesAnalysisService salesAnalysisService,
        ProductAnalysisService productAnalysisService,
        ClientiCsvImportService clientiCsvImportService,
        VenditeCsvImportService venditeCsvImportService
) {
    public StatisticheServices(ConnectionProvider connections, Executor workers,
                              ClientiCsvImportService customersImport, VenditeCsvImportService salesImport) {
        this(new SalesDashboardService(Objects.requireNonNull(connections), Objects.requireNonNull(workers)),
                new SalesAnalysisService(connections, workers),
                new ProductAnalysisService(connections, workers),
                Objects.requireNonNull(customersImport), Objects.requireNonNull(salesImport));
    }
}

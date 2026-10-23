package com.orodent.ecos.statistiche;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.service.ClientiCsvImportService;
import com.orodent.ecos.database.service.VenditeCsvImportService;
import com.orodent.ecos.statistiche.internal.StatisticheController;
import com.orodent.ecos.statistiche.internal.StatisticheServices;
import javafx.scene.Parent;

import java.util.concurrent.Executor;

/** Embedded Statistiche entry point. It never owns a database, stage or worker pool. */
public final class StatisticheModule {
    private final StatisticheController controller;

    public StatisticheModule(ConnectionProvider connections, Executor workers,
                             ClientiCsvImportService customersImport, VenditeCsvImportService salesImport,
                             Runnable onEcosHome) {
        controller = new StatisticheController(
                new StatisticheServices(connections, workers, customersImport, salesImport), onEcosHome);
        controller.showHome();
    }

    public Parent root() { return controller.root(); }

    public void showDashboard() { controller.showHome(); }

    /** Releases page callbacks, without shutting down ecosystem-owned services. */
    public void dispose() { controller.dispose(); }
}

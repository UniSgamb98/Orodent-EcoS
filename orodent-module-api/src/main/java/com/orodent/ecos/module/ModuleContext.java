package com.orodent.ecos.module;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.service.ClientiCsvImportService;
import com.orodent.ecos.database.service.VenditeCsvImportService;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Borrowed services. Modules must never stop the database or the shared executors. */
public record ModuleContext(ConnectionProvider connections, Executor workers,
                            ClientiCsvImportService customersImport, VenditeCsvImportService salesImport,
                            Runnable showHome) {
    public ModuleContext {
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(workers, "workers");
        Objects.requireNonNull(customersImport, "customersImport");
        Objects.requireNonNull(salesImport, "salesImport");
        Objects.requireNonNull(showHome, "showHome");
    }
}

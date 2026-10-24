package com.orodent.ecos.statistiche;

import com.orodent.ecos.module.EcosModule;
import com.orodent.ecos.module.ModuleContext;
import com.orodent.ecos.module.ModuleDescriptor;
import com.orodent.ecos.module.ModuleSession;
import com.orodent.ecos.statistiche.internal.StatisticheController;
import com.orodent.ecos.statistiche.internal.StatisticheServices;
import javafx.scene.Parent;

/** Embedded entry point. Services and the main window remain owned by EcoS. */
public final class StatisticheModule implements EcosModule {
    private static final ModuleDescriptor DESCRIPTOR = new ModuleDescriptor(
            "statistiche", "Statistiche", "Vendite, clienti e prodotti: analisi, andamento e importazioni.",
            "M3 13h4v8H3z M10 8h4v13h-4z M17 3h4v18h-4z");

    @Override
    public ModuleDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public ModuleSession open(ModuleContext context) {
        StatisticheController controller = new StatisticheController(new StatisticheServices(
                context.connections(), context.workers(), context.customersImport(), context.salesImport()),
                context.showHome());
        controller.showHome();
        return new ModuleSession() {
            @Override public Parent root() { return controller.root(); }
            @Override public void close() { controller.dispose(); }
        };
    }
}

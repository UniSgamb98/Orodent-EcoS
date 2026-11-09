package com.orodent.ecos.costi;

import com.orodent.ecos.costi.controller.PowderPricesController;
import com.orodent.ecos.costi.service.PowderPriceService;
import com.orodent.ecos.costi.view.PowderPricesView;
import com.orodent.ecos.module.*;

import javafx.scene.Parent;

public final class CostiModule implements EcosModule {
    private static final ModuleDescriptor DESCRIPTOR =
            new ModuleDescriptor(
                    "costi",
                    "Costi e consumi",
                    "Prezzi delle polveri e quantità di riferimento per i costi e gli scenari di"
                            + " produzione.",
                    "M3 3h10l8 8-10 10-8-8z M7 7h2v2H7z");

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ModuleSession open(ModuleContext context) {
        var view = new PowderPricesView();
        var controller =
                new PowderPricesController(
                        view,
                        new PowderPriceService(context.connections(), context.workers()),
                        context.showHome());
        controller.start();
        return new ModuleSession() {
            @Override
            public Parent root() {
                return view;
            }

            @Override
            public void close() {
                controller.dispose();
            }
        };
    }
}

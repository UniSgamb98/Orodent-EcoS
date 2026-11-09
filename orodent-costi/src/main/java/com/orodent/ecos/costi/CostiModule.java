package com.orodent.ecos.costi;

import com.orodent.ecos.module.*;

import javafx.scene.Parent;

public final class CostiModule implements EcosModule {
    private static final ModuleDescriptor DESCRIPTOR =
            new ModuleDescriptor(
                    "costi",
                    "Costi e consumi",
                    "Costi di produzione, prezzi delle polveri, consumi e autonomia delle scorte.",
                    "M3 3h10l8 8-10 10-8-8z M7 7h2v2H7z");

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ModuleSession open(ModuleContext context) {
        var controller = new CostiController(context);
        return new ModuleSession() {
            @Override
            public Parent root() {
                return controller.root();
            }

            @Override
            public void close() {
                controller.dispose();
            }
        };
    }
}

package com.orodent.ecos.ton;

import com.orodent.ecos.module.EcosModule;
import com.orodent.ecos.module.ModuleContext;
import com.orodent.ecos.module.ModuleDescriptor;
import com.orodent.ecos.module.ModuleSession;
import javafx.scene.Parent;

public final class TonModule implements EcosModule {
    private static final ModuleDescriptor DESCRIPTOR = new ModuleDescriptor(
            "ton", "Ton", "Cataloghi tecnici e strumenti per il laboratorio e la produzione.",
            "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z");

    @Override
    public ModuleDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public ModuleSession open(ModuleContext context) {
        TonController controller = new TonController(context);
        return new ModuleSession() {
            @Override public Parent root() { return controller.root(); }
            @Override public void close() { controller.dispose(); }
        };
    }
}

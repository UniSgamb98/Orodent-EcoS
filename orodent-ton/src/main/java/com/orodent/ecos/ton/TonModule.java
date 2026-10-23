package com.orodent.ecos.ton;

import com.orodent.ecos.module.EcosModule;
import com.orodent.ecos.module.ModuleContext;
import com.orodent.ecos.module.ModuleDescriptor;
import com.orodent.ecos.module.ModuleSession;
import com.orodent.ecos.ton.view.TonView;
import javafx.scene.Parent;

public final class TonModule implements EcosModule {
    private static final ModuleDescriptor DESCRIPTOR = new ModuleDescriptor(
            "ton", "Ton", "Lo spazio dedicato alle funzionalità Ton.",
            "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z");

    @Override
    public ModuleDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public ModuleSession open(ModuleContext context) {
        TonView view = new TonView();
        view.homeButton().setOnAction(event -> context.showHome().run());
        return new ModuleSession() {
            @Override public Parent root() { return view; }
            @Override public void close() { view.homeButton().setOnAction(null); }
        };
    }
}

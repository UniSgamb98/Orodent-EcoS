package com.orodent.ecos.registro;

import com.orodent.ecos.module.*;

public final class RegistroModule implements EcosModule {
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(
                "registro",
                "Registro attività",
                "Chi ha creato, modificato o eliminato i dati CRM, con confronto prima e dopo.",
                "M5 3h14v18H5z M8 7h8 M8 11h8 M8 15h8");
    }

    public ModuleSession open(ModuleContext context) {
        return new RegistroController(context);
    }
}

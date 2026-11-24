package com.orodent.ecos.misurazioni;

import com.orodent.ecos.database.DatabaseSettings;
import com.orodent.ecos.misurazioni.config.MeasurementSettings;
import com.orodent.ecos.misurazioni.controller.MeasurementsController;
import com.orodent.ecos.module.*;

public final class MisurazioniModule implements EcosModule {
    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(
                "misurazioni",
                "Misurazioni",
                "Acquisizione da micrometro e bilancia, con pedale e copia dei dati per Excel.",
                "M3 4h18v16H3z M7 4v5 M11 4v3 M15 4v5 M19 4v3 M7 14h10 M7 17h6");
    }

    @Override
    public boolean enabled() {
        return MeasurementSettings.enabled(DatabaseSettings.loadApplicationProperties());
    }

    @Override
    public ModuleSession open(ModuleContext context) {
        var properties = DatabaseSettings.loadApplicationProperties();
        if (!MeasurementSettings.enabled(properties))
            throw new IllegalStateException(
                    "Misurazioni non abilitate su questa postazione. Imposta"
                        + " misurazioni.enabled=true nel file application.properties.");
        return new MeasurementsController(context.showHome(), MeasurementSettings.from(properties));
    }
}

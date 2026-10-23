package com.orodent.ecos.module;

/** Stateless provider, discovered once. Opening a module creates a fresh UI session. */
public interface EcosModule {
    ModuleDescriptor descriptor();

    /** Called on the JavaFX thread, after ecosystem services have started. */
    ModuleSession open(ModuleContext context);
}

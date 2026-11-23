package com.orodent.ecos.module;

/** Stateless provider, discovered once. Opening a module creates a fresh UI session. */
public interface EcosModule {
    ModuleDescriptor descriptor();

    /** Called on the JavaFX thread, after ecosystem services have started. */
    ModuleSession open(ModuleContext context);

    /** Open a referenced record when the module supports it. */
    default ModuleSession openRecord(ModuleContext context, String recordId) {
        return open(context);
    }
}

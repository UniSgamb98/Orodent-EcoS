package com.orodent.ecos.app.modules;

import com.orodent.ecos.module.EcosModule;
import com.orodent.ecos.module.ModuleDescriptor;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/** Validates providers before startup; the Home and navigation use the same catalog. */
public final class ModuleCatalog {
    private final Map<String, EcosModule> providers;
    private final List<ModuleDescriptor> descriptors;

    public static ModuleCatalog discover() {
        return new ModuleCatalog(ServiceLoader.load(EcosModule.class).stream()
                .map(ServiceLoader.Provider::get).toList());
    }

    public ModuleCatalog(Collection<? extends EcosModule> modules) {
        Map<String, EcosModule> entries = new LinkedHashMap<>();
        for (EcosModule module : modules) {
            ModuleDescriptor descriptor = Objects.requireNonNull(module.descriptor(), "descriptor");
            if (entries.putIfAbsent(descriptor.id(), module) != null) {
                throw new IllegalArgumentException("Identificativo modulo duplicato: " + descriptor.id());
            }
        }
        providers = Map.copyOf(entries);
        descriptors = entries.values().stream().map(EcosModule::descriptor)
                .sorted(Comparator.comparing(ModuleDescriptor::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ModuleDescriptor::id)).toList();
    }

    public List<ModuleDescriptor> descriptors() { return descriptors; }

    public EcosModule require(String id) {
        EcosModule module = providers.get(id);
        if (module == null) throw new IllegalArgumentException("Modulo non disponibile: " + id);
        return module;
    }
}

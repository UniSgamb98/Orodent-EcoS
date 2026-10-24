package com.orodent.ecos.app.modules;

import com.orodent.ecos.module.EcosModule;
import com.orodent.ecos.module.ModuleContext;
import com.orodent.ecos.module.ModuleDescriptor;
import com.orodent.ecos.module.ModuleSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModuleCatalogTest {
    @Test
    void discoversPackagedModulesWithoutOpeningTheirUiOrStartingServices() {
        ModuleCatalog catalog = ModuleCatalog.discover();
        assertEquals(List.of("statistiche", "ton"), catalog.descriptors().stream()
                .map(ModuleDescriptor::id).toList());
        assertEquals("Statistiche", catalog.require("statistiche").descriptor().name());
    }

    @Test
    void acceptsAnAdditionalProviderWithoutChangingNavigationAndSortsByName() {
        EcosModule office = provider("ufficio", "Ufficio");
        EcosModule archive = provider("archivio", "Archivio");
        ModuleCatalog catalog = new ModuleCatalog(List.of(office, archive));
        assertEquals(List.of("archivio", "ufficio"), catalog.descriptors().stream()
                .map(ModuleDescriptor::id).toList());
        assertSame(office, catalog.require("ufficio"));
        assertThrows(UnsupportedOperationException.class, () -> catalog.descriptors().clear());
    }

    @Test
    void rejectsDuplicateIdsInsteadOfSilentlyHidingAnApplication() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleCatalog(
                List.of(provider("ufficio", "Uno"), provider("ufficio", "Due"))));
    }

    @Test
    void rejectsUnknownNavigationAndInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleCatalog(List.of()).require("assente"));
        assertThrows(IllegalArgumentException.class, () -> provider("../invalid", "Nome"));
        assertThrows(IllegalArgumentException.class, () -> provider("valid", " "));
    }

    private static EcosModule provider(String id, String name) {
        ModuleDescriptor descriptor = new ModuleDescriptor(id, name, "Descrizione", "M0 0h24v24H0z");
        return new EcosModule() {
            @Override public ModuleDescriptor descriptor() { return descriptor; }
            @Override public ModuleSession open(ModuleContext context) {
                throw new AssertionError("Il catalogo non deve avviare i moduli.");
            }
        };
    }
}

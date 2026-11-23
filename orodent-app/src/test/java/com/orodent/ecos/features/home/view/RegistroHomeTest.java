package com.orodent.ecos.features.home.view;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.app.modules.ModuleCatalog;
import com.orodent.ecos.database.DatabaseMode;

import javafx.application.Platform;

import org.junit.jupiter.api.*;

import java.util.concurrent.*;

@Tag("javafx")
class RegistroHomeTest {
    @Test
    void registroUsesTheSameVisibilityToggleAsTonAndCosts() throws Exception {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException started) {
        }
        Platform.setImplicitExit(false);
        var result = new CompletableFuture<Void>();
        Platform.runLater(
                () -> {
                    try {
                        var home =
                                new HomeView(
                                        DatabaseMode.HOST,
                                        ModuleCatalog.discover().descriptors(),
                                        id -> {});
                        new javafx.scene.Scene(home, 1120, 760);
                        home.applyCss();
                        home.layout();
                        for (String id : java.util.List.of("ton", "costi", "registro")) {
                            assertFalse(home.lookup("#module-" + id).isVisible());
                            assertFalse(home.lookup("#module-" + id).isManaged());
                        }
                        assertTrue(home.lookup("#module-crm").isVisible());
                        home.showHiddenModules(true);
                        for (String id : java.util.List.of("ton", "costi", "registro")) {
                            assertTrue(home.lookup("#module-" + id).isVisible());
                            assertTrue(home.lookup("#module-" + id).isManaged());
                        }
                        home.showHiddenModules(false);
                        assertFalse(home.lookup("#module-registro").isManaged());
                        result.complete(null);
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                });
        result.get(15, TimeUnit.SECONDS);
    }
}

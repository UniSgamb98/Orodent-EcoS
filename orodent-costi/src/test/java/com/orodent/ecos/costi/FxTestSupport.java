package com.orodent.ecos.costi;

import javafx.application.Platform;

import java.util.concurrent.*;

/** One toolkit per Surefire fork, so independent UI sessions can be exercised in the same suite. */
final class FxTestSupport {
    private static CompletableFuture<Void> started;

    static synchronized void start() throws Exception {
        if (started == null) {
            started = new CompletableFuture<>();
            Platform.startup(
                    () -> {
                        Platform.setImplicitExit(false);
                        started.complete(null);
                    });
        }
        started.get(15, TimeUnit.SECONDS);
    }
}

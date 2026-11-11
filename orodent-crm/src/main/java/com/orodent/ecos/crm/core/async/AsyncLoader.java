package com.orodent.ecos.crm.core.async;

import com.orodent.ecos.crm.core.database.Database;

import javafx.application.Platform;

import java.util.concurrent.*;
import java.util.function.*;

/** Serial operations on borrowed EcoS workers; UI callbacks expire when the page closes. */
public final class AsyncLoader implements AutoCloseable {
    private final Database database;
    private final Executor workers;
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    private int pending;
    private boolean closed, uncertain;

    public boolean requiresReload() {
        return uncertain;
    }

    private Runnable changed = () -> {}, clean = () -> {};
    private Runnable cleanup = () -> {};

    public void onClose(Runnable action) {
        cleanup = action;
    }

    public void onClean(Runnable listener) {
        clean = listener;
    }

    public void markClean() {
        clean.run();
    }

    public AsyncLoader(Database database, Executor workers) {
        this.database = database;
        this.workers = workers;
    }

    public void onChanged(Runnable listener) {
        changed = listener;
    }

    public boolean busy() {
        return pending > 0;
    }

    public <T> void run(Supplier<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        if (closed) return;
        if (uncertain) {
            failure.accept(
                    new IllegalStateException("Ricarica e verifica i dati prima di riprovare."));
            return;
        }
        pending++;
        changed.run();
        var operation =
                tail.handle((v, e) -> null)
                        .thenApplyAsync(v -> database.transaction(work), workers);
        tail = operation.handle((v, e) -> null);
        operation.whenComplete(
                (value, error) ->
                        Platform.runLater(
                                () -> {
                                    pending--;
                                    if (closed) return;
                                    if (error != null && CrmErrors.uncertain(error))
                                        uncertain = true;
                                    try {
                                        if (error == null) success.accept(value);
                                        else
                                            failure.accept(
                                                    error instanceof CompletionException
                                                                    && error.getCause() != null
                                                            ? error.getCause()
                                                            : error);
                                    } finally {
                                        changed.run();
                                    }
                                }));
    }

    @Override
    public void close() {
        closed = true;
        cleanup.run();
        changed = () -> {};
        clean = () -> {};
    }
}

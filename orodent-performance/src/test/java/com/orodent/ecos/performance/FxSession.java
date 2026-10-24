package com.orodent.ecos.performance;

import com.orodent.ecos.app.MainApp;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/** Drives the actual MainApp, with all controls accessed on the JavaFX thread. */
final class FxSession implements AutoCloseable {
    private final int timeout;
    private final AtomicReference<Throwable> asynchronousFailure = new AtomicReference<>();
    private MainApp application;
    private Stage stage;
    private volatile boolean toolkitStarted;

    FxSession(int timeout) { this.timeout = timeout; }

    void start() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> {
            toolkitStarted = true;
            Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> asynchronousFailure.compareAndSet(null, failure));
            Platform.setImplicitExit(false);
            application = new MainApp();
            stage = new Stage();
            try { application.start(stage); started.complete(null); }
            catch (Throwable failure) { started.completeExceptionally(failure); }
        });
        started.get(timeout, TimeUnit.SECONDS);
        await(() -> {
            if (!"Orodent-EcoS - Home".equals(stage.getTitle())) return false;
            Label status = (Label) stage.getScene().lookup("#database-status");
            HeadlessScenario.check(status != null && status.getText().contains("HOST"), "La prova deve usare il proprio HOST generato");
            return true;
        });
        pulse();
    }

    void button(String id) throws Exception {
        fx(() -> {
            fireButton(id);
            return null;
        });
    }

    boolean buttonWhile(String id, BooleanSupplier backgroundPending) throws Exception {
        return fx(() -> {
            boolean pending = backgroundPending.getAsBoolean();
            fireButton(id);
            return pending;
        });
    }

    private void fireButton(String id) {
        Button button = (Button) stage.getScene().lookup("#" + id);
        HeadlessScenario.check(button != null && !button.isDisabled(), "Pulsante non disponibile: " + id);
        button.fire();
    }

    void menu(String id) throws Exception {
        fx(() -> {
            stage.getScene().getRoot().lookupAll(".menu-button").stream()
                    .filter(MenuButton.class::isInstance).map(MenuButton.class::cast)
                    .flatMap(menu -> menu.getItems().stream()).flatMap(FxSession::items)
                    .filter(item -> id.equals(item.getId())).findFirst().orElseThrow().fire();
            return null;
        });
    }

    private static Stream<MenuItem> items(MenuItem item) {
        return item instanceof Menu menu ? Stream.concat(Stream.of(item), menu.getItems().stream().flatMap(FxSession::items)) : Stream.of(item);
    }

    void ready(String id) throws Exception {
        await(() -> {
            Node node = stage.getScene().lookup("#" + id);
            return node instanceof Parent page && page.lookupAll(".progress-indicator").stream().noneMatch(FxSession::visible);
        });
        pulse();
    }

    WeakReference<Parent> leaveModule() throws Exception {
        return fx(() -> {
            Parent root = (Parent) stage.getScene().lookup("#statistiche-module");
            HeadlessScenario.check(root != null, "Modulo non aperto");
            WeakReference<Parent> discarded = new WeakReference<>(root);
            ((Button) root.lookup("#ecosystem-home-button")).fire();
            return discarded;
        });
    }

    private void await(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (fx(ready::getAsBoolean)) return;
            Thread.sleep(50);
        }
        throw new IllegalStateException("Timeout della schermata: " + fx(() -> stage.getTitle()));
    }

    void pulse() throws Exception {
        CompletableFuture<Void> painted = new CompletableFuture<>();
        AnimationTimer timer = fx(() -> new AnimationTimer() {
            private int frames;
            @Override public void handle(long now) {
                if (++frames == 2) { stop(); painted.complete(null); }
            }
        });
        fx(() -> { timer.start(); return null; });
        try { painted.get(timeout, TimeUnit.SECONDS); }
        finally { fx(() -> { timer.stop(); return null; }); }
    }

    private <T> T fx(Callable<T> operation) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                Throwable failure = asynchronousFailure.get();
                if (failure != null) throw new IllegalStateException("Errore asincrono JavaFX", failure);
                if (stage != null && stage.getScene() != null) {
                    Parent root = stage.getScene().getRoot();
                    root.applyCss();
                    for (String selector : List.of(".error-label", ".result-error")) {
                        for (Node node : root.lookupAll(selector)) {
                            if (visible(node)) throw new IllegalStateException("Errore nella schermata: " + ((Label) node).getText());
                        }
                    }
                    for (Node node : root.lookupAll(".button")) {
                        if (node instanceof Button button && visible(button) && button.getText().equals("Riprova")) {
                            throw new IllegalStateException("La schermata richiede un nuovo tentativo");
                        }
                    }
                }
                result.complete(operation.call());
            } catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(timeout, TimeUnit.SECONDS);
    }

    static boolean visible(Node node) {
        for (Node current = node; current != null; current = current.getParent()) if (!current.isVisible()) return false;
        return true;
    }

    @Override public void close() throws Exception {
        if (!toolkitStarted) return;
        // Cleanup must run even when the screen or an asynchronous callback failed.
        CompletableFuture<Void> stopped = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                if (application != null) application.stop();
                if (stage != null) stage.close();
                stopped.complete(null);
            } catch (Throwable failure) { stopped.completeExceptionally(failure); }
        });
        try { stopped.get(timeout, TimeUnit.SECONDS); }
        finally { Platform.exit(); }
    }
}

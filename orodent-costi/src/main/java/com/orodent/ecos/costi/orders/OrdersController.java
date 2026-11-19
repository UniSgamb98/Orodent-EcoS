package com.orodent.ecos.costi.orders;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.nio.charset.Charset;
import java.util.concurrent.*;

public final class OrdersController {
    private final OrdersView view;
    private final OrderService service;
    private boolean busy, disposed;
    private long epoch;

    private javafx.stage.Window window;
    private final javafx.event.EventHandler<javafx.stage.WindowEvent> closing =
            e -> {
                if (!canLeave()) e.consume();
            };
    private final javafx.beans.value.ChangeListener<javafx.stage.Window> windowChanged =
            (o, b, a) -> attachWindow(a);
    private final javafx.beans.value.ChangeListener<javafx.scene.Scene> sceneChanged =
            (o, b, a) -> attachScene(b, a);

    private void attachScene(javafx.scene.Scene before, javafx.scene.Scene after) {
        if (before != null) before.windowProperty().removeListener(windowChanged);
        if (after != null) after.windowProperty().addListener(windowChanged);
        attachWindow(after == null ? null : after.getWindow());
    }

    private void attachWindow(javafx.stage.Window next) {
        if (window != null)
            window.removeEventFilter(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, closing);
        window = next;
        if (window != null)
            window.addEventFilter(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, closing);
    }

    public OrdersController(OrdersView view, OrderService service, Runnable home) {
        this.view = view;
        this.service = service;
        view.home.setOnAction(
                e -> {
                    if (canLeave()) home.run();
                });
        view.refresh.setOnAction(
                e -> {
                    if (canLeave()) {
                        view.clearPreview();
                        start();
                    }
                });
        view.cancel.setOnAction(e -> view.clearPreview());
        view.importCsv.setOnAction(e -> choose());
        view.apply.setOnAction(
                e -> {
                    var pending = view.pending();
                    if (pending == null || busy) return;
                    run(
                            service.apply(pending, view.mappings()),
                            report -> {
                                view.clearPreview();
                                view.feedback.setText(
                                        "Importazione completata: "
                                                + report.created()
                                                + " nuove, "
                                                + report.updated()
                                                + " aggiornate, "
                                                + report.unchanged()
                                                + " invariate. Magazzino invariato.");
                                loadCatalogue();
                            });
                });
        view.sceneProperty().addListener(sceneChanged);
        attachScene(null, view.getScene());
    }

    public void start() {
        loadCatalogue();
    }

    private void loadCatalogue() {
        run(service.list(), view::catalogue);
    }

    private void choose() {
        if (!canLeave()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importa ordini polveri");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv", "*.CSV"));
        var file =
                chooser.showOpenDialog(
                        view.getScene() == null ? null : view.getScene().getWindow());
        if (file != null)
            run(
                    service.preview(file.toPath(), Charset.forName(view.encoding.getValue())),
                    view::preview);
    }

    private <T> void run(CompletableFuture<T> future, java.util.function.Consumer<T> success) {
        busy = true;
        view.busy(true);
        long intent = ++epoch;
        future.whenComplete(
                (result, error) ->
                        Platform.runLater(
                                () -> {
                                    if (disposed || intent != epoch) return;
                                    busy = false;
                                    view.busy(false);
                                    if (error != null) {
                                        Throwable cause = error;
                                        while (cause instanceof CompletionException
                                                && cause.getCause() != null)
                                            cause = cause.getCause();
                                        view.feedback.setText(cause.getMessage());
                                    } else success.accept(result);
                                }));
    }

    public boolean canLeave() {
        if (busy) return false;
        if (view.pending() == null) return true;
        Alert alert =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Scartare l'anteprima non importata?",
                        ButtonType.CANCEL,
                        ButtonType.OK);
        if (view.getScene() != null) alert.initOwner(view.getScene().getWindow());
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    public void dispose() {
        disposed = true;
        attachScene(view.getScene(), null);
        view.sceneProperty().removeListener(sceneChanged);
        epoch++;
    }
}

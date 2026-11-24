package com.orodent.ecos.ton.features.firing.controller;

import com.orodent.ecos.ton.features.firing.service.FiringService;
import com.orodent.ecos.ton.features.firing.view.FiringView;
import com.orodent.ecos.ton.navigation.FeatureController;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.scene.control.*;

import java.util.concurrent.*;

public final class FiringController implements FeatureController {
    private final FiringView view;
    private final FiringService service;
    private final BooleanProperty saving = new SimpleBooleanProperty();
    private CompletableFuture<?> reading, details;
    private long request, detailRequest;
    private boolean disposed;

    public FiringController(FiringView view, FiringService service, Runnable back) {
        this.view = view;
        this.service = service;
        view.back().setOnAction(e -> back.run());
        view.reload().setOnAction(e -> refresh());
        view.create.setOnAction(e -> create());
        view.loads
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, a, b) -> {
                            long token = ++detailRequest;
                            if (details != null) details.cancel(false);
                            view.details(null, java.util.List.of());
                            if (b == null) return;
                            details =
                                    service.loaded(b.id())
                                            .whenComplete(
                                                    (discs, error) ->
                                                            Platform.runLater(
                                                                    () -> {
                                                                        if (disposed
                                                                                || token
                                                                                        != detailRequest)
                                                                            return;
                                                                        if (error != null)
                                                                            view.feedback(
                                                                                    message(error),
                                                                                    true);
                                                                        else view.details(b, discs);
                                                                    }));
                        });
    }

    public void start() {
        refresh();
    }

    private void refresh() {
        if (disposed || saving.get()) return;
        long token = ++request;
        if (reading != null) reading.cancel(false);
        view.busy(true);
        reading =
                service.workspace()
                        .whenComplete(
                                (data, error) ->
                                        Platform.runLater(
                                                () -> {
                                                    if (disposed || token != request) return;
                                                    view.busy(false);
                                                    if (error != null)
                                                        view.feedback(message(error), true);
                                                    else view.show(data);
                                                }));
    }

    private void create() {
        if (disposed || saving.get()) return;
        com.orodent.ecos.ton.features.firing.model.Firing.Request draft;
        try {
            draft = view.request();
        } catch (IllegalArgumentException invalid) {
            view.feedback(invalid.getMessage(), true);
            return;
        }
        saving.set(true);
        view.busy(true);
        request++;
        if (reading != null) reading.cancel(false);
        service.create(draft)
                .whenComplete(
                        (load, error) ->
                                Platform.runLater(
                                        () -> {
                                            saving.set(false);
                                            if (disposed) return;
                                            view.busy(false);
                                            if (error != null) {
                                                view.feedback(message(error), true);
                                                return;
                                            }
                                            view.resetAssignment();
                                            view.feedback(
                                                    load.number()
                                                            + " registrato · "
                                                            + load.count()
                                                            + " dischi · lotto "
                                                            + load.lot(),
                                                    false);
                                            refresh();
                                        }));
    }

    @Override
    public ReadOnlyBooleanProperty savingProperty() {
        return saving;
    }

    @Override
    public boolean canLeave() {
        if (saving.get()) return false;
        if (!view.dirty()) return true;
        var dialog =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Il carico non è stato registrato. Vuoi scartare la selezione e uscire?",
                        ButtonType.YES,
                        ButtonType.NO);
        dialog.setTitle("Carico non registrato");
        dialog.setHeaderText(null);
        if (view.getScene() != null) dialog.initOwner(view.getScene().getWindow());
        return dialog.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private String message(Throwable error) {
        while (error instanceof CompletionException || error instanceof ExecutionException)
            error = error.getCause();
        return error instanceof IllegalArgumentException
                ? error.getMessage()
                : "Impossibile completare l’operazione. Aggiorna i dati prima di riprovare.";
    }

    @Override
    public void dispose() {
        disposed = true;
        request++;
        detailRequest++;
        if (reading != null) reading.cancel(false);
        if (details != null) details.cancel(false);
        view.dispose();
    }
}

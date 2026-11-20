package com.orodent.ecos.ton.features.equipment.controller;

import static com.orodent.ecos.ton.features.equipment.model.Equipment.*;

import com.orodent.ecos.ton.features.equipment.service.EquipmentService;
import com.orodent.ecos.ton.features.equipment.view.EquipmentView;
import com.orodent.ecos.ton.navigation.FeatureController;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.*;

/** Reads can be abandoned; saves finish once and are never replayed automatically. */
public final class EquipmentController implements FeatureController {
    private final EquipmentView view;
    private final EquipmentService service;
    private final BooleanProperty saving = new SimpleBooleanProperty();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
    private final ChangeListener<String> query = (o, a, b) -> debounce.playFromStart();
    private CompletableFuture<?> reading;
    private long request;
    private Entry original;
    private List<String> baseline = List.of();
    private volatile boolean disposed;

    public EquipmentController(EquipmentView view, EquipmentService service) {
        this.view = view;
        this.service = service;
        debounce.setOnFinished(e -> loadList());
        view.listing.search().textProperty().addListener(query);
        view.listing.search().setOnAction(e -> loadList());
        view.listing.refresh().setOnAction(e -> loadList());
        view.archived.setOnAction(e -> loadList());
        view.listing
                .create()
                .setOnAction(
                        e -> {
                            if (saving.get()) return;
                            cancelRead();
                            original = null;
                            view.showEntry(null);
                            baseline = view.fingerprint();
                        });
        view.listing.onOpen(
                () -> {
                    var selected = view.listing.table().getSelectionModel().getSelectedItem();
                    if (selected != null) open(selected.id());
                });
        view.back.setOnAction(e -> back());
        view.cancel.setOnAction(e -> back());
        view.reload.setOnAction(
                e -> {
                    if (original != null && canLeave()) open(original.id());
                });
        view.save.setOnAction(e -> save());
    }

    public void start() {
        loadList();
    }

    private void cancelRead() {
        debounce.stop();
        request++;
        if (reading != null) reading.cancel(false);
        reading = null;
    }

    private void loadList() {
        if (disposed || saving.get() || view.editing()) return;
        cancelRead();
        long token = request;
        view.listing.loading();
        reading =
                service.list(view.kind, view.listing.search().getText(), view.archived.isSelected())
                        .whenComplete(
                                (rows, error) ->
                                        Platform.runLater(
                                                () -> {
                                                    if (disposed || request != token) return;
                                                    if (error != null)
                                                        view.listing.error(message(error));
                                                    else view.listing.showRows(rows);
                                                }));
    }

    private void open(long id) {
        if (disposed || saving.get()) return;
        cancelRead();
        long token = request;
        view.busy(true);
        reading =
                service.load(view.kind, id)
                        .whenComplete(
                                (row, error) ->
                                        Platform.runLater(
                                                () -> {
                                                    if (disposed || request != token) return;
                                                    view.busy(false);
                                                    if (error != null) {
                                                        if (view.editing())
                                                            view.feedback(message(error), true);
                                                        else view.listing.error(message(error));
                                                        return;
                                                    }
                                                    original = row;
                                                    view.showEntry(row);
                                                    baseline = view.fingerprint();
                                                }));
    }

    private void save() {
        if (disposed || saving.get() || !view.editing()) return;
        Draft draft;
        try {
            draft = view.draft();
            draft.validate(view.kind);
        } catch (IllegalArgumentException invalid) {
            view.feedback(invalid.getMessage(), true);
            return;
        }
        cancelRead();
        saving.set(true);
        view.busy(true);
        service.save(view.kind, original, draft)
                .whenComplete(
                        (row, error) ->
                                Platform.runLater(
                                        () -> {
                                            saving.set(false);
                                            if (disposed) return;
                                            view.busy(false);
                                            if (error != null) {
                                                view.feedback(message(error), true);
                                                return;
                                            }
                                            original = row;
                                            view.showList();
                                            baseline = List.of();
                                            loadList();
                                        }));
    }

    private void back() {
        if (canLeave()) {
            cancelRead();
            view.showList();
            baseline = List.of();
            loadList();
        }
    }

    @Override
    public ReadOnlyBooleanProperty savingProperty() {
        return saving;
    }

    @Override
    public boolean canLeave() {
        if (saving.get()) return false;
        if (!view.editing() || baseline.equals(view.fingerprint())) return true;
        Alert confirm =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Sono presenti modifiche non salvate. Vuoi scartarle?",
                        ButtonType.YES,
                        ButtonType.NO);
        confirm.setTitle("Modifiche non salvate");
        confirm.setHeaderText("Uscire dall’anagrafica?");
        if (view.getScene() != null) confirm.initOwner(view.getScene().getWindow());
        return confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private String message(Throwable error) {
        while (error instanceof CompletionException || error instanceof ExecutionException)
            error = error.getCause();
        return error instanceof IllegalArgumentException || error instanceof Conflict
                ? error.getMessage()
                : "Impossibile completare l’operazione. Riprova con Aggiorna dati.";
    }

    @Override
    public void dispose() {
        disposed = true;
        cancelRead();
        debounce.setOnFinished(null);
        view.listing.search().textProperty().removeListener(query);
        view.listing.search().setOnAction(null);
        view.listing.refresh().setOnAction(null);
        view.archived.setOnAction(null);
        view.listing.create().setOnAction(null);
        for (var b : List.of(view.back, view.reload, view.cancel, view.save)) b.setOnAction(null);
        view.dispose();
    }
}

package com.orodent.ecos.ton.features.firingprograms.controller;

import com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms.*;
import com.orodent.ecos.ton.features.firingprograms.service.FiringProgramService;
import com.orodent.ecos.ton.features.firingprograms.view.FiringProgramsView;
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
public final class FiringProgramsController implements FeatureController {
    private final FiringProgramsView view;
    private final FiringProgramService service;
    private final BooleanProperty saving = new SimpleBooleanProperty();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
    private final ChangeListener<String> query = (o, a, b) -> debounce.playFromStart();
    private CompletableFuture<?> reading;
    private long request;
    private Entry original;
    private Snapshot selected;
    private List<String> baseline = List.of();
    private volatile boolean disposed;

    public FiringProgramsController(FiringProgramsView view, FiringProgramService service) {
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
                            selected = null;
                            view.showDetail(null);
                            baseline = view.fingerprint();
                        });
        view.listing.onOpen(
                () -> {
                    var selected = view.listing.table().getSelectionModel().getSelectedItem();
                    if (selected != null) open(selected.id(), null);
                });
        view.back.setOnAction(e -> back());
        view.cancel.setOnAction(e -> back());
        view.reload.setOnAction(
                e -> {
                    if (original != null && canLeave())
                        open(original.id(), selected == null ? null : selected.id());
                });
        view.save.setOnAction(e -> save(false));
        view.approve.setOnAction(e -> save(true));
        view.copy.setOnAction(e -> copy());
        view.versions
                .refresh()
                .setOnAction(
                        e -> {
                            if (original != null && canLeave())
                                open(original.id(), selected == null ? null : selected.id());
                        });
        view.versions.onOpen(
                () -> {
                    var version = view.versions.table().getSelectionModel().getSelectedItem();
                    if (version != null && canLeave()) open(version.familyId(), version.id());
                });
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
                service.list(view.listing.search().getText(), view.archived.isSelected())
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

    private void open(long id, Long version) {
        if (disposed || saving.get()) return;
        cancelRead();
        long token = request;
        view.busy(true);
        reading =
                service.load(id, version)
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
                                                    original = row.entry();
                                                    selected = row.selected();
                                                    view.showDetail(row);
                                                    baseline = view.fingerprint();
                                                }));
    }

    private void save(boolean approve) {
        if (disposed || saving.get() || !view.editing()) return;
        Draft draft;
        try {
            draft = view.draft();
        } catch (IllegalArgumentException invalid) {
            view.feedback(invalid.getMessage(), true);
            return;
        }
        cancelRead();
        saving.set(true);
        view.busy(true);
        service.save(original, selected, draft, approve)
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
                                            original = row.entry();
                                            selected = row.selected();
                                            view.showDetail(row);
                                            baseline = view.fingerprint();
                                        }));
    }

    private void copy() {
        if (original == null || selected == null || !canLeave()) return;
        cancelRead();
        saving.set(true);
        view.busy(true);
        service.copy(original, selected)
                .whenComplete(
                        (detail, error) ->
                                Platform.runLater(
                                        () -> {
                                            saving.set(false);
                                            if (disposed) return;
                                            view.busy(false);
                                            if (error != null) {
                                                view.feedback(message(error), true);
                                                return;
                                            }
                                            original = detail.entry();
                                            selected = detail.selected();
                                            view.showDetail(detail);
                                            baseline = view.fingerprint();
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
        confirm.setHeaderText("Uscire dal programma di cottura?");
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
        for (var b :
                List.of(view.back, view.reload, view.cancel, view.save, view.approve, view.copy))
            b.setOnAction(null);
        view.dispose();
    }
}

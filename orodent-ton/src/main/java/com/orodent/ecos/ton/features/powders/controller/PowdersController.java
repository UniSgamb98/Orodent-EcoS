package com.orodent.ecos.ton.features.powders.controller;

import com.orodent.ecos.ton.features.powders.model.PowderModel.*;
import com.orodent.ecos.ton.features.powders.service.PowderService;
import com.orodent.ecos.ton.features.powders.view.PowdersView;
import com.orodent.ecos.ton.navigation.FeatureController;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Owns read cancellation and stale-result guards; a write is allowed to finish and is never
 * repeated.
 */
public final class PowdersController implements FeatureController {
    private final PowdersView view;
    private final PowderService service;
    private final SimpleBooleanProperty saving = new SimpleBooleanProperty();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
    private final ChangeListener<String> searchListener;
    private final ChangeListener<Row> selectionListener;
    private CompletableFuture<Page> listRead;
    private CompletableFuture<Detail> detailRead;
    private Detail original;
    private Revision selected;
    private List<String> fingerprint = List.of();
    private boolean editing;
    private volatile boolean disposed;
    private int offset;
    private long selectedPowder;

    public PowdersController(PowdersView view, PowderService service) {
        this.view = view;
        this.service = service;
        searchListener = (observable, before, after) -> debounce.playFromStart();
        selectionListener =
                (observable, before, after) -> view.list().open().setDisable(after == null);
        view.list().search().textProperty().addListener(searchListener);
        view.list()
                .table()
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(selectionListener);
        debounce.setOnFinished(event -> reloadList(0));
        view.list().search().setOnAction(event -> reloadList(0));
        view.list().inactive().setOnAction(event -> reloadList(0));
        view.list().refresh().setOnAction(event -> reloadList(offset));
        view.list()
                .previous()
                .setOnAction(event -> reloadList(Math.max(0, offset - PowderService.PAGE_SIZE)));
        view.list().next().setOnAction(event -> reloadList(offset + PowderService.PAGE_SIZE));
        view.list().open().setOnAction(event -> openSelected());
        view.list()
                .table()
                .setOnMouseClicked(
                        event -> {
                            if (event.getClickCount() == 2) openSelected();
                        });
        view.list()
                .table()
                .setOnKeyPressed(
                        event -> {
                            if (event.getCode() == KeyCode.ENTER) {
                                openSelected();
                                event.consume();
                            }
                        });
        view.list()
                .create()
                .setOnAction(
                        event -> {
                            if (!canLeave()) return;
                            cancelReads();
                            original = null;
                            selected = null;
                            editing = true;
                            view.editor().newEditor();
                            fingerprint = view.editor().fingerprint();
                            view.showEditor();
                        });
        var editor = view.editor();
        editor.back()
                .setOnAction(
                        event -> {
                            if (canLeave()) {
                                cancelDetail();
                                editing = false;
                                view.showList();
                                reloadList(offset);
                            }
                        });
        editor.reload()
                .setOnAction(
                        event -> {
                            if (original != null && canLeave())
                                load(original.powder().id(), selected == null ? 0 : selected.id());
                        });
        editor.cancel()
                .setOnAction(
                        event -> {
                            if (canLeave()) {
                                if (original == null) {
                                    editing = false;
                                    view.showList();
                                } else render(original, selected.id());
                            }
                        });
        editor.save().setOnAction(event -> save(false));
        editor.approve().setOnAction(event -> save(true));
        editor.identitySave()
                .setOnAction(
                        event -> {
                            try {
                                var identity = editor.identity();
                                write(
                                        () -> service.saveIdentity(original, identity),
                                        selected.id(),
                                        "Anagrafica salvata.");
                            } catch (RuntimeException invalid) {
                                editor.feedback(message(invalid), true);
                            }
                        });
        editor.newVersion()
                .setOnAction(
                        event -> {
                            if (canLeave())
                                write(
                                        () -> service.newVersion(original, selected),
                                        0,
                                        "Nuova bozza creata: puoi modificarne caratteristiche e"
                                            + " ossidi.");
                        });
        editor.retire()
                .setOnAction(
                        event -> {
                            if (canLeave()
                                    && confirm(
                                            "Ritira versione",
                                            "La versione non sarà più proposta per nuove ricette. I"
                                                + " dati e i collegamenti esistenti rimangono"
                                                + " conservati."))
                                write(
                                        () -> service.retire(original, selected),
                                        selected.id(),
                                        "Versione ritirata.");
                        });
        editor.versions()
                .setOnAction(
                        event -> {
                            if (editor.updating()
                                    || !editing
                                    || editor.versions().getValue() == null
                                    || editor.versions().getValue().equals(selected)) return;
                            Revision next = editor.versions().getValue();
                            if (canLeave()) render(original, next.id());
                            else editor.selectVersion(selected);
                        });
    }

    public void loadInitialData() {
        reloadList(0);
    }

    @Override
    public ReadOnlyBooleanProperty savingProperty() {
        return saving;
    }

    @Override
    public boolean canLeave() {
        if (saving.get()) return false;
        return !editing
                || fingerprint.equals(view.editor().fingerprint())
                || confirm(
                        "Modifiche alla polvere", "Ci sono modifiche non salvate. Vuoi scartarle?");
    }

    private boolean confirm(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(title);
        if (view.getScene() != null) alert.initOwner(view.getScene().getWindow());
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void reloadList(int nextOffset) {
        if (disposed || editing) return;
        debounce.stop();
        if (listRead != null) listRead.cancel(false);
        offset = nextOffset;
        view.list().showLoading();
        CompletableFuture<Page> future =
                service.findPage(
                        view.list().search().getText(),
                        view.list().inactive().isSelected(),
                        offset);
        listRead = future;
        future.whenComplete(
                (page, error) -> {
                    if (disposed) return;
                    Platform.runLater(
                            () -> {
                                if (disposed || listRead != future || editing) return;
                                listRead = null;
                                if (error != null) {
                                    view.list().showError(message(error));
                                    return;
                                }
                                if (page.rows().isEmpty() && page.total() > 0 && offset > 0) {
                                    reloadList(
                                            (int) ((page.total() - 1) / PowderService.PAGE_SIZE)
                                                    * PowderService.PAGE_SIZE);
                                    return;
                                }
                                view.list().showPage(page);
                                page.rows().stream()
                                        .filter(row -> row.powder().id() == selectedPowder)
                                        .findFirst()
                                        .ifPresent(
                                                row -> {
                                                    view.list()
                                                            .table()
                                                            .getSelectionModel()
                                                            .select(row);
                                                    view.list().table().scrollTo(row);
                                                });
                            });
                });
    }

    private void openSelected() {
        Row row = view.list().table().getSelectionModel().getSelectedItem();
        if (row != null && !saving.get()) load(row.powder().id(), 0);
    }

    private void load(long id, long version) {
        selectedPowder = id;
        cancelReads();
        editing = false;
        view.loading();
        CompletableFuture<Detail> future = service.load(id);
        detailRead = future;
        future.whenComplete(
                (detail, error) -> {
                    if (disposed) return;
                    Platform.runLater(
                            () -> {
                                if (disposed || detailRead != future) return;
                                detailRead = null;
                                if (error != null) {
                                    view.showList();
                                    view.list().showError(message(error));
                                    return;
                                }
                                render(detail, version);
                            });
                });
    }

    private void render(Detail detail, long version) {
        selectedPowder = detail.powder().id();
        original = detail;
        selected =
                version == 0
                        ? detail.latest()
                        : detail.revisions().stream()
                                .filter(r -> r.id() == version)
                                .findFirst()
                                .orElse(detail.latest());
        editing = true;
        view.editor().show(detail, selected);
        fingerprint = view.editor().fingerprint();
        view.showEditor();
    }

    private void save(boolean approve) {
        if (saving.get()) return;
        try {
            var identity = view.editor().identity();
            var technical = view.editor().technical();
            if (approve) {
                technical.requireApprovable();
                if (!confirm(
                        "Approva scheda tecnica",
                        "La scheda approvata diventerà di sola lettura. Per modificare"
                            + " caratteristiche e ossidi creerai una nuova versione."))
                    return;
            }
            long version = selected == null ? 0 : selected.id();
            write(
                    () ->
                            original == null
                                    ? service.create(identity, technical, approve)
                                    : service.save(
                                            original, selected, identity, technical, approve),
                    version,
                    approve
                            ? "Scheda approvata. Caratteristiche e ossidi sono ora in sola lettura."
                            : "Bozza salvata.");
        } catch (RuntimeException invalid) {
            view.editor().feedback(message(invalid), true);
        }
    }

    private void write(
            Supplier<CompletableFuture<Detail>> operation, long version, String success) {
        if (disposed || saving.get()) return;
        saving.set(true);
        view.editor().saving(true);
        CompletableFuture<Detail> command;
        try {
            command = operation.get();
        } catch (RuntimeException failure) {
            saving.set(false);
            view.editor().saving(false);
            view.editor().feedback(message(failure), true);
            return;
        }
        command.whenComplete(
                (detail, error) -> {
                    if (disposed) {
                        if (error != null)
                            System.getLogger(getClass().getName())
                                    .log(
                                            System.Logger.Level.WARNING,
                                            "Salvataggio polvere terminato con errore dopo la"
                                                + " chiusura della pagina",
                                            error);
                        return;
                    }
                    Platform.runLater(
                            () -> {
                                if (disposed) return;
                                saving.set(false);
                                view.editor().saving(false);
                                if (error != null) {
                                    view.editor().feedback(message(error), true);
                                    return;
                                }
                                render(detail, version);
                                view.editor().feedback(success, false);
                            });
                });
    }

    private void cancelDetail() {
        if (detailRead != null) detailRead.cancel(false);
        detailRead = null;
    }

    private void cancelReads() {
        debounce.stop();
        if (listRead != null) listRead.cancel(false);
        listRead = null;
        cancelDetail();
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        return error.getMessage() == null
                ? "Operazione non riuscita. Verifica la connessione e riprova."
                : error.getMessage();
    }

    @Override
    public void dispose() {
        disposed = true;
        cancelReads();
        debounce.setOnFinished(null);
        view.list().search().textProperty().removeListener(searchListener);
        view.list()
                .table()
                .getSelectionModel()
                .selectedItemProperty()
                .removeListener(selectionListener);
        view.list().search().setOnAction(null);
        view.list().inactive().setOnAction(null);
        view.list().table().setOnMouseClicked(null);
        view.list().table().setOnKeyPressed(null);
        for (var button :
                List.of(
                        view.list().open(),
                        view.list().create(),
                        view.list().refresh(),
                        view.list().previous(),
                        view.list().next(),
                        view.editor().back(),
                        view.editor().reload(),
                        view.editor().cancel(),
                        view.editor().save(),
                        view.editor().approve(),
                        view.editor().identitySave(),
                        view.editor().newVersion(),
                        view.editor().retire())) button.setOnAction(null);
        view.editor().versions().setOnAction(null);
        view.dispose();
    }
}

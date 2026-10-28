package com.orodent.ecos.ton.features.diskmodels.controller;

import static com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import com.orodent.ecos.ton.features.diskmodels.service.DiskModelService;
import com.orodent.ecos.ton.features.diskmodels.view.*;
import com.orodent.ecos.ton.features.diskmodels.view.DiskModelEditorView.Sheet;
import com.orodent.ecos.ton.navigation.FeatureController;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Owns pending reads/listeners. A write finishes once; late UI callbacks are discarded. */
public final class DiskModelsController implements FeatureController {
    private final DiskModelsView view;
    private final DiskModelService service;
    private final LongConsumer openArticle;
    private final SimpleBooleanProperty saving = new SimpleBooleanProperty();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
    private final Map<String, CompletableFuture<?>> reads = new HashMap<>();
    private final List<Runnable> cleanups = new ArrayList<>();
    private Detail original;
    private List<String> baseline = List.of();
    private int offset, versionsOffset, usagesOffset;
    private long selectedId;
    private boolean editing;
    private volatile boolean disposed;

    public DiskModelsController(
            DiskModelsView view, DiskModelService service, LongConsumer openArticle) {
        this.view = view;
        this.service = service;
        this.openArticle = openArticle;
        var list = view.list();
        var e = view.editor();
        listen(list.search().textProperty(), (o, a, b) -> debounce.playFromStart());
        debounce.setOnFinished(event -> loadList(0));
        list.search().setOnAction(event -> loadList(0));
        list.filter().setOnAction(event -> loadList(0));
        on(list.create(), this::create);
        on(list.results().refresh(), () -> loadList(offset));
        on(
                list.results().previous(),
                () -> loadList(Math.max(0, offset - DiskModelService.PAGE_SIZE)));
        on(list.results().next(), () -> loadList(offset + DiskModelService.PAGE_SIZE));
        on(list.results().open(), this::openSelected);
        tableAction(list.results().table(), this::openSelected);
        on(
                e.back(),
                () -> {
                    if (canLeave()) {
                        cancelReads();
                        editing = false;
                        view.showList();
                        loadList(offset);
                    }
                });
        on(
                e.reload(),
                () -> {
                    if (original != null && canLeave()) load(original.revision().id(), e.sheet());
                });
        on(
                e.cancel(),
                () -> {
                    if (canLeave()) {
                        if (original == null) {
                            editing = false;
                            view.showList();
                            loadList(offset);
                        } else render(original, e.sheet());
                    }
                });
        on(e.save(), () -> save(false));
        on(e.approve(), () -> save(true));
        on(
                e.copy(),
                () -> {
                    if (original != null && canLeave())
                        write(
                                () -> service.copy(original.revision()),
                                "Nuova bozza creata. Le specifiche esistenti conservano la versione"
                                        + " precedente.",
                                Sheet.LAYERS);
                });
        on(
                e.retire(),
                () -> {
                    if (original != null
                            && canLeave()
                            && confirm(
                                    "Ritira versione",
                                    "La versione sarà esclusa dalle nuove selezioni. I contenuti e"
                                            + " i collegamenti esistenti vengono conservati."))
                        write(
                                () -> service.retire(original.revision()),
                                "Versione ritirata.",
                                e.sheet());
                });
        on(
                e.delete(),
                () -> {
                    if (original != null
                            && canLeave()
                            && confirm(
                                    "Elimina bozza",
                                    "Eliminare definitivamente questa bozza mai utilizzata? I"
                                            + " collegamenti vengono ricontrollati nel database."))
                        write(
                                () ->
                                        service.delete(original.revision())
                                                .thenApply(ignored -> null),
                                "Bozza eliminata.",
                                Sheet.PARAMETERS);
                });
        on(
                e.layers().distributeButton(),
                () -> {
                    if (confirm(
                            "Distribuisci gli strati",
                            "Sostituire le percentuali correnti con una distribuzione uniforme al"
                                    + " 100%?")) e.layers().distribute();
                });
        e.versions()
                .setOnAction(
                        event -> {
                            if (e.updating()
                                    || !editing
                                    || e.versions().getValue() == null
                                    || original == null) return;
                            Version next = e.versions().getValue();
                            if (next.id() == original.revision().id()) return;
                            if (canLeave()) load(next.id(), e.sheet());
                            else
                                e.selectVersion(
                                        new Version(
                                                original.revision().id(),
                                                original.revision().number(),
                                                original.revision().state(),
                                                original.revision().updated()));
                        });
        listen(
                e.tabs().getSelectionModel().selectedItemProperty(),
                (o, a, b) -> {
                    if (!e.updating() && editing && original != null) loadSheet();
                });
        on(e.history().refresh(), () -> loadVersions(versionsOffset));
        on(
                e.history().previous(),
                () -> loadVersions(Math.max(0, versionsOffset - DiskModelService.PAGE_SIZE)));
        on(e.history().next(), () -> loadVersions(versionsOffset + DiskModelService.PAGE_SIZE));
        on(e.history().open(), this::openVersion);
        tableAction(e.history().table(), this::openVersion);
        on(e.usages().refresh(), () -> loadUsages(usagesOffset));
        on(
                e.usages().previous(),
                () -> loadUsages(Math.max(0, usagesOffset - DiskModelService.PAGE_SIZE)));
        on(e.usages().next(), () -> loadUsages(usagesOffset + DiskModelService.PAGE_SIZE));
        on(e.usages().open(), this::openUsage);
        tableAction(e.usages().table(), this::openUsage);
    }

    public void start() {
        loadList(0);
    }

    @Override
    public ReadOnlyBooleanProperty savingProperty() {
        return saving;
    }

    @Override
    public boolean canLeave() {
        return !saving.get()
                && (!editing
                        || baseline.equals(view.editor().fingerprint())
                        || confirm(
                                "Modifiche al modello",
                                "Ci sono modifiche non salvate. Vuoi scartarle?"));
    }

    private boolean confirm(String title, String message) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(title);
        if (view.getScene() != null) alert.initOwner(view.getScene().getWindow());
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void create() {
        if (!canLeave()) return;
        cancelReads();
        original = null;
        editing = true;
        versionsOffset = usagesOffset = 0;
        view.editor().show(null);
        view.editor().sheet(Sheet.PARAMETERS);
        baseline = view.editor().fingerprint();
        view.showEditor();
    }

    private void openSelected() {
        Summary row = view.list().results().table().getSelectionModel().getSelectedItem();
        if (row != null && !saving.get()) load(row.id(), Sheet.PARAMETERS);
    }

    private void loadList(int nextOffset) {
        if (disposed || editing || saving.get()) return;
        debounce.stop();
        offset = nextOffset;
        var list = view.list();
        list.results().loading();
        read(
                "list",
                () -> service.page(list.search().getText(), list.filter().getValue(), offset),
                page -> {
                    if (editing) return;
                    if (page.rows().isEmpty() && page.total() > 0 && offset > 0) {
                        loadList(
                                (int) ((page.total() - 1) / DiskModelService.PAGE_SIZE)
                                        * DiskModelService.PAGE_SIZE);
                        return;
                    }
                    list.results().show(page);
                    page.rows().stream()
                            .filter(r -> r.id() == selectedId)
                            .findFirst()
                            .ifPresent(r -> list.results().table().getSelectionModel().select(r));
                },
                list.results()::error);
    }

    private void load(long id, Sheet sheet) {
        cancelReads();
        editing = false;
        view.loading();
        read(
                "detail",
                () -> service.load(id),
                detail -> render(detail, sheet),
                error -> {
                    view.showList();
                    view.list().results().error(error);
                });
    }

    private void render(Detail detail, Sheet sheet) {
        cancelReads();
        original = detail;
        selectedId = detail.revision().id();
        editing = true;
        versionsOffset = usagesOffset = 0;
        view.editor().show(detail);
        view.editor().sheet(sheet);
        baseline = view.editor().fingerprint();
        view.showEditor();
        loadSheet();
    }

    private void loadSheet() {
        if (original == null || saving.get()) return;
        switch (view.editor().sheet()) {
            case VERSIONS -> loadVersions(versionsOffset);
            case USAGES -> loadUsages(usagesOffset);
            default -> {}
        }
    }

    private void loadVersions(int offset) {
        if (original == null || saving.get()) return;
        versionsOffset = offset;
        var page = view.editor().history();
        page.loading();
        read(
                "versions",
                () -> service.versions(original.revision().data().code(), offset),
                page::show,
                page::error);
    }

    private void loadUsages(int offset) {
        if (original == null || saving.get()) return;
        usagesOffset = offset;
        var page = view.editor().usages();
        page.loading();
        read(
                "usages",
                () -> service.usages(original.revision().id(), offset),
                page::show,
                page::error);
    }

    private void openVersion() {
        Version next = view.editor().history().table().getSelectionModel().getSelectedItem();
        if (next != null && canLeave()) load(next.id(), Sheet.PARAMETERS);
    }

    private void openUsage() {
        Usage next = view.editor().usages().table().getSelectionModel().getSelectedItem();
        if (next != null && !saving.get()) openArticle.accept(next.articleId());
    }

    private void save(boolean approve) {
        if (saving.get()) return;
        try {
            Draft draft = view.editor().draft();
            if (approve) {
                draft.requireApprovable();
                if (!confirm(
                        "Approva modello disco",
                        "La versione approvata diventerà di sola lettura. Per modificare parametri,"
                                + " strati o sovramateriali creerai una nuova versione.")) return;
            }
            write(
                    () ->
                            original == null
                                    ? service.create(draft, approve)
                                    : service.save(original.revision(), draft, approve),
                    approve
                            ? "Modello approvato. La versione è ora in sola lettura."
                            : "Bozza salvata.",
                    view.editor().sheet());
        } catch (RuntimeException invalid) {
            view.editor().feedback(message(invalid), true);
        }
    }

    private void write(Supplier<CompletableFuture<Detail>> command, String success, Sheet sheet) {
        if (disposed || saving.get()) return;
        cancelReads();
        saving.set(true);
        view.setDisable(true);
        CompletableFuture<Detail> future;
        try {
            future = command.get();
        } catch (RuntimeException failure) {
            saving.set(false);
            view.setDisable(false);
            view.editor().feedback(message(failure), true);
            return;
        }
        future.whenComplete(
                (detail, failure) -> {
                    if (disposed) {
                        if (failure != null)
                            System.getLogger(getClass().getName())
                                    .log(
                                            System.Logger.Level.WARNING,
                                            "Scrittura modello terminata con errore dopo la"
                                                    + " chiusura della pagina",
                                            failure);
                        return;
                    }
                    Platform.runLater(
                            () -> {
                                if (disposed) return;
                                saving.set(false);
                                view.setDisable(false);
                                if (failure != null) {
                                    view.editor().feedback(message(failure), true);
                                    return;
                                }
                                if (detail == null) {
                                    editing = false;
                                    original = null;
                                    view.showList();
                                    loadList(offset);
                                } else {
                                    render(detail, sheet);
                                    view.editor().feedback(success, false);
                                }
                            });
                });
    }

    private <T> void read(
            String slot,
            Supplier<CompletableFuture<T>> operation,
            Consumer<T> success,
            Consumer<String> failure) {
        CompletableFuture<?> old = reads.remove(slot);
        if (old != null) old.cancel(false);
        CompletableFuture<T> future;
        try {
            future = operation.get();
        } catch (RuntimeException error) {
            failure.accept(message(error));
            return;
        }
        reads.put(slot, future);
        future.whenComplete(
                (result, error) -> {
                    if (disposed) return;
                    Platform.runLater(
                            () -> {
                                if (disposed || reads.get(slot) != future) return;
                                reads.remove(slot);
                                if (error != null) failure.accept(message(error));
                                else success.accept(result);
                            });
                });
    }

    private void cancelReads() {
        debounce.stop();
        for (var future : reads.values()) future.cancel(false);
        reads.clear();
    }

    private <T> void listen(ObservableValue<T> property, ChangeListener<T> listener) {
        property.addListener(listener);
        cleanups.add(() -> property.removeListener(listener));
    }

    private void on(Button button, Runnable action) {
        button.setOnAction(event -> action.run());
        cleanups.add(() -> button.setOnAction(null));
    }

    private <T> void tableAction(TableView<T> table, Runnable action) {
        table.setOnMouseClicked(
                event -> {
                    if (event.getClickCount() == 2) action.run();
                });
        table.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ENTER) {
                        action.run();
                        event.consume();
                    }
                });
        cleanups.add(
                () -> {
                    table.setOnMouseClicked(null);
                    table.setOnKeyPressed(null);
                });
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
        cleanups.forEach(Runnable::run);
        cleanups.clear();
        view.list().search().setOnAction(null);
        view.list().filter().setOnAction(null);
        view.editor().versions().setOnAction(null);
        view.dispose();
    }
}

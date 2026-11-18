package com.orodent.ecos.ton.features.catalog.bulk.controller;

import static com.orodent.ecos.ton.features.catalog.bulk.model.BulkCatalogModels.MAX_ROWS;

import com.orodent.ecos.ton.features.catalog.bulk.model.BulkCatalogModels.*;
import com.orodent.ecos.ton.features.catalog.bulk.service.BulkCatalogService;
import com.orodent.ecos.ton.features.catalog.bulk.view.*;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.Navigation;
import com.orodent.ecos.ton.navigation.FeatureController;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Async reads expire on navigation; a write owns its transaction until completion. */
public final class BulkCatalogController implements FeatureController {
    private final BulkCatalogView view;
    private final BulkCatalogService service;
    private final Consumer<Navigation> navigate;
    private final SimpleBooleanProperty saving = new SimpleBooleanProperty();
    private final Map<Object, CompletableFuture<?>> reads = new HashMap<>();
    private final List<PauseTransition> timers = new ArrayList<>();
    private final Map<BulkWorkspace, PauseTransition> validation = new HashMap<>();
    private final Map<BulkWorkspace, PauseTransition> generation = new HashMap<>();
    private boolean disposed;
    private Navigation route;

    public BulkCatalogController(
            BulkCatalogView view, BulkCatalogService service, Consumer<Navigation> navigate) {
        this.view = view;
        this.service = service;
        this.navigate = navigate;
        for (BulkWorkspace w : view.workspaces()) {
            configurePicker(w.parents(), w);
            if (w.lineFilter() != null) {
                configurePicker(w.lineFilter(), w);
                w.lineFilter().onSelection(() -> changeLine(w));
                w.parents().selectAllButton().setOnAction(e -> selectAllProducts(w));
            }
            PauseTransition verify = timer();
            verify.setOnFinished(e -> verify(w));
            validation.put(w, verify);
            PauseTransition regenerate = new PauseTransition(Duration.millis(450));
            timers.add(regenerate);
            regenerate.setOnFinished(e -> generate(w));
            generation.put(w, regenerate);
            w.onInputs(() -> queueGeneration(w));
            w.onRows(
                    () -> {
                        if (!w.hasRows()) return;
                        w.pending();
                        validation.get(w).playFromStart();
                    });
            w.saveButton().setOnAction(e -> save(w));
            w.openListButton().setOnAction(e -> navigate.accept(Navigation.list(w.kind())));
        }
        view.modeProperty()
                .addListener(
                        (o, a, b) -> {
                            var w = view.workspace(b);
                            if (w.lineFilter() != null && w.lineFilter().id() == null) {
                                Parent line =
                                        view.workspace(CatalogKind.PRODUCTS).parents().value();
                                if (line != null) {
                                    w.lineFilter().select(line);
                                    changeLine(w);
                                }
                            }
                            loadParents(w.parents(), w, false);
                            if (w.lineFilter() != null) loadParents(w.lineFilter(), w, false);
                        });
        view.refresh().setOnAction(e -> refresh());
        view.back()
                .setOnAction(
                        e -> {
                            Navigation back =
                                    route.parentFilter() == null
                                            ? Navigation.list(route.kind())
                                            : Navigation.open(
                                                    route.kind().parent(), route.parentFilter());
                            navigate.accept(back);
                        });
    }

    public void start(Navigation route) {
        this.route = route;
        for (BulkWorkspace w : view.workspaces()) {
            loadParents(w.parents(), w, false);
            if (w.lineFilter() != null) loadParents(w.lineFilter(), w, false);
        }
        if (route.parentFilter() != null) {
            BulkWorkspace w = view.workspace(route.kind());
            view.setDisable(true);
            read(
                    "context",
                    () ->
                            service.parentsByIds(
                                            route.kind().parent(), List.of(route.parentFilter()))
                                    .thenCompose(
                                            parents ->
                                                    w.lineFilter() == null
                                                            ? CompletableFuture.completedFuture(
                                                                    new InitialSelection(
                                                                            parents, null))
                                                            : service.parentsByIds(
                                                                            CatalogKind.LINES,
                                                                            List.of(
                                                                                    parents.getFirst()
                                                                                            .lineId()))
                                                                    .thenApply(
                                                                            lines ->
                                                                                    new InitialSelection(
                                                                                            parents,
                                                                                            lines
                                                                                                    .getFirst()))),
                    selection -> {
                        view.setDisable(false);
                        if (selection.line() != null) w.lineFilter().select(selection.line());
                        selection.parents().forEach(w.parents()::select);
                        loadParents(w.parents(), w, false);
                        w.refreshContext();
                        w.markClean();
                        queueGeneration(w);
                    },
                    error -> {
                        view.setDisable(false);
                        w.feedback(message(error), true);
                    });
        }
    }

    private record InitialSelection(List<Parent> parents, Parent line) {}

    private void changeLine(BulkWorkspace w) {
        CompletableFuture<?> all = reads.remove("all-" + w.kind());
        if (all != null) all.cancel(false);
        w.parents().restrictToLine(w.lineFilter().id());
        w.parents().query().clear();
        w.refreshContext();
        queueGeneration(w);
        loadParents(w.parents(), w, false);
    }

    private void selectAllProducts(BulkWorkspace w) {
        Long lineId = w.lineFilter().id();
        if (lineId == null || disposed || saving.get()) return;
        generation.get(w).stop();
        validation.get(w).stop();
        CompletableFuture<?> preview = reads.remove("preview-" + w.kind());
        if (preview != null) preview.cancel(false);
        w.pending();
        List<Parent> before = w.parents().selected();
        read(
                "all-" + w.kind(),
                () -> productsOfLine(lineId, 0, new ArrayList<>()),
                products -> {
                    if (before.equals(w.parents().selected())) w.parents().selectAll(products);
                    else queueGeneration(w);
                    w.refreshContext();
                },
                error -> w.failed(message(error)));
    }

    private CompletableFuture<List<Parent>> productsOfLine(
            long lineId, int offset, List<Parent> products) {
        return service.parents(CatalogKind.PRODUCTS, "", lineId, offset)
                .thenCompose(
                        page -> {
                            products.addAll(page.rows());
                            if (products.size() > MAX_ROWS)
                                throw new IllegalArgumentException(
                                        "La linea contiene più di "
                                                + MAX_ROWS
                                                + " prodotti. Seleziona un gruppo più piccolo.");
                            return page.more()
                                    ? productsOfLine(lineId, offset + page.rows().size(), products)
                                    : CompletableFuture.completedFuture(List.copyOf(products));
                        });
    }

    private void configurePicker(BulkParentPicker picker, BulkWorkspace workspace) {
        PauseTransition search = timer();
        search.setOnFinished(e -> loadParents(picker, workspace, false));
        picker.query().textProperty().addListener((o, a, b) -> search.playFromStart());
        picker.more().setOnAction(e -> loadParents(picker, workspace, true));
    }

    private void loadParents(BulkParentPicker picker, BulkWorkspace w, boolean append) {
        Long lineId = picker == w.parents() && w.lineFilter() != null ? w.lineFilter().id() : null;
        if (picker == w.parents() && w.lineFilter() != null) {
            picker.setDisable(lineId == null);
            if (lineId == null) {
                CompletableFuture<?> old = reads.remove(picker);
                if (old != null) old.cancel(false);
                picker.results(new ParentPage(List.of(), false, 0), false);
                picker.error("Seleziona prima una linea.");
                return;
            }
        }
        read(
                picker,
                () ->
                        service.parents(
                                picker.kind(),
                                picker.query().getText(),
                                lineId,
                                append ? picker.loaded() : 0),
                page -> {
                    picker.results(page, append);
                    w.refreshContext();
                    if (w.settingsDirty()) queueGeneration(w);
                },
                e -> picker.error(message(e)));
    }

    private void queueGeneration(BulkWorkspace w) {
        if (disposed || saving.get()) return;
        validation.get(w).stop();
        CompletableFuture<?> old = reads.remove("preview-" + w.kind());
        if (old != null) old.cancel(false);
        w.pending();
        generation.get(w).playFromStart();
    }

    private void generate(BulkWorkspace w) {
        if (disposed || saving.get()) return;
        try {
            validation.get(w).stop();
            w.generate();
            verify(w);
        } catch (RuntimeException error) {
            w.failed(message(error));
        }
    }

    private void verify(BulkWorkspace w) {
        if (disposed || saving.get() || !w.hasRows() || reads.containsKey("all-" + w.kind()))
            return;
        if (w.settingsDirty()) {
            w.pending();
            return;
        }
        Request request = w.request();
        w.pending();
        read(
                "preview-" + w.kind(),
                () -> service.preview(request),
                preview -> {
                    if (!w.settingsDirty() && request.equals(w.request())) w.apply(preview);
                },
                error -> w.failed(message(error)));
    }

    private void save(BulkWorkspace w) {
        if (disposed
                || saving.get()
                || reads.containsKey("all-" + w.kind())
                || w.preview() == null
                || !w.preview().savable()
                || w.settingsDirty()) return;
        Preview expected = w.preview();
        saving.set(true);
        view.setDisable(true);
        validation.values().forEach(PauseTransition::stop);
        generation.values().forEach(PauseTransition::stop);
        reads.values().forEach(f -> f.cancel(false));
        reads.clear();
        service.create(expected)
                .whenComplete(
                        (result, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed) return;
                                            saving.set(false);
                                            view.setDisable(false);
                                            if (error != null) {
                                                w.failed(message(error));
                                                return;
                                            }
                                            w.saved(result);
                                            verify(w);
                                        }));
    }

    private void refresh() {
        for (BulkWorkspace w : view.workspaces()) {
            List<Long> ids = w.parents().selected().stream().map(Parent::id).toList();
            if (!ids.isEmpty())
                read(
                        "selected-" + w.kind(),
                        () -> service.parentsByIds(w.kind().parent(), ids),
                        parents -> {
                            w.parents().replaceSelected(parents);
                            if (w.lineFilter() != null)
                                w.parents().restrictToLine(w.lineFilter().id());
                            w.refreshContext();
                            if (w.hasRows()) {
                                if (w.settingsDirty()) queueGeneration(w);
                                else verify(w);
                            }
                        },
                        error -> w.failed(message(error)));
            loadParents(w.parents(), w, false);
            if (w.lineFilter() != null) loadParents(w.lineFilter(), w, false);
        }
    }

    private <T> void read(
            Object key,
            Supplier<CompletableFuture<T>> operation,
            Consumer<T> success,
            Consumer<Throwable> failure) {
        if (disposed) return;
        CompletableFuture<?> old = reads.remove(key);
        if (old != null) old.cancel(false);
        CompletableFuture<T> future;
        try {
            future = operation.get();
        } catch (RuntimeException error) {
            failure.accept(error);
            return;
        }
        reads.put(key, future);
        future.whenComplete(
                (result, error) ->
                        Platform.runLater(
                                () -> {
                                    if (disposed || reads.get(key) != future) return;
                                    reads.remove(key);
                                    if (error == null) success.accept(result);
                                    else failure.accept(error);
                                }));
    }

    private PauseTransition timer() {
        PauseTransition t = new PauseTransition(Duration.millis(300));
        timers.add(t);
        return t;
    }

    private boolean confirm(String title, String message) {
        Alert alert =
                new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.initOwner(view.getScene().getWindow());
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof SQLException sql
                    && sql.getSQLState() != null
                    && sql.getSQLState().startsWith("08"))
                return "Connessione interrotta: l'esito del salvataggio potrebbe non essere"
                        + " confermato. Aggiorna i dati e verifica l'anteprima prima di"
                        + " riprovare.";
        return error.getMessage() == null
                ? "Operazione non riuscita. Verifica i dati e riprova."
                : error.getMessage();
    }

    @Override
    public ReadOnlyBooleanProperty savingProperty() {
        return saving;
    }

    @Override
    public boolean canLeave() {
        return !saving.get()
                && (!view.dirty()
                        || confirm(
                                "Modifiche non salvate",
                                "Vuoi scartare le modifiche non salvate dell'inserimento"
                                        + " multiplo?"));
    }

    @Override
    public void dispose() {
        disposed = true;
        timers.forEach(
                t -> {
                    t.stop();
                    t.setOnFinished(null);
                });
        reads.values().forEach(f -> f.cancel(false));
        reads.clear();
    }
}

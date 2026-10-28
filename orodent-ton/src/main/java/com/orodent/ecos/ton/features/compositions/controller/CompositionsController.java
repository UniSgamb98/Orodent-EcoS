package com.orodent.ecos.ton.features.compositions.controller;

import static com.orodent.ecos.ton.features.compositions.model.Compositions.*;

import com.orodent.ecos.ton.features.compositions.service.CompositionService;
import com.orodent.ecos.ton.features.compositions.view.*;
import com.orodent.ecos.ton.features.compositions.view.CompositionEditorView.Sheet;
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

/** Owns search, read cancellation and dirty-buffer guards; no write is interrupted or replayed. */
public final class CompositionsController implements FeatureController {
    private final CompositionsView view;
    private final CompositionService service;
    private final LongConsumer openArticle, openModel;
    private final SimpleBooleanProperty saving = new SimpleBooleanProperty();
    private final Map<String, CompletableFuture<?>> reads = new HashMap<>();
    private final List<Runnable> cleanups = new ArrayList<>();
    private final PauseTransition listDelay = delay(),
            productDelay = delay(),
            powderDelay = delay(),
            modelDelay = delay();
    private Detail original;
    private List<String> baseline = List.of();
    private int offset, versionsOffset, usagesOffset, modelsOffset, powdersOffset;
    private long selectedId;
    private boolean editing;
    private volatile boolean disposed;

    public CompositionsController(
            CompositionsView view,
            CompositionService service,
            LongConsumer openArticle,
            LongConsumer openModel) {
        this.view = view;
        this.service = service;
        this.openArticle = openArticle;
        this.openModel = openModel;
        var list = view.list();
        var e = view.editor();
        listen(
                list.search().textProperty(),
                (o, a, b) -> {
                    drop("list");
                    listDelay.playFromStart();
                });
        listDelay.setOnFinished(event -> loadList(0));
        list.search().setOnAction(event -> loadList(0));
        list.filter().setOnAction(event -> loadList(0));
        list.archived().setOnAction(event -> loadList(0));
        on(list.create(), this::create);
        on(list.results().refresh(), () -> loadList(offset));
        on(
                list.results().previous(),
                () -> loadList(Math.max(0, offset - CompositionService.PAGE_SIZE)));
        on(list.results().next(), () -> loadList(offset + CompositionService.PAGE_SIZE));
        on(list.results().open(), this::openSelected);
        tableAction(list.results().table(), this::openSelected);
        listen(
                e.product().query().textProperty(),
                (o, a, b) -> {
                    if (!e.updating() && !e.product().updating()) {
                        drop("products");
                        productDelay.playFromStart();
                    }
                });
        productDelay.setOnFinished(event -> loadProducts());
        e.product().query().setOnAction(event -> loadProducts());
        listen(
                e.recipe().search().textProperty(),
                (o, a, b) -> {
                    if (!e.updating()) {
                        drop("powders");
                        powderDelay.playFromStart();
                    }
                });
        powderDelay.setOnFinished(event -> loadPowders(0));
        e.recipe().search().setOnAction(event -> loadPowders(0));
        on(
                e.recipe().powderPrevious(),
                () -> loadPowders(Math.max(0, powdersOffset - CompositionService.PAGE_SIZE)));
        on(
                e.recipe().powderNext(),
                () -> loadPowders(powdersOffset + CompositionService.PAGE_SIZE));
        listen(
                e.modelSearch().textProperty(),
                (o, a, b) -> {
                    if (!e.updating()) {
                        drop("models");
                        modelDelay.playFromStart();
                    }
                });
        modelDelay.setOnFinished(event -> loadModels(0));
        e.modelSearch().setOnAction(event -> loadModels(0));
        e.matching().setOnAction(event -> loadModels(0));
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
                            cancelReads();
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
                                "Nuova bozza creata. Le revisioni delle polveri e le specifiche"
                                        + " precedenti rimangono conservate.",
                                Sheet.RECIPE);
                });
        on(
                e.retire(),
                () -> {
                    if (original != null
                            && canLeave()
                            && confirm(
                                    "Ritira composizione",
                                    "La versione sarà esclusa dalle nuove selezioni. Le specifiche"
                                            + " già collegate rimangono conservate."))
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
                                            + " riferimenti vengono ricontrollati nel database."))
                        write(
                                () ->
                                        service.delete(original.revision())
                                                .thenApply(ignored -> null),
                                "Bozza eliminata.",
                                Sheet.DATA);
                });
        on(
                e.recipe().remove(),
                () -> {
                    if (confirm(
                            "Rimuovi strato",
                            "Rimuovere lo strato selezionato e tutti i suoi ingredienti? Le quote"
                                    + " degli altri strati non vengono redistribuite.")) {
                        e.state().removeLayer(e.recipe().selected());
                        e.recipe().removed();
                    }
                });
        on(e.recipe().copy(), this::copyLayer);
        e.versions()
                .setOnAction(
                        event -> {
                            if (e.updating()
                                    || !editing
                                    || original == null
                                    || e.versions().getValue() == null) return;
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
                    if (!e.updating() && editing) loadSheet();
                });
        on(e.models().refresh(), () -> loadModels(modelsOffset));
        on(
                e.models().previous(),
                () -> loadModels(Math.max(0, modelsOffset - CompositionService.PAGE_SIZE)));
        on(e.models().next(), () -> loadModels(modelsOffset + CompositionService.PAGE_SIZE));
        on(e.models().open(), this::openModel);
        tableAction(e.models().table(), this::openModel);
        on(e.history().refresh(), () -> loadVersions(versionsOffset));
        on(
                e.history().previous(),
                () -> loadVersions(Math.max(0, versionsOffset - CompositionService.PAGE_SIZE)));
        on(e.history().next(), () -> loadVersions(versionsOffset + CompositionService.PAGE_SIZE));
        on(e.history().open(), this::openVersion);
        tableAction(e.history().table(), this::openVersion);
        on(e.usages().refresh(), () -> loadUsages(usagesOffset));
        on(
                e.usages().previous(),
                () -> loadUsages(Math.max(0, usagesOffset - CompositionService.PAGE_SIZE)));
        on(e.usages().next(), () -> loadUsages(usagesOffset + CompositionService.PAGE_SIZE));
        on(e.usages().open(), this::openUsage);
        tableAction(e.usages().table(), this::openUsage);
    }

    private static PauseTransition delay() {
        return new PauseTransition(Duration.millis(250));
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
                                "Modifiche alla composizione",
                                "Ci sono modifiche non salvate. Vuoi scartarle?"));
    }

    private boolean confirm(String title, String message) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(title);
        if (view.getScene() != null) alert.initOwner(view.getScene().getWindow());
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void copyLayer() {
        var e = view.editor();
        int target = e.recipe().selected();
        var candidates = new ArrayList<RecipeState.Stratum>(e.state().layers());
        candidates.remove(target);
        if (candidates.isEmpty()) return;
        var dialog = new ChoiceDialog<>(candidates.getFirst(), candidates);
        dialog.setTitle("Copia ricetta da strato");
        dialog.setHeaderText("Scegli lo strato di origine");
        if (view.getScene() != null) dialog.initOwner(view.getScene().getWindow());
        dialog.showAndWait()
                .ifPresent(
                        source -> {
                            if (confirm(
                                    "Sostituisci ingredienti",
                                    "Copiare polveri e quote dello strato scelto, sostituendo gli"
                                            + " ingredienti correnti?")) {
                                e.state().copy(e.state().layers().indexOf(source), target);
                                e.recipe().copied();
                            }
                        });
    }

    private void create() {
        if (!canLeave()) return;
        cancelReads();
        original = null;
        editing = true;
        versionsOffset = usagesOffset = modelsOffset = powdersOffset = 0;
        view.editor().show(null);
        view.editor().sheet(Sheet.DATA);
        baseline = view.editor().fingerprint();
        view.showEditor();
        loadProducts();
        loadPowders(0);
    }

    private void openSelected() {
        Summary row = view.list().results().table().getSelectionModel().getSelectedItem();
        if (row != null && !saving.get()) load(row.id(), Sheet.DATA);
    }

    private void loadList(int next) {
        if (disposed || editing || saving.get()) return;
        listDelay.stop();
        offset = next;
        var list = view.list();
        list.results().loading();
        read(
                "list",
                () ->
                        service.page(
                                list.search().getText(),
                                list.filter().getValue(),
                                list.archived().isSelected(),
                                offset),
                page -> {
                    if (editing) return;
                    if (page.rows().isEmpty() && page.total() > 0 && offset > 0) {
                        loadList(
                                (int) ((page.total() - 1) / CompositionService.PAGE_SIZE)
                                        * CompositionService.PAGE_SIZE);
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

    private void loadProducts() {
        if (!editing || original != null || saving.get()) return;
        productDelay.stop();
        var lookup = view.editor().product();
        read(
                "products",
                () -> service.products(lookup.query().getText(), lookup.id()),
                lookup::results,
                error -> view.editor().feedback(error, true));
    }

    private void loadPowders(int next) {
        if (!editing || saving.get()) return;
        powderDelay.stop();
        powdersOffset = next;
        read(
                "powders",
                () -> service.powderPage(view.editor().recipe().search().getText(), next),
                page -> {
                    if (page.rows().isEmpty() && page.total() > 0 && next > 0) {
                        loadPowders(
                                (int) ((page.total() - 1) / CompositionService.PAGE_SIZE)
                                        * CompositionService.PAGE_SIZE);
                        return;
                    }
                    view.editor().recipe().choices(page);
                },
                error -> view.editor().feedback(error, true));
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
        versionsOffset = usagesOffset = modelsOffset = powdersOffset = 0;
        view.editor().show(detail);
        view.editor().sheet(sheet);
        baseline = view.editor().fingerprint();
        view.showEditor();
        if (!detail.revision().frozen()) loadPowders(0);
        loadSheet();
    }

    private void loadSheet() {
        if (saving.get()) return;
        switch (view.editor().sheet()) {
            case MODELS -> loadModels(modelsOffset);
            case USAGES -> {
                if (original != null) loadUsages(usagesOffset);
            }
            case VERSIONS -> {
                if (original != null) loadVersions(versionsOffset);
            }
            default -> {}
        }
    }

    private void loadModels(int next) {
        if (!editing || saving.get()) return;
        modelDelay.stop();
        modelsOffset = next;
        var e = view.editor();
        e.models().loading();
        List<Integer> numbers =
                e.state().layers().stream().map(RecipeState.Stratum::number).toList();
        read(
                "models",
                () ->
                        service.models(
                                numbers,
                                e.modelSearch().getText(),
                                e.matching().isSelected(),
                                next),
                e.models()::show,
                e.models()::error);
    }

    private void loadVersions(int next) {
        if (original == null || saving.get()) return;
        versionsOffset = next;
        var page = view.editor().history();
        page.loading();
        read(
                "versions",
                () -> service.versions(original.product().id(), next),
                page::show,
                page::error);
    }

    private void loadUsages(int next) {
        if (original == null || saving.get()) return;
        usagesOffset = next;
        var page = view.editor().usages();
        page.loading();
        read(
                "usages",
                () -> service.usages(original.revision().id(), next),
                page::show,
                page::error);
    }

    private void openVersion() {
        var selected = view.editor().history().table().getSelectionModel().getSelectedItem();
        if (selected != null && canLeave()) load(selected.id(), Sheet.DATA);
    }

    private void openUsage() {
        var selected = view.editor().usages().table().getSelectionModel().getSelectedItem();
        if (selected != null && !saving.get()) openArticle.accept(selected.articleId());
    }

    private void openModel() {
        var selected = view.editor().models().table().getSelectionModel().getSelectedItem();
        if (selected != null && !saving.get()) openModel.accept(selected.id());
    }

    private void save(boolean approve) {
        if (saving.get()) return;
        try {
            Draft draft = view.editor().draft();
            if (approve) {
                draft.requireApprovable();
                if (!confirm(
                        "Approva composizione",
                        "La ricetta e le revisioni delle polveri diventeranno di sola lettura. Le"
                            + " specifiche già approvate non verranno aggiornate automaticamente."))
                    return;
            }
            write(
                    () ->
                            original == null
                                    ? service.create(draft, approve)
                                    : service.save(original.revision(), draft, approve),
                    approve
                            ? "Composizione approvata. La ricetta è ora in sola lettura."
                            : "Bozza salvata.",
                    view.editor().sheet());
        } catch (RuntimeException invalid) {
            view.editor().feedback(message(invalid), true);
        }
    }

    private void write(Supplier<CompletableFuture<Detail>> operation, String success, Sheet sheet) {
        if (disposed || saving.get()) return;
        cancelReads();
        saving.set(true);
        view.setDisable(true);
        view.editor().feedback("Salvataggio…", false);
        CompletableFuture<Detail> command;
        try {
            command = operation.get();
        } catch (RuntimeException failure) {
            saving.set(false);
            view.setDisable(false);
            view.editor().feedback(message(failure), true);
            return;
        }
        command.whenComplete(
                (detail, failure) -> {
                    if (disposed) {
                        if (failure != null)
                            System.getLogger(getClass().getName())
                                    .log(
                                            System.Logger.Level.WARNING,
                                            "Scrittura composizione terminata con errore dopo la"
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

    private void drop(String slot) {
        var old = reads.remove(slot);
        if (old != null) old.cancel(false);
    }

    private <T> void read(
            String slot,
            Supplier<CompletableFuture<T>> operation,
            Consumer<T> success,
            Consumer<String> failure) {
        drop(slot);
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
        for (var timer : List.of(listDelay, productDelay, powderDelay, modelDelay)) timer.stop();
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
        for (var timer : List.of(listDelay, productDelay, powderDelay, modelDelay))
            timer.setOnFinished(null);
        cleanups.forEach(Runnable::run);
        cleanups.clear();
        view.list().search().setOnAction(null);
        view.list().filter().setOnAction(null);
        view.list().archived().setOnAction(null);
        var e = view.editor();
        e.product().query().setOnAction(null);
        e.recipe().search().setOnAction(null);
        e.modelSearch().setOnAction(null);
        e.matching().setOnAction(null);
        e.versions().setOnAction(null);
        view.dispose();
    }
}

package com.orodent.ecos.ton.features.catalog.controller;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;
import com.orodent.ecos.ton.features.catalog.service.*;
import com.orodent.ecos.ton.features.catalog.view.*;
import com.orodent.ecos.ton.features.catalog.view.CatalogEditorView.Sheet;
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

/** Owns the catalogue session. Only reads are cancelled; writes are never replayed. */
public final class CatalogController implements FeatureController {
    private final CatalogKind kind;
    private final CatalogView view;
    private final CatalogService service;
    private final SpecificationService specifications;
    private final Consumer<Navigation> navigate;
    private final SimpleBooleanProperty saving = new SimpleBooleanProperty();
    private final Map<String, CompletableFuture<?>> reads = new HashMap<>();
    private final List<Runnable> cleanup = new ArrayList<>();
    private final List<PauseTransition> timers = new ArrayList<>();
    private final PauseTransition debounce = timer();
    private Detail original;
    private Specification selectedVersion;
    private List<String> baseline = List.of(), specBaseline = List.of();
    private int offset, relatedOffset, historyOffset, specOffset;
    private long selectedId;
    private boolean editing, specLoaded;
    private volatile boolean disposed;
    private Sheet lastSheet = Sheet.GENERAL;

    public CatalogController(
            CatalogKind kind,
            CatalogView view,
            CatalogService service,
            SpecificationService specifications,
            Consumer<Navigation> navigate) {
        this.kind = kind;
        this.view = view;
        this.service = service;
        this.specifications = specifications;
        this.navigate = navigate;
        var list = view.list();
        var editor = view.editor();
        debounce.setOnFinished(e -> loadList(0));
        listen(list.search().textProperty(), (o, a, b) -> debounce.playFromStart());
        listen(list.diameter().textProperty(), (o, a, b) -> debounce.playFromStart());
        listen(list.height().textProperty(), (o, a, b) -> debounce.playFromStart());
        on(list.refresh(), () -> loadList(offset));
        on(list.previous(), () -> loadList(Math.max(0, offset - CatalogService.PAGE_SIZE)));
        on(list.next(), () -> loadList(offset + CatalogService.PAGE_SIZE));
        list.archived().setOnAction(e -> loadList(0));
        cleanup.add(() -> list.archived().setOnAction(null));
        list.search().setOnAction(e -> loadList(0));
        cleanup.add(() -> list.search().setOnAction(null));
        on(
                list.create(),
                () ->
                        create(
                                kind == CatalogKind.PRODUCTS
                                        ? list.line().value()
                                        : kind == CatalogKind.ARTICLES
                                                ? list.product().value()
                                                : null));
        on(list.open(), this::openSelected);
        listen(
                list.table().getSelectionModel().selectedItemProperty(),
                (o, a, b) -> list.open().setDisable(b == null || list.table().isDisabled()));
        list.table()
                .setOnMouseClicked(
                        e -> {
                            if (e.getClickCount() == 2) openSelected();
                        });
        list.table()
                .setOnKeyPressed(
                        e -> {
                            if (e.getCode() == KeyCode.ENTER) {
                                openSelected();
                                e.consume();
                            }
                        });
        cleanup.add(
                () -> {
                    list.table().setOnMouseClicked(null);
                    list.table().setOnKeyPressed(null);
                });
        configureLookup(list.line(), true);
        configureLookup(list.product(), true);
        configureLookup(editor.form().parent(), false);
        on(
                editor.back(),
                () -> {
                    if (canLeave()) {
                        cancelReads();
                        editing = false;
                        view.showList();
                        loadList(offset);
                        loadListChoices();
                    }
                });
        on(
                editor.reload(),
                () -> {
                    if (original != null && canLeave()) load(original.entry().id(), editor.sheet());
                });
        on(
                editor.cancel(),
                () -> {
                    if (canLeave()) {
                        if (original == null) {
                            editing = false;
                            view.showList();
                            loadList(offset);
                        } else render(original, Sheet.GENERAL);
                    }
                });
        on(editor.save(), this::save);
        on(
                editor.archive(),
                () -> {
                    if (original != null
                            && canLeave()
                            && confirm(
                                    original.entry().active()
                                            ? "Archivia anagrafica"
                                            : "Riattiva anagrafica",
                                    "I dati e lo storico vengono conservati. I figli non cambiano"
                                        + " stato automaticamente."))
                        write(
                                () ->
                                        service.setActive(
                                                original.entry(), !original.entry().active()),
                                "Stato aggiornato.",
                                Sheet.GENERAL);
                });
        on(
                editor.delete(),
                () -> {
                    if (original != null
                            && canLeave()
                            && confirm(
                                    "Elimina anagrafica",
                                    "Eliminare definitivamente questo elemento mai utilizzato? I"
                                        + " riferimenti vengono ricontrollati prima"
                                        + " dell'eliminazione."))
                        write(
                                () -> service.delete(original.entry()).thenApply(ignored -> null),
                                "Anagrafica eliminata.",
                                Sheet.GENERAL);
                });
        on(
                editor.parent(),
                () -> {
                    if (original == null) return;
                    Choice parent = editor.form().parent().value();
                    if (parent != null)
                        navigate.accept(Navigation.open(kind.parent(), parent.id()));
                });
        listen(
                editor.tabs().getSelectionModel().selectedItemProperty(),
                (o, a, b) -> sheetChanged());
        if (editor.related() != null) {
            var related = editor.related();
            on(
                    related.create(),
                    () -> {
                        if (original != null)
                            navigate.accept(
                                    new Navigation(
                                            kind.child(), original.entry().id(), null, true));
                    });
            on(
                    related.open(),
                    () -> {
                        Entry e = related.table().getSelectionModel().getSelectedItem();
                        if (e != null) navigate.accept(Navigation.open(kind.child(), e.id()));
                    });
            on(
                    related.previous(),
                    () -> loadRelated(Math.max(0, relatedOffset - CatalogService.PAGE_SIZE)));
            on(related.next(), () -> loadRelated(relatedOffset + CatalogService.PAGE_SIZE));
        }
        on(
                editor.history().previous(),
                () -> loadHistory(Math.max(0, historyOffset - CatalogService.PAGE_SIZE)));
        on(editor.history().next(), () -> loadHistory(historyOffset + CatalogService.PAGE_SIZE));
        if (editor.specifications() != null) configureSpecifications();
    }

    public void start(Navigation route) {
        if (route.parentFilter() != null && kind.parent() != null) {
            var lookup = kind == CatalogKind.PRODUCTS ? view.list().line() : view.list().product();
            lookup.select(
                    new Choice(route.parentFilter(), "Caricamento…", "", false, null, null, null));
        }
        loadListChoices();
        if (route.openId() != null) load(route.openId(), Sheet.GENERAL);
        else if (route.create()) {
            Choice context =
                    kind.parent() == null
                            ? null
                            : (kind == CatalogKind.PRODUCTS
                                            ? view.list().line()
                                            : view.list().product())
                                    .value();
            create(context);
        } else loadList(0);
    }

    private void configureLookup(CatalogLookup lookup, boolean filter) {
        if (lookup == null) return;
        PauseTransition delay = timer();
        delay.setOnFinished(e -> loadLookup(lookup));
        listen(
                lookup.query().textProperty(),
                (o, a, b) -> {
                    if (!lookup.updating() && !view.editor().updating()) delay.playFromStart();
                });
        if (filter)
            listen(
                    lookup.choices().valueProperty(),
                    (o, a, b) -> {
                        if (!lookup.updating() && !editing) loadList(0);
                    });
    }

    private void loadLookup(CatalogLookup lookup) {
        if (lookup == null) return;
        read(
                "lookup-" + System.identityHashCode(lookup),
                () -> service.choices(lookup.kind(), lookup.query().getText(), lookup.id()),
                lookup::results,
                e -> {
                    if (editing) view.editor().feedback(message(e), true);
                    else view.list().error(message(e));
                });
    }

    private void loadListChoices() {
        loadLookup(view.list().line());
        loadLookup(view.list().product());
    }

    private void create(Choice context) {
        if (!canLeave()) return;
        cancelReads();
        original = null;
        selectedVersion = null;
        editing = true;
        specLoaded = false;
        relatedOffset = historyOffset = specOffset = 0;
        view.editor().show(null, context);
        view.editor().sheet(Sheet.GENERAL);
        lastSheet = Sheet.GENERAL;
        view.showEditor();
        baselines();
        loadLookup(view.editor().form().parent());
    }

    private void openSelected() {
        Entry e = view.list().table().getSelectionModel().getSelectedItem();
        if (e != null && !saving.get()) load(e.id(), Sheet.GENERAL);
    }

    private void loadList(int pageOffset) {
        if (disposed || editing) return;
        debounce.stop();
        Filter filter;
        try {
            filter = view.list().filter();
        } catch (IllegalArgumentException invalid) {
            view.list().error(message(invalid));
            return;
        }
        offset = pageOffset;
        view.list().loading();
        read(
                "list",
                () -> service.page(kind, filter, offset),
                page -> {
                    if (editing) return;
                    if (page.rows().isEmpty() && page.total() > 0 && offset > 0) {
                        loadList(
                                (int) ((page.total() - 1) / CatalogService.PAGE_SIZE)
                                        * CatalogService.PAGE_SIZE);
                        return;
                    }
                    view.list().show(page);
                    page.rows().stream()
                            .filter(e -> e.id() == selectedId)
                            .findFirst()
                            .ifPresent(
                                    e -> {
                                        view.list().table().getSelectionModel().select(e);
                                        view.list().table().scrollTo(e);
                                    });
                },
                e -> view.list().error(message(e)));
    }

    private void load(long id, Sheet sheet) {
        cancelReads();
        editing = false;
        selectedId = id;
        view.loading();
        read(
                "detail",
                () -> service.load(kind, id),
                d -> render(d, sheet),
                e -> {
                    view.showList();
                    view.list().error(message(e));
                });
    }

    private void render(Detail detail, Sheet sheet) {
        original = detail;
        editing = true;
        selectedId = detail.entry().id();
        selectedVersion = null;
        specLoaded = false;
        relatedOffset = historyOffset = specOffset = 0;
        view.editor().show(detail, null);
        view.editor().sheet(sheet);
        lastSheet = sheet;
        view.showEditor();
        baselines();
        loadLookup(view.editor().form().parent());
        loadSheet();
    }

    private void baselines() {
        baseline = view.editor().form().fingerprint();
        specBaseline =
                view.editor().specifications() == null
                        ? List.of()
                        : view.editor().specifications().fingerprint();
    }

    private boolean metadataDirty() {
        return editing && !baseline.equals(view.editor().form().fingerprint());
    }

    private boolean specificationDirty() {
        return editing
                && specLoaded
                && view.editor().specifications() != null
                && !specBaseline.equals(view.editor().specifications().fingerprint());
    }

    @Override
    public boolean canLeave() {
        return !saving.get()
                && (!editing
                        || !metadataDirty() && !specificationDirty()
                        || confirm(
                                "Modifiche non salvate",
                                "Vuoi scartare le modifiche non salvate prima di continuare?"));
    }

    private void sheetChanged() {
        if (view.editor().updating() || !editing) return;
        Sheet next = view.editor().sheet();
        if (original != null
                && (next == Sheet.SPECIFICATIONS && metadataDirty()
                        || next == Sheet.GENERAL && specificationDirty())) {
            if (!confirm(
                    "Modifiche non salvate",
                    "Salva le modifiche nella scheda corrente oppure scartale prima di modificare"
                        + " l'altra scheda. Vuoi scartarle?")) {
                view.editor().sheet(lastSheet);
                return;
            }
            if (next == Sheet.SPECIFICATIONS) {
                view.editor().form().show(original, null);
                baseline = view.editor().form().fingerprint();
            } else {
                view.editor().specifications().fill(selectedVersion);
                specBaseline = view.editor().specifications().fingerprint();
            }
        }
        lastSheet = next;
        loadSheet();
    }

    private void loadSheet() {
        if (original == null) return;
        switch (view.editor().sheet()) {
            case GENERAL -> {}
            case RELATED -> loadRelated(relatedOffset);
            case HISTORY -> loadHistory(historyOffset);
            case SPECIFICATIONS -> {
                if (!specLoaded) loadSpecs(specOffset, false);
            }
        }
    }

    private void loadRelated(int pageOffset) {
        if (original == null) return;
        relatedOffset = pageOffset;
        var related = view.editor().related();
        related.loading();
        Filter filter =
                kind == CatalogKind.LINES
                        ? new Filter("", true, original.entry().id(), null, null, null)
                        : new Filter("", true, null, original.entry().id(), null, null);
        read(
                "related",
                () -> service.page(kind.child(), filter, relatedOffset),
                related::show,
                e -> related.error(message(e)));
    }

    private void loadHistory(int pageOffset) {
        if (original == null) return;
        historyOffset = pageOffset;
        view.editor().history().loading();
        read(
                "history",
                () -> service.history(kind, original.entry().id(), historyOffset),
                view.editor().history()::show,
                e -> view.editor().history().error(message(e)));
    }

    private void save() {
        try {
            Draft draft = view.editor().form().draft();
            if (original == null)
                write(() -> service.create(draft), "Anagrafica creata.", Sheet.GENERAL);
            else
                write(
                        () -> service.save(original.entry(), draft),
                        "Revisione salvata.",
                        Sheet.GENERAL);
        } catch (RuntimeException invalid) {
            view.editor().feedback(message(invalid), true);
        }
    }

    private void configureSpecifications() {
        var s = view.editor().specifications();
        on(s.save(), () -> saveSpecification(false));
        on(s.approve(), () -> saveSpecification(true));
        on(
                s.copy(),
                () -> {
                    if (selectedVersion != null && canLeave())
                        write(
                                () ->
                                        specifications.copy(
                                                (Article) original.entry(), selectedVersion),
                                "Nuova bozza creata.",
                                Sheet.SPECIFICATIONS);
                });
        on(
                s.prefer(),
                () -> {
                    if (selectedVersion != null && canLeave())
                        write(
                                () ->
                                        specifications.prefer(
                                                (Article) original.entry(), selectedVersion),
                                "Versione predefinita aggiornata.",
                                Sheet.SPECIFICATIONS);
                });
        on(
                s.retire(),
                () -> {
                    if (selectedVersion != null
                            && canLeave()
                            && confirm(
                                    "Ritira versione",
                                    "I dati rimangono nello storico. Se è predefinita, dovrai"
                                        + " scegliere un'altra versione prima di produrre."))
                        write(
                                () ->
                                        specifications.retire(
                                                (Article) original.entry(), selectedVersion),
                                "Versione ritirata.",
                                Sheet.SPECIFICATIONS);
                });
        on(
                s.refresh(),
                () -> {
                    if (canLeave()) {
                        discardForms();
                        loadSpecs(specOffset, false);
                    }
                });
        on(
                s.previous(),
                () -> {
                    if (canLeave()) {
                        discardForms();
                        loadSpecs(Math.max(0, specOffset - CatalogService.PAGE_SIZE), false);
                    }
                });
        on(
                s.next(),
                () -> {
                    if (canLeave()) {
                        discardForms();
                        loadSpecs(specOffset + CatalogService.PAGE_SIZE, false);
                    }
                });
        s.versions()
                .setOnAction(
                        e -> {
                            if (s.updating() || !specLoaded) return;
                            Specification next = s.versions().getValue();
                            if (Objects.equals(next, selectedVersion)) return;
                            if (specificationDirty()
                                    && !confirm(
                                            "Modifiche alla versione",
                                            "Vuoi scartare le modifiche non salvate di questa"
                                                + " versione?")) {
                                s.selectVersion(selectedVersion);
                                return;
                            }
                            selectedVersion = next;
                            s.fill(next);
                            specBaseline = s.fingerprint();
                        });
        cleanup.add(() -> s.versions().setOnAction(null));
        PauseTransition delay = timer();
        delay.setOnFinished(
                e -> {
                    if (original != null && specLoaded) loadSpecs(specOffset, true);
                });
        listen(
                s.compositionQuery().textProperty(),
                (o, a, b) -> {
                    if (!s.updating() && !view.editor().updating()) delay.playFromStart();
                });
        listen(
                s.modelQuery().textProperty(),
                (o, a, b) -> {
                    if (!s.updating() && !view.editor().updating()) delay.playFromStart();
                });
    }

    private void discardForms() {
        view.editor().form().show(original, null);
        baseline = view.editor().form().fingerprint();
        if (view.editor().specifications() != null) {
            view.editor().specifications().fill(selectedVersion);
            specBaseline = view.editor().specifications().fingerprint();
        }
    }

    private void loadSpecs(int pageOffset, boolean choicesOnly) {
        if (original == null || !(original.entry() instanceof Article article)) return;
        specOffset = pageOffset;
        var s = view.editor().specifications();
        if (!choicesOnly) s.loading();
        read(
                "specifications",
                () ->
                        specifications.page(
                                article,
                                specOffset,
                                s.compositionQuery().getText(),
                                s.modelQuery().getText()),
                page -> {
                    if (choicesOnly) {
                        s.choices(page);
                        return;
                    }
                    s.versions().setDisable(false);
                    s.show(page, null);
                    selectedVersion = s.versions().getValue();
                    specLoaded = true;
                    specBaseline = s.fingerprint();
                },
                e -> s.error(message(e)));
    }

    private void saveSpecification(boolean approve) {
        if (original == null) return;
        try {
            var draft = view.editor().specifications().draft();
            if (approve
                    && !confirm(
                            "Approva specifica produttiva",
                            "Composizione e modello verranno collegati alle revisioni anagrafiche"
                                + " correnti. La versione approvata sarà di sola lettura."))
                return;
            write(
                    () ->
                            specifications.save(
                                    (Article) original.entry(), selectedVersion, draft, approve),
                    approve ? "Versione approvata." : "Bozza salvata.",
                    Sheet.SPECIFICATIONS);
        } catch (RuntimeException invalid) {
            view.editor().feedback(message(invalid), true);
        }
    }

    private void write(Supplier<CompletableFuture<Detail>> operation, String success, Sheet sheet) {
        if (disposed || saving.get()) return;
        saving.set(true);
        view.setDisable(true);
        cancelReads();
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
                (detail, error) -> {
                    if (disposed) {
                        if (error != null)
                            System.getLogger(getClass().getName())
                                    .log(
                                            System.Logger.Level.WARNING,
                                            "Scrittura catalogo terminata con errore dopo la"
                                                + " chiusura della pagina",
                                            error);
                        return;
                    }
                    Platform.runLater(
                            () -> {
                                if (disposed) return;
                                saving.set(false);
                                view.setDisable(false);
                                if (error != null) {
                                    view.editor().feedback(message(error), true);
                                    return;
                                }
                                if (detail == null) {
                                    editing = false;
                                    original = null;
                                    selectedId = 0;
                                    view.showList();
                                    loadList(offset);
                                    loadListChoices();
                                } else {
                                    render(detail, sheet);
                                    view.editor().feedback(success, false);
                                }
                            });
                });
    }

    private <T> void read(
            String slot,
            Supplier<CompletableFuture<T>> query,
            Consumer<T> success,
            Consumer<Throwable> failure) {
        if (disposed) return;
        CompletableFuture<?> previous = reads.remove(slot);
        if (previous != null) previous.cancel(false);
        CompletableFuture<T> future;
        try {
            future = query.get();
        } catch (RuntimeException error) {
            failure.accept(error);
            return;
        }
        reads.put(slot, future);
        future.whenComplete(
                (value, error) -> {
                    if (disposed) return;
                    Platform.runLater(
                            () -> {
                                if (disposed || reads.get(slot) != future) return;
                                reads.remove(slot);
                                if (error == null) success.accept(value);
                                else failure.accept(error);
                            });
                });
    }

    private PauseTransition timer() {
        PauseTransition t = new PauseTransition(Duration.millis(250));
        timers.add(t);
        return t;
    }

    private <T> void listen(ObservableValue<T> value, ChangeListener<T> listener) {
        value.addListener(listener);
        cleanup.add(() -> value.removeListener(listener));
    }

    private void on(Button button, Runnable action) {
        button.setOnAction(
                e -> {
                    if (!disposed && !saving.get()) action.run();
                });
        cleanup.add(() -> button.setOnAction(null));
    }

    private boolean confirm(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(title);
        if (view.getScene() != null) alert.initOwner(view.getScene().getWindow());
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void cancelReads() {
        for (PauseTransition t : timers) t.stop();
        var pending = new ArrayList<>(reads.values());
        reads.clear();
        pending.forEach(f -> f.cancel(false));
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        return error.getMessage() == null
                ? "Operazione non riuscita. Verifica la connessione e riprova."
                : error.getMessage();
    }

    @Override
    public ReadOnlyBooleanProperty savingProperty() {
        return saving;
    }

    @Override
    public void dispose() {
        disposed = true;
        cancelReads();
        timers.forEach(t -> t.setOnFinished(null));
        cleanup.forEach(Runnable::run);
        cleanup.clear();
        view.dispose();
    }
}

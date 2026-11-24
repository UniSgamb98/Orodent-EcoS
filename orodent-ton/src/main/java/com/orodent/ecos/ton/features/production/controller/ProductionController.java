package com.orodent.ecos.ton.features.production.controller;

import static com.orodent.ecos.ton.features.production.model.Production.*;

import com.orodent.ecos.ton.features.production.service.*;
import com.orodent.ecos.ton.features.production.view.ProductionView;
import com.orodent.ecos.ton.navigation.FeatureController;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Owns asynchronous reads, quantity edits, immutable preparation and document emission. */
public final class ProductionController implements FeatureController {
    private final ProductionView view;
    private final ProductionService service;
    private final ReadOnlyBooleanWrapper saving = new ReadOnlyBooleanWrapper();
    private final Map<String, CompletableFuture<?>> reads = new HashMap<>();
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(250)),
            documentDelay = new PauseTransition(Duration.millis(350));
    private final ChangeListener<String> searchListener = (o, a, b) -> searchDelay.playFromStart();
    private Catalogues catalogues;
    private Workspace workspace;
    private Saved saved;
    private Plan plan;
    private boolean editing, updating, dirty, disposed, documentLoading;
    private ProductChoice product;
    private LineChoice line;
    private BigDecimal diameter;
    private BigDecimal preferredDiameter;
    private Runnable returnAction;
    private long formVersion, renderedVersion = -1;
    private final ChangeListener<LocalDate> dateListener = (o, a, b) -> changed();
    private final ChangeListener<String> notesListener = (o, a, b) -> changed();

    public ProductionController(
            ProductionView view,
            ProductionService service,
            java.util.function.LongConsumer openArticle,
            java.util.function.LongConsumer openProduct) {
        this.view = view;
        this.service = service;
        view.onArticle(openArticle);
        view.onProductSpecifications(openProduct);
        view.listing.search().textProperty().addListener(searchListener);
        searchDelay.setOnFinished(e -> loadList());
        view.listing.search().setOnAction(e -> loadList());
        view.listState.setOnAction(e -> loadList());
        view.listing
                .refresh()
                .setOnAction(
                        e -> {
                            if (catalogues == null) loadCatalogues();
                            loadList();
                        });
        view.listing.create().setDisable(true);
        view.listing.create().setOnAction(e -> newPlan());
        view.listing.onOpen(
                () -> {
                    var row = view.listing.table().getSelectionModel().getSelectedItem();
                    if (row != null && canLeave()) load(row.id());
                });
        view.callbacks(this::selectProduct, this::changed);
        view.line.setOnAction(e -> selectLine(view.line.getValue()));
        view.diameter.setOnAction(e -> selectDiameter(view.diameter.getValue()));
        view.template.setOnAction(e -> changed());
        view.date.valueProperty().addListener(dateListener);
        view.notes.textProperty().addListener(notesListener);
        view.back.setOnAction(
                e -> {
                    if (canLeave()) {
                        if (returnAction != null) {
                            returnAction.run();
                            return;
                        }
                        cancelReads();
                        editing = false;
                        workspace = null;
                        saved = null;
                        plan = null;
                        dirty = false;
                        view.showList();
                        loadList();
                    }
                });
        view.refresh.setOnAction(
                e -> {
                    if (canLeave()) {
                        if (saved != null) load(saved.id());
                        else if (product != null && diameter != null) {
                            loadCatalogues();
                            loadWorkspace(view.quantities(), Map.of());
                        } else loadCatalogues();
                    }
                });
        view.clear.setOnAction(
                e -> {
                    if (confirm(
                            "Azzerare le quantità?",
                            "Linea, prodotto, documento e note verranno mantenuti.")) view.zero();
                });
        view.save.setOnAction(e -> save(false));
        view.prepare.setOnAction(e -> save(true));
        view.duplicate.setOnAction(e -> duplicate());
        view.tabs
                .getSelectionModel()
                .selectedIndexProperty()
                .addListener(
                        (o, a, b) -> {
                            if (b.intValue() == 2) renderDocument(false);
                        });
        view.previewPrint.setOnAction(e -> print());
        view.print.setOnAction(e -> print());
        documentDelay.setOnFinished(
                e -> {
                    if (view.documentSelected()) renderDocument(false);
                });
        view.document.onPrintFailure(text -> view.feedback(text));
    }

    public void start() {
        loadCatalogues();
        loadList();
    }

    public void onReturn(Runnable action) {
        returnAction = action;
    }

    public void startForProduct(long productId, BigDecimal selectedDiameter) {
        loadCatalogues(
                () -> {
                    newPlan();
                    var selected =
                            catalogues.products().stream()
                                    .filter(p -> p.id() == productId)
                                    .findFirst()
                                    .orElse(null);
                    if (selected == null) {
                        view.feedback(
                                "Il prodotto non è disponibile per nuove produzioni. Verifica"
                                    + " l’anagrafica.");
                        return;
                    }
                    preferredDiameter = selectedDiameter;
                    var selectedLine =
                            catalogues.lines().stream()
                                    .filter(l -> l.id() == selected.lineId())
                                    .findFirst()
                                    .orElse(null);
                    selectLine(selectedLine);
                    updating = true;
                    try {
                        view.line.setValue(selectedLine);
                    } finally {
                        updating = false;
                    }
                    selectProduct(selected);
                    dirty = false;
                });
    }

    public ReadOnlyBooleanProperty savingProperty() {
        return saving.getReadOnlyProperty();
    }

    private void loadCatalogues() {
        loadCatalogues(null);
    }

    private void loadCatalogues(Runnable loaded) {
        read(
                "catalogues",
                service.catalogues(),
                value -> {
                    catalogues = value;
                    updating = true;
                    try {
                        var selectedLine = view.line.getValue();
                        var selectedTemplate = view.template.getValue();
                        view.setCatalogues(value);
                        if (selectedLine != null) {
                            if (!view.line.getItems().contains(selectedLine))
                                view.line.getItems().add(selectedLine);
                            view.line.setValue(selectedLine);
                        }
                        if (selectedTemplate != null) {
                            view.template.getItems().stream()
                                    .filter(Objects::nonNull)
                                    .filter(t -> t.id() == selectedTemplate.id())
                                    .findFirst()
                                    .ifPresent(view.template::setValue);
                        }
                        if (editing) view.showColors(line, product);
                    } finally {
                        updating = false;
                    }
                    view.listing.create().setDisable(false);
                    if (loaded != null) loaded.run();
                },
                text -> {
                    view.listing.error(text);
                    view.listing.create().setDisable(true);
                });
    }

    private void loadList() {
        if (disposed || editing) return;
        searchDelay.stop();
        view.listing.loading();
        read(
                "list",
                service.list(view.listing.search().getText(), view.listState.getValue()),
                value -> view.listing.showRows(value),
                view.listing::error);
    }

    private void newPlan() {
        if (catalogues == null || saving.get()) return;
        cancelReads();
        editing = true;
        saved = null;
        workspace = null;
        plan = null;
        product = null;
        line = null;
        diameter = null;
        updating = true;
        try {
            view.locked.set(false);
            view.line.setValue(null);
            view.diameter.getItems().clear();
            view.date.setValue(LocalDate.now());
            view.notes.clear();
            view.template.setValue(
                    catalogues.templates().isEmpty() ? null : catalogues.templates().getFirst());
            view.showColors(null, null);
            view.clearRows();
            view.state(null);
            view.plan(null);
            view.showEditor();
            view.tabs.getSelectionModel().selectFirst();
            view.feedback("");
        } finally {
            updating = false;
        }
        dirty = false;
        formVersion++;
        renderedVersion = -1;
        documentLoading = false;
        controls();
    }

    private boolean replaceSelection() {
        return view.quantities().values().stream().noneMatch(q -> q > 0)
                || confirm(
                        "Cambiare la selezione?",
                        "Le quantità inserite appartengono al prodotto e al diametro correnti."
                            + " Cambiando selezione riparti con le quantità a zero.");
    }

    private void selectLine(LineChoice value) {
        if (updating || !editing || saving.get()) return;
        if (Objects.equals(value, line)) return;
        if (!replaceSelection()) {
            updating = true;
            try {
                view.line.setValue(line);
            } finally {
                updating = false;
            }
            return;
        }
        cancelReads();
        line = value;
        product = null;
        diameter = null;
        workspace = null;
        plan = null;
        updating = true;
        try {
            view.showColors(value, null);
            view.diameter.getItems().clear();
            view.clearRows();
        } finally {
            updating = false;
        }
        changed();
    }

    private void selectProduct(ProductChoice value) {
        if (updating || saving.get() || value == null || Objects.equals(value, product)) return;
        if (!replaceSelection()) {
            view.showColors(line, product);
            return;
        }
        cancelReads();
        product = value;
        workspace = null;
        plan = null;
        diameter = null;
        view.showColors(line, value);
        view.clearRows();
        changed();
        BigDecimal desired = preferredDiameter;
        preferredDiameter = null;
        read(
                "diameters",
                service.diameters(value.id()),
                values -> {
                    updating = true;
                    try {
                        view.diameter.getItems().setAll(values);
                        diameter =
                                values.stream()
                                        .filter(
                                                d ->
                                                        d.compareTo(
                                                                        desired == null
                                                                                ? new BigDecimal(
                                                                                        "98")
                                                                                : desired)
                                                                == 0)
                                        .findFirst()
                                        .orElse(
                                                desired != null || values.isEmpty()
                                                        ? null
                                                        : values.getFirst());
                        view.diameter.setValue(diameter);
                    } finally {
                        updating = false;
                    }
                    if (diameter != null) loadWorkspace(Map.of(), Map.of());
                    else {
                        view.feedback(
                                "Il prodotto non ha articoli attivi. Crea i formati nei Dati"
                                        + " tecnici.");
                        controls();
                    }
                });
    }

    private void selectDiameter(BigDecimal value) {
        if (updating || !editing || saving.get() || Objects.equals(value, diameter)) return;
        if (!replaceSelection()) {
            updating = true;
            try {
                view.diameter.setValue(diameter);
            } finally {
                updating = false;
            }
            return;
        }
        cancelReads();
        diameter = value;
        workspace = null;
        view.clearRows();
        changed();
        if (product != null && diameter != null) loadWorkspace(Map.of(), Map.of());
    }

    private void loadWorkspace(Map<Long, Integer> quantities, Map<Long, Long> pinned) {
        if (product == null || diameter == null) return;
        workspace = null;
        controls();
        read(
                "workspace",
                service.workspace(product.id(), diameter, pinned),
                value -> {
                    workspace = value;
                    product = value.product();
                    view.showColors(line, product);
                    updating = true;
                    try {
                        view.rows(value, quantities);
                    } finally {
                        updating = false;
                    }
                    formVersion++;
                    recalculate();
                    if (saved != null && saved.plan().schemaVersion() == 1)
                        view.feedback(
                                "Bozza precedente ricalcolata con le specifiche del prodotto:"
                                        + " verifica le quantità prima di salvarla o prepararla.");
                });
    }

    private void load(long id) {
        if (catalogues == null) {
            view.listing.error("Attendi il caricamento dei dati tecnici.");
            return;
        }
        cancelReads();
        read(
                "load",
                service.load(id),
                value -> {
                    editing = true;
                    saved = value;
                    product = value.plan().product();
                    line =
                            catalogues.lines().stream()
                                    .filter(l -> l.id() == product.lineId())
                                    .findFirst()
                                    .orElse(
                                            new LineChoice(
                                                    product.lineId(),
                                                    product.lineCode(),
                                                    product.lineName()));
                    diameter = value.plan().diameter();
                    updating = true;
                    try {
                        view.state(value);
                        if (!view.line.getItems().contains(line)) view.line.getItems().add(line);
                        view.line.setValue(line);
                        view.showColors(line, product);
                        view.diameter.getItems().setAll(diameter);
                        view.diameter.setValue(diameter);
                        view.date.setValue(LocalDate.parse(value.plan().date()));
                        view.notes.setText(value.plan().notes());
                        var t = value.plan().template();
                        if (t != null
                                && view.template.getItems().stream()
                                        .filter(Objects::nonNull)
                                        .noneMatch(v -> v.id() == t.id()))
                            view.template.getItems().add(t);
                        view.template.setValue(t);
                        view.showEditor();
                        view.tabs.getSelectionModel().selectFirst();
                        view.feedback("");
                    } finally {
                        updating = false;
                    }
                    dirty = false;
                    formVersion++;
                    renderedVersion = -1;
                    documentLoading = false;
                    plan = value.plan();
                    if (value.state() == State.PREPARATA) {
                        workspace =
                                new Workspace(
                                        product,
                                        diameter,
                                        plan.formats().stream().map(f -> f.article()).toList());
                        view.rows(workspace, plan.quantities());
                        view.plan(plan);
                        controls();
                    } else {
                        var pinned = new HashMap<Long, Long>();
                        if (plan.schemaVersion() >= 2)
                            plan.formats()
                                    .forEach(
                                            f ->
                                                    pinned.put(
                                                            f.article().id(),
                                                            f.article().specificationId()));
                        loadWorkspace(plan.quantities(), pinned);
                    }
                });
    }

    private void changed() {
        if (updating || !editing || saving.get()) return;
        dirty = true;
        formVersion++;
        renderedVersion = -1;
        recalculate();
        documentDelay.playFromStart();
    }

    private Request request() {
        return new Request(
                view.date.getValue(),
                product == null ? 0 : product.id(),
                diameter,
                view.template.getValue() == null ? null : view.template.getValue().id(),
                view.notes.getText(),
                view.quantities());
    }

    private void recalculate() {
        if (saved != null && saved.state() == State.PREPARATA) {
            plan = saved.plan();
            view.plan(plan);
            controls();
            return;
        }
        view.feedback("");
        if (workspace == null) {
            plan = null;
            view.plan(null);
            controls();
            return;
        }
        try {
            plan = ProductionCalculator.calculate(workspace, request(), view.template.getValue());
            view.plan(plan);
        } catch (RuntimeException invalid) {
            plan = null;
            view.plan(null);
            view.feedback(message(invalid));
        }
        controls();
    }

    private void controls() {
        boolean input = workspace != null && plan != null;
        view.ready(input, input && plan.count() > 0, saving.get());
        boolean document = plan != null && plan.count() > 0 && plan.template() != null;
        view.previewPrint.setDisable(!document);
        view.print.setDisable(!document);
    }

    private void save(boolean prepare) {
        if (saving.get()) return;
        recalculate();
        if (plan == null || workspace == null) return;
        if (prepare
                && !confirm(
                        "Confermare questa produzione?",
                        plan.count()
                                + " dischi · "
                                + plan.formats().size()
                                + " formati · "
                                + product.code()
                                + "\nMiscela: "
                                + ProductionDocumentData.number(plan.prepared())
                                + " g, maggiorazione compresa.\n"
                                + "I dischi saranno registrati e disponibili in Cotture. Le"
                                + " specifiche e le dosi verranno conservate."))
            return;
        Request request = request();
        saving.set(true);
        controls();
        service.save(saved, workspace, request, prepare)
                .whenComplete(
                        (value, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed) return;
                                            if (error != null) {
                                                saving.set(false);
                                                view.feedback(message(error));
                                                controls();
                                                return;
                                            }
                                            saved = value;
                                            plan = value.plan();
                                            dirty = false;
                                            formVersion++;
                                            renderedVersion = -1;
                                            view.state(value);
                                            view.plan(plan);
                                            view.feedback(
                                                    prepare
                                                            ? "Produzione confermata. Dischi"
                                                                  + " disponibili in Cotture."
                                                            : "Bozza salvata nel database"
                                                                    + " condiviso.",
                                                    false);
                                            if (prepare && plan.template() != null) {
                                                renderDocument(true);
                                            } else {
                                                saving.set(false);
                                                controls();
                                                if (view.documentSelected()) renderDocument(false);
                                            }
                                        }));
    }

    private void duplicate() {
        if (saved == null || saving.get()) return;
        var quantities = saved.plan().quantities();
        saved = null;
        view.state(null);
        dirty = true;
        formVersion++;
        renderedVersion = -1;
        view.tabs.getSelectionModel().selectFirst();
        loadWorkspace(quantities, Map.of());
    }

    private void print() {
        try {
            view.documentTab();
            renderDocument(false);
            view.document.print();
        } catch (RuntimeException failure) {
            view.feedback(message(failure));
        }
    }

    private void renderDocument(boolean afterPreparation) {
        if (disposed || plan == null || plan.template() == null || plan.count() == 0) {
            if (afterPreparation) {
                saving.set(false);
                controls();
            }
            view.document.show(plan, "", null, v -> {}, view::feedback);
            return;
        }
        if (documentLoading && renderedVersion == formVersion) return;
        if (renderedVersion == formVersion && !afterPreparation) return;
        long version = formVersion;
        renderedVersion = version;
        documentLoading = true;
        var source = plan;
        var record = saved;
        boolean store =
                record != null && record.state() == State.PREPARATA && record.document() == null;
        if (store) {
            saving.set(true);
            controls();
        }
        view.document.show(
                source,
                record == null ? "Bozza non salvata" : record.number(),
                record == null ? null : record.document(),
                html -> {
                    if (disposed || version != formVersion) return;
                    documentLoading = false;
                    if (store) {
                        service.storeDocument(record.id(), html)
                                .whenComplete(
                                        (document, error) ->
                                                Platform.runLater(
                                                        () -> {
                                                            if (disposed) return;
                                                            saving.set(false);
                                                            if (error != null)
                                                                view.feedback(
                                                                        "Produzione confermata, ma"
                                                                            + " il documento non è"
                                                                            + " stato conservato: "
                                                                                + message(error));
                                                            else {
                                                                saved =
                                                                        new Saved(
                                                                                record.id(),
                                                                                record.token(),
                                                                                record.state(),
                                                                                record.plan(),
                                                                                document);
                                                                view.feedback(
                                                                        "Produzione confermata e"
                                                                            + " foglio operativo"
                                                                            + " conservato.",
                                                                        false);
                                                            }
                                                            controls();
                                                        }));
                    } else if (afterPreparation) {
                        saving.set(false);
                        controls();
                    }
                },
                text -> {
                    if (disposed || version != formVersion) return;
                    documentLoading = false;
                    renderedVersion = -1;
                    saving.set(false);
                    view.feedback(
                            (store ? "Produzione confermata; documento da generare: " : "") + text);
                    controls();
                });
    }

    public boolean canLeave() {
        return !saving.get()
                && (!dirty
                        || confirm(
                                "Modifiche alla produzione",
                                "Ci sono modifiche non salvate. Vuoi scartarle e proseguire?"));
    }

    private boolean confirm(String title, String text) {
        var alert = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (view.getScene() != null) alert.initOwner(view.getScene().getWindow());
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private <T> void read(String key, CompletableFuture<T> future, Consumer<T> success) {
        read(
                key,
                future,
                success,
                text -> {
                    view.feedback(text);
                    view.listing.error(text);
                    controls();
                });
    }

    private <T> void read(
            String key,
            CompletableFuture<T> future,
            Consumer<T> success,
            Consumer<String> failure) {
        var previous = reads.put(key, future);
        if (previous != null) previous.cancel(false);
        future.whenComplete(
                (value, error) ->
                        Platform.runLater(
                                () -> {
                                    if (disposed || reads.get(key) != future) return;
                                    reads.remove(key);
                                    if (error != null) failure.accept(message(error));
                                    else success.accept(value);
                                }));
    }

    private void cancelReads() {
        searchDelay.stop();
        documentDelay.stop();
        reads.values().forEach(f -> f.cancel(false));
        reads.clear();
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        return error.getMessage() == null
                ? "Operazione non riuscita. Aggiorna i dati prima di riprovare."
                : error.getMessage();
    }

    public void dispose() {
        disposed = true;
        cancelReads();
        view.listing.search().textProperty().removeListener(searchListener);
        view.date.valueProperty().removeListener(dateListener);
        view.notes.textProperty().removeListener(notesListener);
        for (var b :
                List.of(
                        view.back,
                        view.refresh,
                        view.clear,
                        view.save,
                        view.prepare,
                        view.duplicate,
                        view.print,
                        view.previewPrint,
                        view.listing.create(),
                        view.listing.refresh())) b.setOnAction(null);
        view.line.setOnAction(null);
        view.diameter.setOnAction(null);
        view.template.setOnAction(null);
        view.listState.setOnAction(null);
        view.listing.search().setOnAction(null);
        searchDelay.setOnFinished(null);
        documentDelay.setOnFinished(null);
        view.dispose();
    }
}

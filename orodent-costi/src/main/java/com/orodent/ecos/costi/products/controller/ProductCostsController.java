package com.orodent.ecos.costi.products.controller;

import com.orodent.ecos.costi.products.csv.ProductCostCsv;
import com.orodent.ecos.costi.products.model.ProductCosts;
import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.products.service.ProductCostService;
import com.orodent.ecos.costi.products.view.ProductCostsView;
import com.orodent.ecos.costi.view.PriceFormats;
import com.orodent.ecos.module.csv.CsvExportDialog;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.*;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Owns one UI session, unsaved parameters and immutable price snapshots. */
public final class ProductCostsController {
    private final ProductCostsView view;
    private final ProductCostService service;
    private final Executor workers;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(180));
    private final ChangeListener<Scene> sceneChanged = (o, b, a) -> attachScene(b, a);
    private final ChangeListener<Window> windowChanged = (o, b, a) -> attachWindow(a);
    private final EventHandler<WindowEvent> closing =
            e -> {
                if (!canLeave()) e.consume();
            };
    private Catalogue catalogue;
    private Product selected;
    private Parameters baseline = Parameters.empty();

    private record Inputs(String common, Map<Long, String> overrides) {
        private Inputs {
            overrides = Map.copyOf(overrides);
        }
    }

    private Inputs originalInputs;
    private final Map<Long, String> overrides = new HashMap<>();
    private Window window;
    private long readEpoch, calculationEpoch, selectionEpoch;
    private int offset;
    private boolean rendering, loading, saving, calculating, exporting, disposed;

    public ProductCostsController(
            ProductCostsView view, ProductCostService service, Executor workers, Runnable home) {
        this.view = view;
        this.service = service;
        this.workers = workers;
        view.search
                .textProperty()
                .addListener(
                        (o, b, a) -> {
                            if (!rendering) filter(0);
                        });
        view.line.setOnAction(
                e -> {
                    if (!rendering) filter(0);
                });
        view.archived.setOnAction(
                e -> {
                    filter(0);
                    recalculate();
                });
        view.alternatives.setOnAction(e -> recalculate());
        view.previous.setOnAction(e -> filter(Math.max(0, offset - ProductCosts.PAGE_SIZE)));
        view.next.setOnAction(e -> filter(offset + ProductCosts.PAGE_SIZE));
        view.products
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, b, a) -> {
                            if (rendering
                                    || a == null
                                    || selected != null && selected.id() == a.id()) return;
                            long intent = ++selectionEpoch;
                            Platform.runLater(
                                    () -> {
                                        if (disposed
                                                || intent != selectionEpoch
                                                || view.products
                                                                .getSelectionModel()
                                                                .getSelectedItem()
                                                        != a) return;
                                        if (canLeave()) select(a);
                                        else restoreProduct();
                                    });
                        });
        view.articles
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, b, a) -> {
                            if (!rendering) showArticle(a);
                        });
        view.commonCost.textProperty().addListener((o, b, a) -> edited());
        view.specific.setOnAction(
                e -> {
                    if (rendering) return;
                    Result row = view.articles.getSelectionModel().getSelectedItem();
                    if (row == null || row.article() == null) return;
                    if (view.specific.isSelected())
                        overrides.put(row.article().id(), view.commonCost.getText());
                    else overrides.remove(row.article().id());
                    showArticle(row);
                    edited();
                });
        view.articleCost
                .textProperty()
                .addListener(
                        (o, b, a) -> {
                            if (rendering) return;
                            Result row = view.articles.getSelectionModel().getSelectedItem();
                            if (row != null
                                    && row.article() != null
                                    && view.specific.isSelected()) {
                                overrides.put(row.article().id(), a);
                                edited();
                            }
                        });
        debounce.setOnFinished(e -> recalculate());
        view.save.setOnAction(e -> save());
        view.cancel.setOnAction(
                e -> {
                    if (selected != null && !saving) resetInputs();
                });
        view.refresh.setOnAction(
                e -> {
                    if (canLeave()) start();
                });
        view.home.setOnAction(
                e -> {
                    if (canLeave()) home.run();
                });
        view.exportAll.setOnAction(e -> export(0));
        view.exportProduct.setOnAction(e -> export(1));
        view.exportDetail.setOnAction(e -> export(2));
        view.exportFiltered.setOnAction(e -> export(3));
        view.sceneProperty().addListener(sceneChanged);
        attachScene(null, view.getScene());
        controls();
    }

    public void start() {
        if (disposed || loading || saving) return;
        loading = true;
        calculating = false;
        long epoch = ++readEpoch;
        calculationEpoch++;
        debounce.stop();
        view.feedback("Caricamento dei cataloghi e dei prezzi…", false);
        controls();
        service.load()
                .whenComplete(
                        (loaded, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed || epoch != readEpoch) return;
                                            loading = false;
                                            if (error != null) {
                                                view.feedback(message(error), true);
                                                controls();
                                                return;
                                            }
                                            catalogue = loaded;
                                            Long previousLine =
                                                    view.line.getValue() == null
                                                            ? null
                                                            : view.line.getValue().id();
                                            rendering = true;
                                            try {
                                                var lines =
                                                        new TreeMap<
                                                                String, ProductCostsView.Line>();
                                                for (Product p : catalogue.products())
                                                    if (p.lineId() != null)
                                                        lines.put(
                                                                p.lineCode(),
                                                                new ProductCostsView.Line(
                                                                        p.lineId(),
                                                                        p.lineCode()
                                                                                + " · "
                                                                                + p.lineName()));
                                                view.line
                                                        .getItems()
                                                        .setAll(
                                                                new ProductCostsView.Line(
                                                                        null, "Tutte le linee"));
                                                view.line.getItems().addAll(lines.values());
                                                view.line.setValue(
                                                        view.line.getItems().stream()
                                                                .filter(
                                                                        l ->
                                                                                Objects.equals(
                                                                                        l.id(),
                                                                                        previousLine))
                                                                .findFirst()
                                                                .orElse(
                                                                        view.line
                                                                                .getItems()
                                                                                .getFirst()));
                                            } finally {
                                                rendering = false;
                                            }
                                            view.captured(
                                                    "Prezzi e dati letti il "
                                                            + DateTimeFormatter.ofPattern(
                                                                            "dd/MM/yyyy HH:mm:ss")
                                                                    .withZone(
                                                                            ZoneId.systemDefault())
                                                                    .format(loaded.capturedAt()));
                                            Product before = selected;
                                            selected = null;
                                            originalInputs = null;
                                            calculating = false;
                                            filter(offset);
                                            Product next =
                                                    before == null
                                                            ? null
                                                            : catalogue.products().stream()
                                                                    .filter(
                                                                            p ->
                                                                                    p.id()
                                                                                            == before
                                                                                                    .id())
                                                                    .findFirst()
                                                                    .orElse(null);
                                            if (next != null) select(next);
                                            else if (!view.products.getItems().isEmpty())
                                                select(view.products.getItems().getFirst());
                                            else {
                                                view.product(null, Parameters.empty());
                                                view.articles.getItems().clear();
                                                showArticle(null);
                                                controls();
                                            }
                                        }));
    }

    private List<Product> filtered() {
        if (catalogue == null) return List.of();
        String search = view.search.getText().strip().toUpperCase(Locale.ROOT);
        Long line = view.line.getValue() == null ? null : view.line.getValue().id();
        return catalogue.products().stream()
                .filter(p -> view.archived.isSelected() || p.active())
                .filter(p -> line == null || line.equals(p.lineId()))
                .filter(
                        p ->
                                (p.code()
                                                + " "
                                                + p.lineCode()
                                                + " "
                                                + p.lineName()
                                                + " "
                                                + p.color())
                                        .toUpperCase(Locale.ROOT)
                                        .contains(search))
                .toList();
    }

    private void filter(int start) {
        if (catalogue == null || disposed) return;
        List<Product> rows = filtered();
        offset =
                Math.min(
                        start,
                        rows.isEmpty()
                                ? 0
                                : ((rows.size() - 1) / ProductCosts.PAGE_SIZE)
                                        * ProductCosts.PAGE_SIZE);
        rendering = true;
        try {
            view.products
                    .getItems()
                    .setAll(
                            rows.subList(
                                    offset,
                                    Math.min(offset + ProductCosts.PAGE_SIZE, rows.size())));
            restoreProduct();
        } finally {
            rendering = false;
        }
        view.count(offset, view.products.getItems().size(), rows.size());
    }

    private void restoreProduct() {
        boolean before = rendering;
        rendering = true;
        selectionEpoch++;
        try {
            view.products.getSelectionModel().clearSelection();
            if (selected != null)
                view.products.getItems().stream()
                        .filter(p -> p.id() == selected.id())
                        .findFirst()
                        .ifPresent(view.products.getSelectionModel()::select);
        } finally {
            rendering = before;
        }
    }

    private void select(Product product) {
        rendering = true;
        try {
            view.articles.getItems().clear();
        } finally {
            rendering = false;
        }
        showArticle(null);
        selected = product;
        baseline = catalogue.parameters(product.id());
        resetInputs();
        restoreProduct();
    }

    private void resetInputs() {
        rendering = true;
        try {
            overrides.clear();
            baseline.overrides()
                    .forEach((id, value) -> overrides.put(id, PriceFormats.edit(value)));
            view.product(selected, baseline);
            originalInputs = inputs();
        } finally {
            rendering = false;
        }
        recalculate();
    }

    private Inputs inputs() {
        return new Inputs(view.commonCost.getText(), overrides);
    }

    private boolean dirty() {
        return originalInputs != null && !originalInputs.equals(inputs());
    }

    private Parameters draft() {
        var values = new HashMap<Long, BigDecimal>();
        overrides.forEach(
                (id, text) -> {
                    BigDecimal value = PriceFormats.decimal(text, "Trasformazione specifica");
                    if (value == null)
                        throw new IllegalArgumentException(
                                "Compila la trasformazione specifica o disattiva l'eccezione.");
                    values.put(id, value);
                });
        return new Parameters(
                baseline.version(),
                PriceFormats.decimal(view.commonCost.getText(), "Trasformazione comune"),
                values);
    }

    private void edited() {
        if (rendering || disposed) return;
        calculationEpoch++;
        calculating = true;
        view.costsPending(true);
        controls();
        debounce.playFromStart();
    }

    private void recalculate() {
        if (disposed || catalogue == null || selected == null || loading || saving) return;
        debounce.stop();
        long epoch = ++calculationEpoch;
        Parameters parameters;
        try {
            parameters = draft();
        } catch (IllegalArgumentException invalid) {
            calculating = false;
            view.costsPending(true);
            view.feedback(
                    "Anteprima sospesa · "
                            + invalid.getMessage()
                            + " I valori in tabella non sono aggiornati.",
                    true);
            controls();
            return;
        }
        Result row = view.articles.getSelectionModel().getSelectedItem();
        Long article = row == null || row.article() == null ? null : row.article().id();
        Long spec = row == null || row.specification() == null ? null : row.specification().id();
        calculating = true;
        view.costsPending(true);
        controls();
        view.feedback("Calcolo in corso…", false);
        service.calculate(
                        catalogue,
                        List.of(selected.id()),
                        Map.of(selected.id(), parameters),
                        view.alternatives.isSelected(),
                        view.archived.isSelected())
                .whenComplete(
                        (results, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed || epoch != calculationEpoch) return;
                                            calculating = false;
                                            if (error != null) {
                                                view.feedback(message(error), true);
                                                controls();
                                                return;
                                            }
                                            view.costsPending(false);
                                            rendering = true;
                                            try {
                                                view.results(results, article, spec);
                                            } finally {
                                                rendering = false;
                                            }
                                            showArticle(
                                                    view.articles
                                                            .getSelectionModel()
                                                            .getSelectedItem());
                                            view.feedback(
                                                    dirty()
                                                            ? "Anteprima aggiornata · Salva i"
                                                                    + " parametri per abilitare"
                                                                    + " l'esportazione."
                                                            : "Costi aggiornati · "
                                                                    + results.stream()
                                                                            .filter(
                                                                                    r ->
                                                                                            r.problems()
                                                                                                    .isEmpty())
                                                                            .count()
                                                                    + " configurazioni complete su "
                                                                    + results.size()
                                                                    + ".",
                                                    false);
                                            controls();
                                        }));
    }

    private void showArticle(Result row) {
        rendering = true;
        try {
            view.article(row);
            Long id = row == null || row.article() == null ? null : row.article().id();
            view.specific.setSelected(id != null && overrides.containsKey(id));
            view.articleCost.setText(
                    id == null ? "" : overrides.getOrDefault(id, view.commonCost.getText()));
        } finally {
            rendering = false;
        }
        controls();
    }

    private void save() {
        if (disposed || saving || loading || selected == null || !dirty()) return;
        Parameters parameters;
        try {
            parameters = draft();
        } catch (IllegalArgumentException invalid) {
            view.feedback(invalid.getMessage(), true);
            return;
        }
        saving = true;
        calculationEpoch++;
        debounce.stop();
        controls();
        view.feedback("Salvataggio parametri…", false);
        long id = selected.id();
        service.save(id, parameters)
                .whenComplete(
                        (saved, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed) return;
                                            saving = false;
                                            calculating = false;
                                            if (error != null) {
                                                view.feedback(message(error), true);
                                                controls();
                                                return;
                                            }
                                            baseline = saved;
                                            catalogue = catalogue.withParameters(id, saved);
                                            originalInputs = inputs();
                                            view.revision(
                                                    "Parametri di trasformazione · revisione "
                                                            + saved.version());
                                            recalculate();
                                        }));
    }

    private void export(int kind) {
        if (disposed
                || catalogue == null
                || saving
                || loading
                || exporting
                || calculating
                || dirty()) return;
        Catalogue snapshot = catalogue;
        List<Long> ids =
                kind == 0
                        ? snapshot.products().stream().map(Product::id).toList()
                        : kind == 3
                                ? filtered().stream().map(Product::id).toList()
                                : selected == null ? List.of() : List.of(selected.id());
        if (ids.isEmpty()) {
            view.feedback("Nessun prodotto da esportare.", true);
            return;
        }
        boolean all = kind == 0;
        boolean alternatives = !all && view.alternatives.isSelected();
        boolean archived = all || view.archived.isSelected();
        exporting = true;
        controls();
        view.feedback("Preparazione dell'anteprima CSV…", false);
        service.calculate(snapshot, ids, Map.of(), alternatives, archived)
                .whenComplete(
                        (rows, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed) return;
                                            exporting = false;
                                            controls();
                                            if (error != null) {
                                                view.feedback(message(error), true);
                                                return;
                                            }
                                            String scope =
                                                    all
                                                            ? "Intero catalogo · Incluse archiviate"
                                                                  + " · Configurazioni predefinite"
                                                                  + " · "
                                                                    + ids.size()
                                                                    + " prodotti"
                                                            : kind == 3
                                                                    ? "Tutti i prodotti filtrati · "
                                                                            + ids.size()
                                                                            + " prodotti"
                                                                    : "Prodotto selezionato · "
                                                                            + rows.getFirst()
                                                                                    .product()
                                                                                    .code();
                                            if (kind == 2)
                                                CsvExportDialog.show(
                                                        window,
                                                        "Esporta dettaglio polveri",
                                                        scope,
                                                        "dettaglio-polveri.csv",
                                                        ProductCostCsv.details(rows),
                                                        ProductCostCsv.detail(
                                                                snapshot.capturedAt()),
                                                        workers);
                                            else
                                                CsvExportDialog.show(
                                                        window,
                                                        "Esporta costi prodotti",
                                                        scope,
                                                        all
                                                                ? "costi-tutti-prodotti.csv"
                                                                : "costi-prodotti.csv",
                                                        rows,
                                                        ProductCostCsv.summary(
                                                                snapshot.capturedAt()),
                                                        workers);
                                            view.feedback(
                                                    "Anteprima pronta. Scegli Salva CSV per creare"
                                                            + " il file.",
                                                    false);
                                        }));
    }

    private void controls() {
        boolean blocked = loading || saving || exporting;
        boolean valid = selected != null;
        try {
            if (valid) draft();
        } catch (IllegalArgumentException invalid) {
            valid = false;
        }
        view.formEnabled(!blocked && selected != null);
        Result row = view.articles.getSelectionModel().getSelectedItem();
        view.specific.setDisable(blocked || row == null || row.article() == null);
        view.articleCost.setDisable(view.specific.isDisabled() || !view.specific.isSelected());
        view.save.setDisable(blocked || !dirty() || !valid);
        view.cancel.setDisable(blocked || !dirty());
        view.refresh.setDisable(blocked);
        view.home.setDisable(saving);
        view.products.setDisable(blocked);
        view.search.setDisable(blocked);
        view.line.setDisable(blocked);
        view.archived.setDisable(blocked);
        view.export.setDisable(blocked || calculating || dirty() || catalogue == null);
        view.exportAll.setDisable(
                blocked
                        || calculating
                        || dirty()
                        || catalogue == null
                        || catalogue.products().isEmpty());
        view.exportProduct.setDisable(selected == null);
        view.exportDetail.setDisable(selected == null);
    }

    public boolean canLeave() {
        if (saving) return false;
        if (!dirty()) return true;
        Alert alert =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Ci sono parametri non salvati. Vuoi scartarli e proseguire?",
                        ButtonType.CANCEL,
                        ButtonType.OK);
        alert.setTitle("Modifiche ai costi");
        alert.setHeaderText(null);
        if (window != null) alert.initOwner(window);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void attachScene(Scene before, Scene after) {
        if (before != null) before.windowProperty().removeListener(windowChanged);
        if (after != null) after.windowProperty().addListener(windowChanged);
        attachWindow(after == null ? null : after.getWindow());
    }

    private void attachWindow(Window next) {
        if (window != null) window.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closing);
        window = next;
        if (window != null) window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closing);
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        return error.getMessage() == null
                ? "Operazione non riuscita. Ricarica i dati prima di riprovare."
                : error.getMessage();
    }

    public void dispose() {
        disposed = true;
        readEpoch++;
        calculationEpoch++;
        selectionEpoch++;
        debounce.stop();
        attachScene(view.getScene(), null);
        view.sceneProperty().removeListener(sceneChanged);
    }
}

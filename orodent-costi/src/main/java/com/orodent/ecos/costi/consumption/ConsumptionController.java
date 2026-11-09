package com.orodent.ecos.costi.consumption;

import com.orodent.ecos.costi.consumption.Consumption.*;
import com.orodent.ecos.costi.view.PriceFormats;
import com.orodent.ecos.module.csv.CsvExportDialog;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

public final class ConsumptionController {
    private final ConsumptionView view;
    private final ConsumptionService service;
    private final Executor workers;
    private Analysis analysis;
    private Map<Long, BigDecimal> required;
    private List<Contribution> details = List.of();
    private boolean busy, saving, disposed;
    private long epoch, planEpoch;
    private Window window;
    private final ChangeListener<Window> windowChanged = (o, b, a) -> attachWindow(a);
    private final ChangeListener<Scene> sceneChanged = (o, b, a) -> attachScene(b, a);
    private final EventHandler<WindowEvent> closing =
            e -> {
                if (!canLeave()) e.consume();
            };

    public ConsumptionController(
            ConsumptionView view, ConsumptionService service, Executor workers, Runnable home) {
        this.view = view;
        this.service = service;
        this.workers = workers;
        view.refresh.setOnAction(
                e -> {
                    if (canLeave()) start();
                });
        view.home.setOnAction(
                e -> {
                    if (canLeave()) home.run();
                });
        view.basis.setOnAction(e -> render());
        view.planBasis.setOnAction(e -> renderPlan());
        view.metric.setOnAction(e -> render());
        view.colors.values().forEach(c -> c.setOnAction(e -> render()));
        view.from.getEditor().textProperty().addListener((o, b, a) -> invalidate());
        view.through.getEditor().textProperty().addListener((o, b, a) -> invalidate());
        view.from.valueProperty().addListener((o, b, a) -> invalidate());
        view.through.valueProperty().addListener((o, b, a) -> invalidate());
        view.horizon.valueProperty().addListener((o, b, a) -> invalidate());
        view.calendar.setOnAction(e -> invalidate());
        view.calculate.setOnAction(e -> calculate());
        view.onPlanChanged(
                () -> {
                    required = null;
                    planEpoch++;
                    view.planned.getItems().clear();
                    view.contributions.getItems().clear();
                    details = List.of();
                    view.autonomy("Piano modificato: ricalcola il fabbisogno.");
                    controls();
                });
        view.exportPlanDetails.setOnAction(
                e -> {
                    if (analysis != null && required != null)
                        CsvExportDialog.show(
                                window,
                                "Dettaglio piano",
                                "Quantità per articolo e strato",
                                "dettaglio-piano.csv",
                                details,
                                ConsumptionCsv.contributions(),
                                workers);
                });
        view.minimum.setOnAction(e -> minimum());
        view.export.setOnAction(e -> export(false));
        view.exportDaily.setOnAction(e -> export(true));
        view.tabs.getSelectionModel().selectedIndexProperty().addListener((o, b, a) -> controls());
        view.sceneProperty().addListener(sceneChanged);
        attachScene(null, view.getScene());
        controls();
    }

    public void start() {
        if (disposed || busy) return;
        Request request;
        try {
            view.from.commitValue();
            view.through.commitValue();
            request =
                    new Request(
                            view.from.getValue(),
                            view.through.getValue(),
                            LocalDate.now(),
                            view.horizon.getValue(),
                            view.calendar.getValue());
        } catch (RuntimeException invalid) {
            view.feedback(invalid.getMessage());
            return;
        }
        busy = true;
        long intent = ++epoch;
        planEpoch++;
        controls();
        view.feedback("Lettura dei dati e ricostruzione dei consumi…");
        service.load(request)
                .whenComplete(
                        (loaded, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed || intent != epoch) return;
                                            busy = false;
                                            if (error != null) {
                                                view.feedback(message(error));
                                                controls();
                                                return;
                                            }
                                            analysis = loaded;
                                            details = List.of();
                                            view.contributions.getItems().clear();
                                            view.planned.getItems().clear();
                                            view.autonomy(
                                                    "Calcola un ordine per vedere l’autonomia"
                                                            + " residua");
                                            required = null;
                                            view.choices(loaded.snapshot());
                                            view.legend(loaded.snapshot(), this::render);
                                            render();
                                            view.feedback(
                                                    "Dati aggiornati · Nessun movimento di"
                                                            + " magazzino effettuato.");
                                            controls();
                                        }));
    }

    private void invalidate() {
        if (disposed) return;
        epoch++;
        planEpoch++;
        busy = false;
        analysis = null;
        required = null;
        view.chart.show(null, List.of(), view.metric.getValue());
        view.balances.getItems().clear();
        view.planned.getItems().clear();
        view.contributions.getItems().clear();
        details = List.of();
        view.feedback("Periodo o calendario modificato: premi Carica / aggiorna.");
        controls();
    }

    private List<Series> selected(boolean visible) {
        if (analysis == null) return List.of();
        return analysis.series().stream()
                .filter(
                        s ->
                                view.basis.getValue() == Basis.CONFRONTO
                                        || s.basis() == view.basis.getValue())
                .filter(s -> !visible || view.visible(s.powder()))
                .toList();
    }

    private void render() {
        if (analysis == null) return;
        List<Series> visible = selected(true);
        view.chart.show(analysis, visible, view.metric.getValue());
        view.balances
                .getItems()
                .setAll(
                        visible.stream()
                                .map(
                                        s ->
                                                Consumption.balance(
                                                        s,
                                                        analysis.request().today(),
                                                        analysis.request().calendar(),
                                                        s.projected()))
                                .toList());
        view.warnings(analysis.warnings());
        StringBuilder totals = new StringBuilder();
        for (Basis basis : List.of(Basis.PRODUZIONI, Basis.VENDITE)) {
            List<Series> group = selected(false).stream().filter(s -> s.basis() == basis).toList();
            if (group.isEmpty()) continue;
            boolean complete = group.stream().allMatch(Series::complete);
            BigDecimal total =
                    group.stream()
                            .map(Series::projected)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            totals.append(basis)
                    .append(": ")
                    .append(
                            complete
                                    ? PriceFormats.number(total, 0, 3) + " kg previsti"
                                    : "previsione non determinabile / incompleta")
                    .append(" · ");
        }
        view.summary(
                totals
                        + "Le due previsioni sono alternative e non vengono sommate.\n"
                        + "Linea continua: produzioni · Linea tratteggiata: vendite.");
        renderPlan();
        controls();
    }

    private void calculate() {
        if (analysis == null || busy) return;
        List<PlanLine> lines;
        try {
            lines = view.plan();
        } catch (RuntimeException e) {
            view.feedback(e.getMessage());
            return;
        }
        busy = true;
        long intent = ++planEpoch;
        Analysis captured = analysis;
        controls();
        service.plan(captured.snapshot(), lines)
                .whenComplete(
                        (amount, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed || intent != planEpoch) return;
                                            busy = false;
                                            if (error != null) {
                                                view.feedback(message(error));
                                                controls();
                                                return;
                                            }
                                            required = amount.required();
                                            details = amount.details();
                                            view.contributions.getItems().setAll(details);
                                            renderPlan();
                                            view.feedback(
                                                    "Simulazione aggiornata · Disponibilità"
                                                            + " dichiarate invariate.");
                                            controls();
                                        }));
    }

    private void renderPlan() {
        if (required == null || analysis == null) return;
        List<Balance> all =
                analysis.series().stream()
                        .filter(s -> s.basis() == view.planBasis.getValue())
                        .filter(
                                s ->
                                        required.containsKey(s.powder().id())
                                                || s.rate() != null && s.rate().signum() > 0)
                        .map(
                                s ->
                                        Consumption.balance(
                                                s,
                                                analysis.request().today(),
                                                analysis.request().calendar(),
                                                required.getOrDefault(
                                                        s.powder().id(), BigDecimal.ZERO)))
                        .toList();
        view.planned.getItems().setAll(all);
        if (all.isEmpty()) {
            view.autonomy("Nessuna polvere richiesta.");
            return;
        }
        long unknown =
                all.stream().filter(b -> b.residual() == null || b.afterDays() == null).count();
        Optional<Balance> shortage =
                all.stream()
                        .filter(b -> b.residual() != null && b.residual().signum() < 0)
                        .findFirst();
        Optional<Balance> minimum =
                all.stream()
                        .filter(b -> b.afterDays() != null)
                        .min(Comparator.comparing(Balance::afterDays));
        String text =
                shortage.isPresent()
                        ? "Polveri insufficienti per l'ordine: " + shortage.get().powder().code()
                        : minimum.map(
                                        b ->
                                                "Dopo l'ordine: "
                                                        + PriceFormats.number(b.afterDays(), 0, 1)
                                                        + " giorni · Polvere limitante: "
                                                        + b.powder().code()
                                                        + " · "
                                                        + b.basis())
                                .orElse("Autonomia non determinabile");
        if (unknown > 0)
            text += " · Copertura incompleta: " + unknown + " valori non determinabili";
        view.autonomy(text + " (mix abituale e polveri dell’ordine, anche nascoste)");
    }

    private void minimum() {
        if (analysis == null || busy) return;
        Balance row = view.balances.getSelectionModel().getSelectedItem();
        if (row == null) {
            view.feedback("Seleziona una polvere nella tabella per impostare la scorta minima.");
            return;
        }
        Powder powder = row.powder();
        Dialog<Settings> dialog = new Dialog<>();
        if (window != null) dialog.initOwner(window);
        dialog.setTitle("Scorta minima · " + powder.code());
        ComboBox<Unit> unit = new ComboBox<>();
        unit.getItems().setAll(Unit.values());
        unit.setValue(powder.settings() == null ? Unit.KG : powder.settings().unit());
        TextField amount =
                new TextField(
                        powder.settings() == null
                                ? "0"
                                : PriceFormats.edit(powder.settings().value()));
        Label error = new Label();
        dialog.getDialogPane()
                .setContent(
                        new VBox(
                                10,
                                new Label(
                                        "KG: quantità fissa. GIORNI: consumo medio della fonte e"
                                                + " del calendario selezionati."),
                                unit,
                                amount,
                                error));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK))
                .addEventFilter(
                        javafx.event.ActionEvent.ACTION,
                        e -> {
                            try {
                                new Settings(
                                        0,
                                        unit.getValue(),
                                        PriceFormats.decimal(amount.getText(), "Scorta minima"));
                            } catch (RuntimeException invalid) {
                                error.setText(
                                        "Inserisci un valore non negativo, massimo 3 decimali.");
                                e.consume();
                            }
                        });
        dialog.setResultConverter(
                b ->
                        b == ButtonType.OK
                                ? new Settings(
                                        powder.settings() == null ? 0 : powder.settings().version(),
                                        unit.getValue(),
                                        PriceFormats.decimal(amount.getText(), "Scorta minima"))
                                : null);
        dialog.showAndWait()
                .ifPresent(
                        value -> {
                            saving = true;
                            busy = true;
                            controls();
                            service.save(powder.id(), value)
                                    .whenComplete(
                                            (saved, failure) ->
                                                    Platform.runLater(
                                                            () -> {
                                                                if (disposed) return;
                                                                saving = false;
                                                                busy = false;
                                                                if (failure != null) {
                                                                    view.feedback(message(failure));
                                                                    controls();
                                                                    return;
                                                                }
                                                                var updated =
                                                                        new Powder(
                                                                                powder.id(),
                                                                                powder.code(),
                                                                                powder.name(),
                                                                                powder.color(),
                                                                                powder.stock(),
                                                                                powder.stockDate(),
                                                                                saved);
                                                                Snapshot old = analysis.snapshot();
                                                                Snapshot snapshot =
                                                                        new Snapshot(
                                                                                old.catalogue(),
                                                                                old
                                                                                        .powders()
                                                                                        .stream()
                                                                                        .map(
                                                                                                p ->
                                                                                                        p
                                                                                                                                .id()
                                                                                                                        == updated
                                                                                                                                .id()
                                                                                                                ? updated
                                                                                                                : p)
                                                                                        .toList(),
                                                                                old.movements(),
                                                                                old.warnings(),
                                                                                old.approved());
                                                                analysis =
                                                                        new Analysis(
                                                                                snapshot,
                                                                                analysis.request(),
                                                                                analysis
                                                                                        .series()
                                                                                        .stream()
                                                                                        .map(
                                                                                                s ->
                                                                                                        s.powder()
                                                                                                                                .id()
                                                                                                                        == updated
                                                                                                                                .id()
                                                                                                                ? new Series(
                                                                                                                        updated,
                                                                                                                        s
                                                                                                                                .basis(),
                                                                                                                        s
                                                                                                                                .daily(),
                                                                                                                        s
                                                                                                                                .rate(),
                                                                                                                        s
                                                                                                                                .projected(),
                                                                                                                        s
                                                                                                                                .complete())
                                                                                                                : s)
                                                                                        .toList(),
                                                                                analysis
                                                                                        .warnings());
                                                                render();
                                                                view.feedback(
                                                                        "Scorta minima salvata ·"
                                                                                + " revisione "
                                                                                + saved.version());
                                                            }));
                        });
    }

    private void export(boolean daily) {
        if (analysis == null || busy) return;
        if (daily) {
            Analysis captured = analysis;
            List<Series> visible = selected(true);
            long intent = epoch;
            busy = true;
            controls();
            CompletableFuture.supplyAsync(() -> ConsumptionCsv.points(captured, visible), workers)
                    .whenComplete(
                            (rows, error) ->
                                    Platform.runLater(
                                            () -> {
                                                if (disposed || intent != epoch) return;
                                                busy = false;
                                                controls();
                                                if (error != null) {
                                                    view.feedback(message(error));
                                                    return;
                                                }
                                                CsvExportDialog.show(
                                                        window,
                                                        "Serie giornaliere",
                                                        "Polveri e fonti visibili al momento della"
                                                                + " richiesta · valori teorici e"
                                                                + " stimati",
                                                        "consumi-giornalieri.csv",
                                                        rows,
                                                        ConsumptionCsv.points(),
                                                        workers);
                                            }));
        } else {
            boolean plan = view.tabs.getSelectionModel().getSelectedIndex() == 1;
            var rows = List.copyOf(plan ? view.planned.getItems() : view.balances.getItems());
            CsvExportDialog.show(
                    window,
                    "Consumi e fabbisogni",
                    plan ? "Simulazione produzione" : "Previsione globale",
                    "fabbisogni-polveri.csv",
                    rows,
                    ConsumptionCsv.balances(analysis, plan ? "SIMULAZIONE" : "PREVISIONE"),
                    workers);
        }
    }

    private void controls() {
        view.refresh.setDisable(busy);
        view.calculate.setDisable(busy || analysis == null);
        view.minimum.setDisable(busy || analysis == null);
        view.add.setDisable(busy || analysis == null || !view.hasChoices());
        view.planRows.setDisable(busy);
        view.from.setDisable(saving);
        view.through.setDisable(saving);
        view.calendar.setDisable(saving);
        view.horizon.setDisable(saving);
        view.export.setDisable(
                busy
                        || analysis == null
                        || view.tabs.getSelectionModel().getSelectedIndex() == 1
                                && required == null);
        view.exportDaily.setDisable(busy || analysis == null);
        view.exportPlanDetails.setDisable(busy || analysis == null || required == null);
        view.home.setDisable(saving);
    }

    public boolean canLeave() {
        if (saving) return false;
        if (!view.hasPlan()) return true;
        Alert alert =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Il piano è una simulazione non salvata. Vuoi scartarlo e proseguire?",
                        ButtonType.CANCEL,
                        ButtonType.OK);
        if (window != null) alert.initOwner(window);
        alert.setHeaderText(null);
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

    public void dispose() {
        disposed = true;
        epoch++;
        planEpoch++;
        attachScene(view.getScene(), null);
        view.sceneProperty().removeListener(sceneChanged);
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        return Objects.toString(error.getMessage(), "Operazione non riuscita.");
    }
}

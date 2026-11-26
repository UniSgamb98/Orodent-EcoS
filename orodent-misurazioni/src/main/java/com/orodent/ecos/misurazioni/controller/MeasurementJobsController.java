package com.orodent.ecos.misurazioni.controller;

import static com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.*;

import com.orodent.ecos.misurazioni.config.MeasurementSettings;
import com.orodent.ecos.misurazioni.devices.DeviceConnections;
import com.orodent.ecos.misurazioni.jobs.service.MeasurementJobsService;
import com.orodent.ecos.misurazioni.jobs.view.MeasurementJobsView;
import com.orodent.ecos.misurazioni.model.*;
import com.orodent.ecos.module.*;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.util.*;
import java.util.concurrent.*;

/** Edits stay local until the operator saves one transactional snapshot, then prints. */
public final class MeasurementJobsController implements ModuleSession {
    final MeasurementJobsView view = new MeasurementJobsView();
    final MeasurementsController acquisition;
    private final ModuleContext context;
    private final MeasurementJobsService service;
    private Detail baseline;

    @FunctionalInterface
    interface PrintSessionFactory {
        AutoCloseable open(Key key, Runnable onClosed);
    }

    private final PrintSessionFactory printSessions;
    private AutoCloseable labelPrinting;
    private Window window;
    private boolean printing, closed, saving, unsaved, completed;
    private final EventHandler<WindowEvent> closeRequest =
            e -> {
                if (!closed && !canLeave()) e.consume();
            };
    private final ChangeListener<Window> windowListener = (o, b, a) -> attachWindow(a);
    private final ChangeListener<Scene> sceneListener =
            (o, b, a) -> {
                if (b != null) b.windowProperty().removeListener(windowListener);
                if (a != null) a.windowProperty().addListener(windowListener);
                attachWindow(a == null ? null : a.getWindow());
            };
    private long request;

    public MeasurementJobsController(ModuleContext context, MeasurementSettings settings) {
        this(
                context,
                new MeasurementJobsService(context.connections(), context.workers()),
                DeviceConnections.fromSettings(settings));
    }

    MeasurementJobsController(
            ModuleContext context,
            MeasurementJobsService service,
            DeviceConnections.Factory factory) {
        this(context, service, factory, null);
    }

    MeasurementJobsController(
            ModuleContext context,
            MeasurementJobsService service,
            DeviceConnections.Factory factory,
            PrintSessionFactory printSessions) {
        this.context = context;
        this.service = service;
        this.printSessions =
                printSessions == null
                        ? (key, done) ->
                                new com.orodent.ecos.misurazioni.labels.controller
                                        .LabelPrintingController(
                                        context,
                                        new com.orodent.ecos.misurazioni.labels.service
                                                .LabelService(
                                                context.connections(), context.workers()),
                                        view.getScene().getWindow(),
                                        key,
                                        done)
                        : printSessions;
        view.sceneProperty().addListener(sceneListener);
        acquisition = new MeasurementsController(context.showHome(), factory);
        acquisition.view.embedded();
        acquisition.onChanged(
                values -> {
                    if (baseline != null) {
                        unsaved =
                                !values.equals(
                                        baseline.job().readings().stream()
                                                .map(Reading::value)
                                                .toList());
                        view.remaining(
                                values.stream()
                                        .filter(
                                                r ->
                                                        r.micrometer().isBlank()
                                                                || r.scale().isBlank())
                                        .count());
                        view.saveStatus(unsaved ? "Modifiche non salvate" : status(), false);
                    }
                });
        acquisition.onConnectionState(
                (instrument, state) -> {
                    view.connection(instrument, state);
                    view.saving(saving || printing || acquisition.reading());
                });
        acquisition.view.reset.setOnAction(
                e -> {
                    if (baseline != null
                            && confirm(
                                    "Azzera le misure di questo lavoro? Le revisioni precedenti"
                                            + " rimangono conservate."))
                        acquisition.clearReadings();
                });
        view.home.setOnAction(
                e -> {
                    if (canLeave()) context.showHome().run();
                });
        view.printLabel.setOnAction(e -> openPrint(false));
        view.saveAndPrint.setOnAction(e -> save());
        view.pending.setOnAction(e -> queue(false));
        view.completed.setOnAction(e -> queue(true));
        for (var instrument : Instrument.values())
            view.stateButton(instrument).setOnAction(e -> acquisition.toggleConnection(instrument));
        view.refresh.setOnAction(e -> refresh());
        view.back.setOnAction(e -> queue(completed));
        view.retry.setOnAction(e -> save());
        view.reload.setOnAction(
                e -> {
                    if (baseline != null && canLeave()) open(baseline.job().key());
                });
        view.onOpen(
                () -> {
                    var job = view.selected();
                    if (job != null) open(job.key());
                });
        refresh();
    }

    private void queue(boolean complete) {
        if (!canLeave()) return;
        baseline = null;
        unsaved = false;
        completed = complete;
        acquisition.persistenceState(false, false);
        view.queue(complete);
        refresh();
    }

    private void refresh() {
        if (closed || saving || printing) return;
        long token = ++request;
        view.busy(true);
        view.feedback.setText("Caricamento lavori…");
        service.list()
                .whenComplete(
                        (jobs, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (closed || token != request) return;
                                            view.busy(false);
                                            if (error != null) {
                                                view.feedback.setText(message(error));
                                                return;
                                            }
                                            view.show(jobs, completed);
                                            view.feedback.setText("");
                                        }));
    }

    private void open(Key key) {
        if (closed || saving || printing || acquisition.reading()) return;
        long token = ++request;
        view.busy(true);
        service.load(key)
                .whenComplete(
                        (detail, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (closed || token != request) return;
                                            view.busy(false);
                                            if (error != null) {
                                                view.feedback.setText(message(error));
                                                if (baseline != null)
                                                    view.saveStatus(message(error), true);
                                                return;
                                            }
                                            baseline = detail;
                                            unsaved = false;
                                            acquisition
                                                    .view
                                                    .list
                                                    .getSelectionModel()
                                                    .clearSelection();
                                            acquisition.restore(
                                                    detail.job().readings().stream()
                                                            .map(Reading::value)
                                                            .toList());
                                            acquisition.persistenceState(false, false);
                                            view.showJob(detail.job(), acquisition.view);
                                            view.saveStatus(status(), false);
                                            Platform.runLater(
                                                    () -> {
                                                        if (!closed && token == request)
                                                            acquisition.focusAcquisition();
                                                    });
                                        }));
    }

    private void save() {
        if (closed || saving || printing || baseline == null || acquisition.reading()) return;
        var values = acquisition.readings();
        var expected = baseline;
        var operator =
                context.operators()
                        .current()
                        .map(o -> new Operator(o.id().toString(), o.username()))
                        .orElse(null);
        saving = true;
        view.saving(true);
        view.saveStatus("Salvataggio misure…", false);
        acquisition.persistenceState(true, true);
        service.save(expected, values, operator)
                .whenComplete(
                        (detail, error) ->
                                Platform.runLater(
                                        () -> {
                                            saving = false;
                                            if (closed) return;
                                            view.saving(false);
                                            if (error != null) {
                                                unsaved = true;
                                                acquisition.persistenceState(false, false);
                                                view.saveStatus(
                                                        "Misure non salvate: " + message(error),
                                                        true);
                                                return;
                                            }
                                            baseline = detail;
                                            unsaved = false;
                                            view.remaining(detail.job().remaining());
                                            var canonical =
                                                    detail.job().readings().stream()
                                                            .map(Reading::value)
                                                            .toList();
                                            if (!canonical.equals(values))
                                                acquisition.restore(canonical);
                                            acquisition.persistenceState(false, false);
                                            view.saveStatus(status(), false);
                                            openPrint(true);
                                        }));
    }

    private void openPrint(boolean returnToJobs) {
        if (closed || saving || printing || baseline == null || acquisition.reading()) return;
        printing = true;
        view.saving(true);
        acquisition.persistenceState(true, false);
        try {
            labelPrinting =
                    printSessions.open(
                            baseline.job().key(),
                            () -> {
                                printing = false;
                                labelPrinting = null;
                                if (closed) return;
                                acquisition.persistenceState(false, false);
                                view.saving(false);
                                if (returnToJobs) queue(completed);
                                else Platform.runLater(acquisition::focusAcquisition);
                            });
        } catch (RuntimeException failure) {
            printing = false;
            acquisition.persistenceState(false, false);
            view.saving(false);
            view.saveStatus("Impossibile aprire la stampa: " + message(failure), true);
        }
    }

    private String status() {
        return "Misure salvate · "
                + baseline.job().done()
                + " / "
                + baseline.job().readings().size()
                + " dischi con entrambe le letture · "
                + baseline.job().remaining()
                + " da completare";
    }

    private boolean canLeave() {
        if (saving || printing || acquisition.reading()) return false;
        return !unsaved
                || com.orodent.ecos.module.view.UnsavedChanges.confirmDiscard(
                        view.getScene() == null ? null : view.getScene().getWindow());
    }

    private boolean confirm(String text) {
        var dialog = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO);
        dialog.setTitle("Misurazioni");
        dialog.setHeaderText(null);
        if (view.getScene() != null) dialog.initOwner(view.getScene().getWindow());
        return dialog.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void attachWindow(Window next) {
        if (window != null)
            window.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequest);
        window = next;
        if (window != null) window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeRequest);
    }

    private String message(Throwable failure) {
        while (failure instanceof CompletionException || failure instanceof ExecutionException)
            failure = failure.getCause();
        return failure instanceof IllegalArgumentException || failure instanceof Conflict
                ? failure.getMessage()
                : "Impossibile completare l’operazione. Riprova o aggiorna i lavori.";
    }

    @Override
    public Parent root() {
        return view;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        view.sceneProperty().removeListener(sceneListener);
        if (view.getScene() != null)
            view.getScene().windowProperty().removeListener(windowListener);
        attachWindow(null);
        if (labelPrinting != null)
            try {
                labelPrinting.close();
            } catch (Exception ignored) {
            }
        request++;
        acquisition.close(); /* In-flight database writes are intentionally allowed to finish. */
    }
}

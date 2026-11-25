package com.orodent.ecos.misurazioni.controller;

import static com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.*;

import com.orodent.ecos.misurazioni.config.MeasurementSettings;
import com.orodent.ecos.misurazioni.devices.DeviceConnections;
import com.orodent.ecos.misurazioni.jobs.service.MeasurementJobsService;
import com.orodent.ecos.misurazioni.jobs.view.MeasurementJobsView;
import com.orodent.ecos.misurazioni.model.*;
import com.orodent.ecos.module.*;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.*;

import java.util.*;
import java.util.concurrent.*;

/** Autosaves acknowledged snapshots before another capture or navigation can change the job. */
public final class MeasurementJobsController implements ModuleSession {
    final MeasurementJobsView view = new MeasurementJobsView();
    final MeasurementsController acquisition;
    private final ModuleContext context;
    private final MeasurementJobsService service;
    private Detail baseline;
    private com.orodent.ecos.misurazioni.labels.controller.LabelPrintingController labelPrinting;
    private boolean closed, saving, unsaved, completed;
    private long request;

    public MeasurementJobsController(ModuleContext context, MeasurementSettings settings) {
        this(
                context,
                new MeasurementJobsService(context.connections(), context.workers()),
                DeviceConnections.fromSettings(settings),
                "Micrometro: "
                        + settings.micrometer().host()
                        + ":"
                        + settings.micrometer().port()
                        + "\nBilancia: "
                        + settings.scale().host()
                        + ":"
                        + settings.scale().port());
    }

    MeasurementJobsController(
            ModuleContext context,
            MeasurementJobsService service,
            DeviceConnections.Factory factory,
            String endpoints) {
        this.context = context;
        this.service = service;
        acquisition = new MeasurementsController(context.showHome(), factory);
        acquisition.view.embedded();
        acquisition.onChanged(
                values -> {
                    if (baseline != null) {
                        unsaved = true;
                        save();
                    }
                });
        acquisition.onConnectionState(
                (instrument, state) -> {
                    view.connection(instrument, state);
                    view.saving(saving || acquisition.reading());
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
        view.printLabel.setOnAction(
                e -> {
                    if (baseline == null || saving || acquisition.reading()) return;
                    if (labelPrinting != null) labelPrinting.close();
                    labelPrinting =
                            new com.orodent.ecos.misurazioni.labels.controller
                                    .LabelPrintingController(
                                    context,
                                    new com.orodent.ecos.misurazioni.labels.service.LabelService(
                                            context.connections(), context.workers()),
                                    view.getScene().getWindow(),
                                    baseline.job().key(),
                                    () -> {
                                        if (!closed)
                                            Platform.runLater(acquisition::focusAcquisition);
                                    });
                });
        view.pending.setOnAction(e -> queue(false));
        view.completed.setOnAction(e -> queue(true));
        view.tools.setOnAction(
                e -> {
                    if (canLeave()) {
                        request++;
                        baseline = null;
                        unsaved = false;
                        acquisition.persistenceState(false, false);
                        view.tools(endpoints, acquisition::toggleConnection);
                    }
                });
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
        if (closed || saving) return;
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
        if (closed || saving || acquisition.reading()) return;
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
                                            view.showJob(
                                                    detail.job(),
                                                    detail.ruleLabel(),
                                                    acquisition.root());
                                            view.saveStatus(status(), false);
                                            Platform.runLater(
                                                    () -> {
                                                        if (!closed && token == request)
                                                            acquisition.focusAcquisition();
                                                    });
                                        }));
    }

    private void save() {
        if (closed || saving || baseline == null) return;
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
                                                acquisition.persistenceState(false, true);
                                                view.saveStatus(
                                                        "Misure non salvate: " + message(error),
                                                        true);
                                                return;
                                            }
                                            baseline = detail;
                                            unsaved = false;
                                            acquisition.restore(
                                                    detail.job().readings().stream()
                                                            .map(Reading::value)
                                                            .toList());
                                            acquisition.persistenceState(false, false);
                                            view.saveStatus(status(), false);
                                        }));
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
        if (saving || acquisition.reading()) return false;
        return !unsaved
                || confirm("Alcune misure non sono state salvate. Vuoi scartarle e proseguire?");
    }

    private boolean confirm(String text) {
        var dialog = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO);
        dialog.setTitle("Misurazioni");
        dialog.setHeaderText(null);
        if (view.getScene() != null) dialog.initOwner(view.getScene().getWindow());
        return dialog.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
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
        if (labelPrinting != null) labelPrinting.close();
        request++;
        acquisition.close(); /* In-flight database writes are intentionally allowed to finish. */
    }
}

package com.orodent.ecos.misurazioni.controller;

import static com.orodent.ecos.misurazioni.model.Instrument.*;

import com.orodent.ecos.misurazioni.config.MeasurementSettings;
import com.orodent.ecos.misurazioni.devices.DeviceConnections;
import com.orodent.ecos.misurazioni.model.Instrument;
import com.orodent.ecos.misurazioni.model.MeasurementBatch;
import com.orodent.ecos.misurazioni.view.MeasurementsView;
import com.orodent.ecos.module.ModuleSession;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Window;

public final class MeasurementsController implements ModuleSession {
    final MeasurementsView view = new MeasurementsView();
    private final MeasurementBatch batch = new MeasurementBatch();
    private final DeviceConnections connections;
    private final PedalInput pedal;
    private final ChangeListener<Boolean> focusListener;
    private final ChangeListener<Window> windowListener;
    private final ChangeListener<Scene> sceneListener;
    private Window window;
    private Instrument active = MICROMETER;
    private int batchGeneration;
    private boolean closed;

    public MeasurementsController(Runnable home, MeasurementSettings settings) {
        this(home, DeviceConnections.fromSettings(settings));
    }

    MeasurementsController(Runnable home, DeviceConnections.Factory factory) {
        connections = new DeviceConnections(factory, Platform::runLater, this::updateState);
        for (var instrument : Instrument.values())
            view.connectionButton(instrument).setOnAction(e -> toggle(instrument));
        view.home.setOnAction(e -> home.run());
        view.acquire.setOnAction(e -> acquire());
        view.swap.setOnAction(e -> swap());
        view.reset.setOnAction(
                e -> {
                    batchGeneration++;
                    batch.reset();
                    refreshRows(-1);
                    view.feedback.setText("");
                });
        view.onRemove(
                (index, instrument) -> {
                    batch.clearValue(index, instrument);
                    refreshRows(index);
                });
        view.copy.setOnAction(
                e -> {
                    var content = new ClipboardContent();
                    content.putString(batch.clipboardText());
                    Clipboard.getSystemClipboard().setContent(content);
                    view.feedback.setText("Misure copiate negli appunti.");
                });
        pedal = new PedalInput(view, this::acquire, this::swap);
        focusListener =
                (o, was, focused) -> {
                    if (!focused) pedal.cancel();
                };
        windowListener = (o, was, current) -> attachWindow(current);
        sceneListener =
                (o, was, scene) -> {
                    if (was != null) was.windowProperty().removeListener(windowListener);
                    if (scene != null) scene.windowProperty().addListener(windowListener);
                    attachWindow(scene == null ? null : scene.getWindow());
                    pedal.cancel();
                };
        view.sceneProperty().addListener(sceneListener);
        refreshRows(-1);
        for (var instrument : Instrument.values()) toggle(instrument);
    }

    private void toggle(Instrument instrument) {
        connections.toggle(
                instrument,
                failure -> {
                    view.feedback.setText(
                            instrument.label()
                                    + ": collegamento non riuscito. Clicca sullo stato per"
                                    + " riprovare.");
                    view.connectionButton(instrument).getTooltip().setText(message(failure));
                });
    }

    private void acquire() {
        var instrument = active;
        int generation = batchGeneration;
        connections.read(
                instrument,
                value -> {
                    if (generation != batchGeneration) return;
                    try {
                        refreshRows(batch.acquire(instrument, value));
                        view.feedback.setText("");
                    } catch (IllegalArgumentException invalid) {
                        view.feedback.setText(message(invalid));
                    }
                },
                failure ->
                        view.feedback.setText(
                                instrument.label()
                                        + ": "
                                        + message(failure)
                                        + " Ricollega lo strumento per riprovare."));
    }

    private void swap() {
        active = active == MICROMETER ? SCALE : MICROMETER;
        updateState();
    }

    private void refreshRows(int index) {
        view.list.getItems().setAll(batch.rows());
        if (index >= 0) {
            view.list.getSelectionModel().select(index);
            view.list.scrollTo(index);
        }
        view.copy.setDisable(batch.rows().isEmpty());
        updateState();
    }

    private void updateState() {
        for (var instrument : Instrument.values()) {
            var state = connections.state(instrument);
            view.connectionState(instrument, state.connected(), state.busy());
        }
        view.active(active, connections.state(active).ready());
    }

    private static String message(Exception failure) {
        return failure.getMessage() == null
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
    }

    private void attachWindow(Window current) {
        if (window != null) window.focusedProperty().removeListener(focusListener);
        window = current;
        if (window != null) window.focusedProperty().addListener(focusListener);
    }

    @Override
    public Parent root() {
        return view;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        pedal.close();
        view.sceneProperty().removeListener(sceneListener);
        if (view.getScene() != null)
            view.getScene().windowProperty().removeListener(windowListener);
        attachWindow(null);
        connections.close();
    }
}

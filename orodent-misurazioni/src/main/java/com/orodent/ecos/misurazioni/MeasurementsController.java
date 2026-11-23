package com.orodent.ecos.misurazioni;

import static com.orodent.ecos.misurazioni.MeasurementBatch.Instrument.*;

import com.orodent.ecos.module.ModuleSession;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Window;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MeasurementsController implements ModuleSession {
    interface DeviceFactory {
        MeasurementDevice connect(MeasurementBatch.Instrument instrument) throws Exception;
    }

    private static final class Slot {
        MeasurementDevice device;
        boolean busy;
    }

    final MeasurementsView view = new MeasurementsView();
    private final MeasurementBatch batch = new MeasurementBatch();
    private final Map<MeasurementBatch.Instrument, Slot> slots =
            new EnumMap<>(MeasurementBatch.Instrument.class);
    private final ExecutorService io =
            Executors.newFixedThreadPool(
                    2, Thread.ofVirtual().name("misurazioni-io-", 0).factory());
    private final DeviceFactory factory;
    private final PedalInput pedal;
    private final ChangeListener<Boolean> focusListener;
    private final ChangeListener<Window> windowListener;
    private final ChangeListener<Scene> sceneListener;
    private Window window;
    private MeasurementBatch.Instrument active = MICROMETER;
    private int batchGeneration;
    private boolean closed;

    MeasurementsController(Runnable home, MeasurementSettings settings) {
        this(
                home,
                instrument ->
                        instrument == MICROMETER
                                ? new KeyenceDevice(settings.micrometer(), settings.timeoutMs())
                                : new ScaleDevice(settings.scale(), settings.timeoutMs()));
    }

    MeasurementsController(Runnable home, DeviceFactory factory) {
        this.factory = factory;
        for (var instrument : MeasurementBatch.Instrument.values()) {
            slots.put(instrument, new Slot());
            view.connectionButton(instrument).setOnAction(e -> toggle(instrument));
        }
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
        for (var instrument : MeasurementBatch.Instrument.values()) toggle(instrument);
    }

    private void toggle(MeasurementBatch.Instrument instrument) {
        if (closed) return;
        Slot slot = slots.get(instrument);
        if (slot.busy) return;
        if (slot.device != null) {
            var previous = slot.device;
            slot.device = null;
            closeAsync(previous);
            updateState();
            return;
        }
        slot.busy = true;
        updateState();
        io.execute(
                () -> {
                    try {
                        var device = factory.connect(instrument);
                        Platform.runLater(
                                () -> {
                                    if (closed) {
                                        closeAsync(device);
                                        return;
                                    }
                                    slot.device = device;
                                    slot.busy = false;
                                    updateState();
                                });
                    } catch (Exception failure) {
                        Platform.runLater(
                                () -> {
                                    if (closed) return;
                                    slot.busy = false;
                                    view.feedback.setText(
                                            instrument.label
                                                    + ": collegamento non riuscito. Clicca sullo"
                                                    + " stato per riprovare.");
                                    view.connectionButton(instrument)
                                            .getTooltip()
                                            .setText(message(failure));
                                    updateState();
                                });
                    }
                });
    }

    private void acquire() {
        if (closed) return;
        var instrument = active;
        Slot slot = slots.get(instrument);
        if (slot.device == null || slot.busy) return;
        var device = slot.device;
        int generation = batchGeneration;
        slot.busy = true;
        updateState();
        io.execute(
                () -> {
                    try {
                        String value = device.measure();
                        Platform.runLater(
                                () -> {
                                    if (closed) return;
                                    slot.busy = false;
                                    if (generation == batchGeneration) {
                                        try {
                                            refreshRows(batch.acquire(instrument, value));
                                            view.feedback.setText("");
                                        } catch (IllegalArgumentException invalid) {
                                            view.feedback.setText(message(invalid));
                                        }
                                    }
                                    updateState();
                                });
                    } catch (Exception failure) {
                        device.close();
                        Platform.runLater(
                                () -> {
                                    if (closed) return;
                                    slot.device = null;
                                    slot.busy = false;
                                    view.feedback.setText(
                                            instrument.label
                                                    + ": "
                                                    + message(failure)
                                                    + " Ricollega lo strumento per riprovare.");
                                    updateState();
                                });
                    }
                });
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
        slots.forEach(
                (instrument, slot) ->
                        view.connectionState(instrument, slot.device != null, slot.busy));
        var current = slots.get(active);
        view.active(active, current.device != null && !current.busy);
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

    private static void closeAsync(MeasurementDevice device) {
        Thread.ofVirtual().name("misurazioni-close").start(device::close);
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
        slots.values()
                .forEach(
                        slot -> {
                            if (slot.device != null) closeAsync(slot.device);
                        });
        io.shutdownNow();
    }
}

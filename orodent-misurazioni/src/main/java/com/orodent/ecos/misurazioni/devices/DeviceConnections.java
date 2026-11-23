package com.orodent.ecos.misurazioni.devices;

import com.orodent.ecos.misurazioni.config.MeasurementSettings;
import com.orodent.ecos.misurazioni.model.Instrument;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Owns instrument connections and background IO without depending on JavaFX controls. Methods and
 * dispatched callbacks run on the same owner thread.
 */
public final class DeviceConnections implements AutoCloseable {
    @FunctionalInterface
    public interface Factory {
        MeasurementDevice connect(Instrument instrument) throws Exception;
    }

    public record State(boolean connected, boolean busy) {
        public boolean ready() {
            return connected && !busy;
        }
    }

    private static final class Slot {
        MeasurementDevice device;
        boolean busy;
    }

    private final Map<Instrument, Slot> slots = new EnumMap<>(Instrument.class);
    private final ExecutorService io =
            Executors.newFixedThreadPool(
                    2, Thread.ofVirtual().name("misurazioni-io-", 0).factory());
    private final Factory factory;
    private final Executor callbacks;
    private final Runnable changed;
    private boolean closed;

    public DeviceConnections(Factory factory, Executor callbacks, Runnable changed) {
        this.factory = factory;
        this.callbacks = callbacks;
        this.changed = changed;
        for (var instrument : Instrument.values()) slots.put(instrument, new Slot());
    }

    public static Factory fromSettings(MeasurementSettings settings) {
        return instrument ->
                switch (instrument) {
                    case MICROMETER ->
                            new KeyenceDevice(settings.micrometer(), settings.timeoutMs());
                    case SCALE -> new ScaleDevice(settings.scale(), settings.timeoutMs());
                };
    }

    public State state(Instrument instrument) {
        var slot = slots.get(instrument);
        return new State(slot.device != null, slot.busy);
    }

    public void toggle(Instrument instrument, Consumer<Exception> failed) {
        if (closed) return;
        Slot slot = slots.get(instrument);
        if (slot.busy) return;
        if (slot.device != null) {
            var previous = slot.device;
            slot.device = null;
            closeAsync(previous);
            changed.run();
            return;
        }
        slot.busy = true;
        changed.run();
        io.execute(
                () -> {
                    try {
                        var device = factory.connect(instrument);
                        callbacks.execute(
                                () -> {
                                    if (closed) {
                                        closeAsync(device);
                                        return;
                                    }
                                    slot.device = device;
                                    slot.busy = false;
                                    changed.run();
                                });
                    } catch (Exception failure) {
                        callbacks.execute(
                                () -> {
                                    if (closed) return;
                                    slot.busy = false;
                                    failed.accept(failure);
                                    changed.run();
                                });
                    }
                });
    }

    public void read(Instrument instrument, Consumer<String> measured, Consumer<Exception> failed) {
        if (closed) return;
        Slot slot = slots.get(instrument);
        if (slot.device == null || slot.busy) return;
        var device = slot.device;
        slot.busy = true;
        changed.run();
        io.execute(
                () -> {
                    try {
                        String value = device.measure();
                        callbacks.execute(
                                () -> {
                                    if (closed) return;
                                    slot.busy = false;
                                    measured.accept(value);
                                    changed.run();
                                });
                    } catch (Exception failure) {
                        device.close();
                        callbacks.execute(
                                () -> {
                                    if (closed) return;
                                    slot.device = null;
                                    slot.busy = false;
                                    failed.accept(failure);
                                    changed.run();
                                });
                    }
                });
    }

    private static void closeAsync(MeasurementDevice device) {
        Thread.ofVirtual().name("misurazioni-close").start(device::close);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        slots.values()
                .forEach(
                        slot -> {
                            if (slot.device != null) closeAsync(slot.device);
                        });
        io.shutdownNow();
    }
}

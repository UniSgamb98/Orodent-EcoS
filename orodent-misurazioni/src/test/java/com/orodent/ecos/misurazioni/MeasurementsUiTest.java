package com.orodent.ecos.misurazioni;

import static com.orodent.ecos.misurazioni.MeasurementBatch.Instrument.*;

import static org.junit.jupiter.api.Assertions.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.*;
import javafx.stage.Stage;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Tag("javafx")
class MeasurementsUiTest {
    private static final class FakeInstrument implements MeasurementDevice {
        final String value;
        final AtomicBoolean closed = new AtomicBoolean();
        volatile CountDownLatch started, release;

        FakeInstrument(String value) {
            this.value = value;
        }

        @Override
        public String measure() throws Exception {
            if (started != null) {
                started.countDown();
                release.await();
            }
            return value;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    @Test
    void acquisitionPairingClipboardPedalAndSessionCleanupWorkInTheNativeView() throws Exception {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyStarted) {
        }
        Platform.setImplicitExit(false);
        var micrometer = new FakeInstrument("98,123\t12,456");
        var scale = new FakeInstrument("123,45");
        var session =
                fx(
                        () ->
                                new MeasurementsController(
                                        () -> {},
                                        instrument ->
                                                instrument == MICROMETER ? micrometer : scale));
        Stage stage =
                fx(
                        () -> {
                            var result = new Stage();
                            result.setScene(new Scene(session.root(), 960, 780));
                            result.show();
                            session.view.requestFocus();
                            return result;
                        });
        try {
            await(() -> !session.view.acquire.isDisabled());
            fx(() -> session.view.acquire.fire());
            await(
                    () ->
                            session.view.list.getItems().size() == 1
                                    && !session.view.acquire.isDisabled());
            fx(() -> session.view.swap.fire());
            await(() -> !session.view.acquire.isDisabled());
            fx(() -> session.view.acquire.fire());
            await(() -> !session.view.list.getItems().getFirst().scale().isEmpty());
            fx(
                    () -> {
                        assertEquals(1, session.view.list.getItems().size());
                        session.view.copy.fire();
                        assertEquals(
                                "98,123\t12,456\t123,45",
                                Clipboard.getSystemClipboard().getString());
                        session.view.applyCss();
                        session.view.layout();
                        Button clear =
                                session.view.list.lookupAll(".button").stream()
                                        .filter(
                                                node ->
                                                        "Cancella misura micrometro"
                                                                .equals(node.getAccessibleText()))
                                        .map(Button.class::cast)
                                        .findFirst()
                                        .orElseThrow();
                        clear.fire();
                        assertEquals("", session.view.list.getItems().getFirst().micrometer());
                        assertEquals("123,45", session.view.list.getItems().getFirst().scale());
                        session.view.swap.fire();
                        session.view.acquire.fire();
                    });
            await(
                    () ->
                            !session.view.acquire.isDisabled()
                                    && !session.view
                                            .list
                                            .getItems()
                                            .getFirst()
                                            .micrometer()
                                            .isEmpty());
            fx(
                    () -> {
                        assertEquals(1, session.view.list.getItems().size());
                        session.view.acquire.fire();
                    });
            await(
                    () ->
                            session.view.list.getItems().size() == 2
                                    && !session.view.acquire.isDisabled());
            fx(
                    () -> {
                        stage.setWidth(700);
                        session.view.applyCss();
                        session.view.layout();
                        assertTrue(session.view.list.getWidth() >= 300);
                        screenshot(session.view, Path.of("target/misurazioni.png"));
                        session.view.reset.fire();
                        assertTrue(session.view.list.getItems().isEmpty());
                        assertTrue(session.view.copy.isDisabled());
                        key(session, KeyEvent.KEY_PRESSED);
                        key(session, KeyEvent.KEY_RELEASED);
                    });
            await(
                    () ->
                            session.view.list.getItems().size() == 1
                                    && !session.view.acquire.isDisabled());
            fx(
                    () -> {
                        key(session, KeyEvent.KEY_PRESSED);
                        key(session, KeyEvent.KEY_PRESSED); // OS repeat must not switch twice.
                    });
            await(() -> session.view.acquire.getText().contains("bilancia"));
            fx(
                    () -> {
                        key(session, KeyEvent.KEY_RELEASED);
                        assertEquals("", session.view.list.getItems().getFirst().scale());
                        session.view.swap.fire();
                        micrometer.started = new CountDownLatch(1);
                        micrometer.release = new CountDownLatch(1);
                        session.view.acquire.fire();
                    });
            assertTrue(micrometer.started.await(3, TimeUnit.SECONDS));
            fx(() -> session.view.reset.fire()); // Must run even while acquisition is blocked.
            micrometer.release.countDown();
            await(() -> !session.view.acquire.isDisabled());
            fx(
                    () ->
                            assertTrue(
                                    session.view.list.getItems().isEmpty(),
                                    "Reset must discard a pending capture"));
        } finally {
            fx(
                    () -> {
                        session.close();
                        stage.close();
                    });
        }
        await(() -> micrometer.closed.get() && scale.closed.get());

        // A connection completing after navigation must immediately release its instrument.
        var late = new CompletableFuture<MeasurementDevice>();
        var connecting = new CountDownLatch(2);
        var closedDevice = new FakeInstrument("1");
        var pending =
                fx(
                        () ->
                                new MeasurementsController(
                                        () -> {},
                                        instrument -> {
                                            connecting.countDown();
                                            return late.join();
                                        }));
        assertTrue(connecting.await(3, TimeUnit.SECONDS));
        fx(pending::close);
        late.complete(closedDevice);
        await(closedDevice.closed::get);

        var unavailable = new AtomicBoolean(true);
        var reconnected = new FakeInstrument("98,100\t12,000");
        var offline =
                fx(
                        () ->
                                new MeasurementsController(
                                        () -> {},
                                        instrument -> {
                                            if (unavailable.get())
                                                throw new java.io.IOException("Strumento spento");
                                            return reconnected;
                                        }));
        try {
            await(
                    () ->
                            offline.view
                                            .connectionButton(MICROMETER)
                                            .getAccessibleText()
                                            .contains("scollegato")
                                    && offline.view
                                            .connectionButton(SCALE)
                                            .getAccessibleText()
                                            .contains("scollegato"));
            fx(
                    () -> {
                        assertTrue(offline.view.acquire.isDisabled());
                        assertFalse(offline.view.home.isDisabled());
                        unavailable.set(false);
                        offline.view.connectionButton(MICROMETER).fire();
                    });
            await(() -> !offline.view.acquire.isDisabled());
            fx(() -> offline.view.acquire.fire());
            await(() -> offline.view.list.getItems().size() == 1);
        } finally {
            fx(offline::close);
        }
        await(reconnected.closed::get);
    }

    private static void key(
            MeasurementsController controller, javafx.event.EventType<KeyEvent> type) {
        controller
                .root()
                .fireEvent(new KeyEvent(type, "", "", KeyCode.ENTER, false, false, false, false));
    }

    private static void screenshot(MeasurementsView view, Path file) throws Exception {
        var image = view.snapshot(null, null);
        var buffered =
                new java.awt.image.BufferedImage(
                        (int) image.getWidth(),
                        (int) image.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < buffered.getHeight(); y++)
            for (int x = 0; x < buffered.getWidth(); x++)
                buffered.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(buffered, "png", file.toFile());
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            if (fx(condition)) return;
            Thread.sleep(25);
        }
        fail("Timed out waiting for the measurements UI");
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        result.complete(action.call());
                    } catch (Throwable failure) {
                        result.completeExceptionally(failure);
                    }
                });
        return result.get(10, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private static void fx(Action action) throws Exception {
        fx(
                () -> {
                    action.run();
                    return null;
                });
    }
}

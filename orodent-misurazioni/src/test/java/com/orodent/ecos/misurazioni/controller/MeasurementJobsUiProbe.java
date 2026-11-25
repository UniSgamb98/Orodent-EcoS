package com.orodent.ecos.misurazioni.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.*;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.misurazioni.devices.*;
import com.orodent.ecos.misurazioni.jobs.service.*;
import com.orodent.ecos.misurazioni.model.Instrument;
import com.orodent.ecos.module.*;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.*;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class MeasurementJobsUiProbe {
    Stage stage;
    MeasurementJobsController session;
    ConnectionProvider db;
    ExecutorService workers;
    final List<Throwable> errors = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            new MeasurementJobsUiProbe().run();
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    void run() throws Exception {
        String url = "jdbc:derby:memory:jobs_ui_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        MeasurementJobsFixture.seed(db);
        workers = Executors.newFixedThreadPool(2);
        Platform.startup(() -> {});
        Platform.setImplicitExit(false);
        try {
            fx(
                    () -> {
                        Thread.currentThread()
                                .setUncaughtExceptionHandler(
                                        (t, e) -> {
                                            errors.add(e);
                                            e.printStackTrace();
                                        });
                        stage = new Stage();
                        openSession();
                        return null;
                    });
            await(
                    () ->
                            session.view.table.getItems().size() == 5
                                    && !session.view.table.isDisabled());
            fx(
                    () -> {
                        screenshot("target/measurement-jobs.png");
                        session.view.search.setText("ZR9818");
                        assertEquals(2, session.view.table.getItems().size());
                        session.view.search.clear();
                        session.view.table.getSelectionModel().select(1);
                        session.view.table.fireEvent(enter());
                        return null;
                    });
            await(
                    () ->
                            session.acquisition.view.list.getScene() != null
                                    && !session.acquisition.view.acquire.isDisabled());
            fx(
                    () -> {
                        assertSame(
                                session.acquisition.root(),
                                stage.getScene().getFocusOwner(),
                                "Il focus iniziale deve appartenere alla schermata di"
                                    + " acquisizione");
                        assertNull(
                                session.view.getLeft(),
                                "Nessuna barra laterale nel singolo lavoro");
                        var menu = stage.getScene().getRoot().lookup("#jobs-menu");
                        assertFalse(menu.isVisible());
                        assertFalse(menu.isManaged());
                        return null;
                    });
            sql(
                    "ALTER TABLE TON.DISCO_MISURE_REVISIONI ADD CONSTRAINT reject_ui_save"
                        + " CHECK(numero_revisione<0)");
            fx(
                    () -> {
                        assertEquals(3, session.acquisition.view.list.getItems().size());
                        return null;
                    });
            shortEnter();
            await(() -> session.view.retry.isVisible() && !session.view.retry.isDisabled());
            fx(
                    () -> {
                        assertEquals(
                                "98,100\t14,200",
                                session.acquisition.view.list.getItems().getFirst().micrometer());
                        assertTrue(session.acquisition.view.acquire.isDisabled());
                        assertTrue(session.view.saveState.getText().contains("non salvate"));
                        return null;
                    });
            sql("ALTER TABLE TON.DISCO_MISURE_REVISIONI DROP CONSTRAINT reject_ui_save");
            fx(
                    () -> {
                        session.view.retry.fire();
                        return null;
                    });
            await(this::saved);
            fx(
                    () -> {
                        assertEquals(
                                "98,100\t14,200",
                                session.acquisition.view.list.getItems().getFirst().micrometer());
                        session.view.back.fire();
                        return null;
                    });
            await(() -> session.view.table.getScene() != null && !session.view.table.isDisabled());
            fx(
                    () -> {
                        assertNotNull(session.view.getLeft());
                        assertTrue(stage.getScene().getRoot().lookup("#jobs-menu").isVisible());
                        assertTrue(session.view.table.getItems().get(1).job().touched());
                        assertEquals(0, session.view.table.getItems().get(1).job().done());
                        session.close();
                        openSession();
                        return null;
                    });
            await(
                    () ->
                            session.view.table.getItems().size() == 5
                                    && !session.view.table.isDisabled());
            fx(
                    () -> {
                        session.view.table.getSelectionModel().select(1);
                        layout();
                        var row =
                                session.view.table.lookupAll(".table-row-cell").stream()
                                        .filter(
                                                n ->
                                                        n instanceof TableRow<?> r
                                                                && r.getItem()
                                                                        == session.view
                                                                                .table
                                                                                .getSelectionModel()
                                                                                .getSelectedItem())
                                        .findFirst()
                                        .orElseThrow();
                        row.fireEvent(
                                new MouseEvent(
                                        MouseEvent.MOUSE_CLICKED,
                                        0,
                                        0,
                                        0,
                                        0,
                                        MouseButton.PRIMARY,
                                        2,
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        null));
                        return null;
                    });
            await(
                    () ->
                            session.acquisition.view.list.getScene() != null
                                    && !session.acquisition.view.acquire.isDisabled());
            fx(
                    () -> {
                        assertEquals(
                                "98,100\t14,200",
                                session.acquisition.view.list.getItems().getFirst().micrometer());
                        assertEquals(
                                "", session.acquisition.view.list.getItems().getFirst().scale());
                        assertSame(session.acquisition.root(), stage.getScene().getFocusOwner());
                        new javafx.scene.robot.Robot().keyPress(KeyCode.ENTER);
                        return null;
                    });
            await(() -> session.acquisition.view.acquire.getText().contains("bilancia"));
            fx(
                    () -> {
                        new javafx.scene.robot.Robot().keyRelease(KeyCode.ENTER);
                        return null;
                    });
            fx(
                    () -> {
                        assertEquals(
                                "",
                                session.acquisition.view.list.getItems().getFirst().scale(),
                                "Invio prolungato cambia strumento senza acquisire");
                        return null;
                    });
            shortEnter();
            await(this::saved);
            fx(
                    () -> {
                        assertSame(session.acquisition.root(), stage.getScene().getFocusOwner());
                        assertTrue(session.view.saveState.getText().contains("1 / 3"));
                        screenshot("target/measurement-job-resumed.png");
                        session.acquisition.view.acquire.fire();
                        return null;
                    });
            await(this::saved);
            fx(
                    () -> {
                        session.acquisition.view.swap.fire();
                        session.acquisition.view.acquire.fire();
                        return null;
                    });
            await(this::saved);
            fx(
                    () -> {
                        session.acquisition.view.acquire.fire();
                        return null;
                    });
            await(this::saved);
            fx(
                    () -> {
                        session.acquisition.view.swap.fire();
                        session.acquisition.view.acquire.fire();
                        return null;
                    });
            await(this::saved);
            fx(
                    () -> {
                        assertTrue(session.view.saveState.getText().contains("3 / 3"));
                        Platform.runLater(() -> answer(ButtonType.NO));
                        session.acquisition.view.reset.fire();
                        assertEquals(
                                "330,20",
                                session.acquisition.view.list.getItems().getFirst().scale());
                        session.view.back.fire();
                        return null;
                    });
            await(() -> session.view.table.getScene() != null && !session.view.table.isDisabled());
            fx(
                    () -> {
                        assertEquals(4, session.view.table.getItems().size());
                        session.view.completed.fire();
                        return null;
                    });
            await(
                    () ->
                            session.view.table.getItems().size() == 2
                                    && !session.view.table.isDisabled());
            fx(
                    () -> {
                        assertTrue(session.view.table.getItems().get(1).job().complete());
                        session.view.table.getSelectionModel().select(1);
                        session.view.open.fire();
                        return null;
                    });
            await(
                    () ->
                            session.acquisition.view.list.getScene() != null
                                    && !session.acquisition.view.acquire.isDisabled());
            fx(
                    () -> {
                        assertSame(session.acquisition.root(), stage.getScene().getFocusOwner());
                        assertNull(session.view.getLeft());
                        assertEquals(3, session.acquisition.view.list.getItems().size());
                        stage.setWidth(1050);
                        stage.setHeight(780);
                        return null;
                    });
            await(() -> stage.getWidth() <= 1050);
            fx(
                    () -> {
                        screenshot("target/measurement-job-small.png");
                        session.view.back.fire();
                        return null;
                    });
            await(() -> session.view.table.getScene() != null && !session.view.table.isDisabled());
            fx(
                    () -> {
                        session.view.pending.fire();
                        return null;
                    });
            await(
                    () ->
                            session.view.table.getItems().size() == 4
                                    && !session.view.table.isDisabled());
            fx(
                    () -> {
                        screenshot("target/measurement-jobs-small.png");
                        return null;
                    });
            assertTrue(errors.isEmpty(), errors::toString);
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        if (stage != null) stage.close();
                        return null;
                    });
            workers.shutdown();
            workers.awaitTermination(15, TimeUnit.SECONDS);
            Platform.exit();
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException ignored) {
            }
        }
    }

    void shortEnter() throws Exception {
        fx(
                () -> {
                    var robot = new javafx.scene.robot.Robot();
                    robot.keyPress(KeyCode.ENTER);
                    robot.keyRelease(KeyCode.ENTER);
                    return null;
                });
    }

    boolean saved() {
        return !session.acquisition.view.acquire.isDisabled()
                && session.view.saveState.getText().startsWith("Misure salvate");
    }

    void openSession() {
        var context =
                new ModuleContext(
                        db,
                        workers,
                        new ClientiCsvImportService(db, workers),
                        new VenditeCsvImportService(db, workers),
                        () -> {});
        session =
                new MeasurementJobsController(
                        context,
                        new MeasurementJobsService(db, workers, MeasurementJobsFixture.CLOCK),
                        instrument ->
                                new MeasurementDevice() {
                                    public String measure() {
                                        return instrument == Instrument.MICROMETER
                                                ? "98,100\t14,200"
                                                : "330,20";
                                    }

                                    public void close() {}
                                },
                        "Strumenti simulati");
        stage.setScene(new Scene(session.root(), 1600, 1000));
        stage.getScene()
                .getStylesheets()
                .add(
                        Path.of("../orodent-app/src/main/resources/css/global.css")
                                .toUri()
                                .toString());
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    void sql(String text) {
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(text);
                    }
                    return null;
                });
    }

    void layout() {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    void screenshot(String file) throws Exception {
        layout();
        var snap = stage.getScene().snapshot(null);
        var out =
                new java.awt.image.BufferedImage(
                        (int) snap.getWidth(),
                        (int) snap.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++)
                out.setRGB(x, y, snap.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(out, "png", Path.of(file).toFile());
    }

    void answer(ButtonType type) {
        var pane =
                Window.getWindows().stream()
                        .filter(Window::isShowing)
                        .filter(w -> w.getScene() != null)
                        .map(w -> w.getScene().getRoot())
                        .filter(DialogPane.class::isInstance)
                        .map(DialogPane.class::cast)
                        .findFirst()
                        .orElseThrow();
        ((Button) pane.lookupButton(type)).fire();
    }

    KeyEvent enter() {
        return new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false);
    }

    <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<T>(action);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    void await(BooleanSupplier check) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < end) {
            if (fx(check::getAsBoolean)) return;
            Thread.sleep(40);
        }
        throw new AssertionError("Timed out in measurement jobs UI");
    }
}

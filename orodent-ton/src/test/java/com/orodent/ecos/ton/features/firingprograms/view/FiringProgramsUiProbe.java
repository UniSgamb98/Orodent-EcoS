package com.orodent.ecos.ton.features.firingprograms.view;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.*;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;
import com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms.*;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.*;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class FiringProgramsUiProbe {
    Stage stage;
    ModuleSession session;
    final List<Throwable> errors = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            new FiringProgramsUiProbe().run();
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    void run() throws Exception {
        String url =
                "jdbc:derby:memory:firing_programs_ui_"
                        + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException failure) {
                        throw new IllegalStateException(failure);
                    }
                };
        new DatabaseSchema(db).initialize();
        var workers = Executors.newFixedThreadPool(2);
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
                        session =
                                new TonModule()
                                        .open(
                                                new ModuleContext(
                                                        db,
                                                        workers,
                                                        new ClientiCsvImportService(db, workers),
                                                        new VenditeCsvImportService(db, workers),
                                                        () -> {}));
                        stage = new Stage();
                        stage.setScene(new Scene(session.root(), 1720, 1040));
                        stage.getScene()
                                .getStylesheets()
                                .add(
                                        Path.of("../orodent-app/src/main/resources/css/global.css")
                                                .toUri()
                                                .toString());
                        stage.show();
                        click("ton-nav-firing");
                        click("ton-tech-programs-button");
                        return null;
                    });
            await(
                    () ->
                            node("ton-firing-programs-new") != null
                                    && !node("ton-firing-programs-new").isDisabled());
            fx(
                    () -> {
                        click("ton-firing-programs-new");
                        return null;
                    });
            await(
                    () ->
                            node("ton-firing-programs-code") != null
                                    && node("ton-firing-programs-editor").isVisible()
                                    && !node("ton-firing-programs-editor").isDisabled());
            fx(
                    () -> {
                        field("ton-firing-programs-code").setText("COTT-STD");
                        field("ton-firing-programs-name").setText("Ciclo standard");
                        ((TextArea) node("ton-firing-programs-description"))
                                .setText("Sinterizzazione con raffreddamento finale");
                        ((TabPane) node("ton-firing-programs-tabs")).getSelectionModel().select(1);
                        step(1, "500", "1.30", "0.05");
                        click("ton-firing-programs-add-step");
                        step(2, "1450", "2.00", "1.30");
                        click("ton-firing-programs-add-step");
                        step(3, "900", "0.45", "0.00");
                        assertTrue(duration().getText().contains("5.50"), duration().getText());
                        assertEquals(7, chart().getData().getFirst().getData().size());
                        assertEquals(
                                90L,
                                chart().getData()
                                        .getFirst()
                                        .getData()
                                        .get(1)
                                        .getXValue()
                                        .longValue());
                        assertEquals(
                                95L,
                                chart().getData()
                                        .getFirst()
                                        .getData()
                                        .get(2)
                                        .getXValue()
                                        .longValue());
                        assertEquals(
                                350L,
                                chart().getData()
                                        .getFirst()
                                        .getData()
                                        .getLast()
                                        .getXValue()
                                        .longValue());
                        field(stepId(1, "ramp")).setText("1.60");
                        assertFalse(error().getText().isEmpty());
                        assertTrue(chart().getData().isEmpty());
                        field(stepId(1, "ramp")).setText("1.30");
                        click(stepId(3, "up"));
                        assertEquals("900", field(stepId(2, "temperature")).getText());
                        click(stepId(2, "down"));
                        assertEquals("1450", field(stepId(2, "temperature")).getText());
                        click("ton-firing-programs-add-step");
                        click(stepId(4, "remove"));
                        screenshot("target/firing-program-editor.png");
                        Platform.runLater(() -> answer(ButtonType.NO));
                        click("ton-overview-button");
                        assertNotNull(node("ton-firing-programs-editor"));
                        click("ton-firing-programs-save");
                        return null;
                    });
            await(
                    () ->
                            versions().getItems().size() == 1
                                    && !node("ton-firing-programs-editor").isDisabled());
            fx(
                    () -> {
                        click("ton-firing-programs-approve");
                        return null;
                    });
            await(() -> !field(stepId(1, "temperature")).isEditable());
            fx(
                    () -> {
                        assertTrue(node("ton-firing-programs-add-step").isDisabled());
                        click("ton-firing-programs-copy");
                        return null;
                    });
            await(() -> field(stepId(1, "temperature")).isEditable());
            fx(
                    () -> {
                        field(stepId(2, "hold")).setText("1.15");
                        assertTrue(duration().getText().contains("5.35"));
                        click("ton-firing-programs-approve");
                        return null;
                    });
            await(
                    () ->
                            !field(stepId(1, "temperature")).isEditable()
                                    && versions().getItems().size() == 4);
            fx(
                    () -> {
                        ((TabPane) node("ton-firing-programs-tabs")).getSelectionModel().select(2);
                        screenshot("target/firing-program-versions.png");
                        Snapshot old =
                                versions().getItems().stream()
                                        .filter(
                                                v ->
                                                        v.number() == 1
                                                                && v.state().equals("APPROVATA"))
                                        .findFirst()
                                        .orElseThrow();
                        versions().getSelectionModel().select(old);
                        versions().fireEvent(enter());
                        return null;
                    });
            await(
                    () ->
                            field(stepId(2, "hold")).getText().equals("1.30")
                                    && !node("ton-firing-programs-editor").isDisabled());
            fx(
                    () -> {
                        assertFalse(field(stepId(2, "hold")).isEditable());
                        assertTrue(duration().getText().contains("5.50"));
                        assertTrue(field("ton-firing-programs-initial-temperature").isEditable());
                        field("ton-firing-programs-initial-temperature").setText("10");
                        assertEquals(
                                10.0,
                                chart().getData()
                                        .getFirst()
                                        .getData()
                                        .getFirst()
                                        .getYValue()
                                        .doubleValue());
                        ((TabPane) node("ton-firing-programs-tabs")).getSelectionModel().select(1);
                        stage.setWidth(1024);
                        stage.setHeight(760);
                        return null;
                    });
            await(() -> stage.getScene().getWidth() <= 1024 && stage.getScene().getHeight() <= 760);
            fx(
                    () -> {
                        screenshot("target/firing-program-editor-small.png");
                        Node cancel = node("ton-firing-programs-cancel");
                        assertTrue(
                                cancel.localToScene(cancel.getBoundsInLocal()).getMaxY()
                                        <= stage.getScene().getHeight());
                        click("ton-firing-programs-back");
                        return null;
                    });
            await(
                    () ->
                            node("ton-firing-programs-list").isVisible()
                                    && !node("ton-firing-programs-table").isDisabled());
            fx(
                    () -> {
                        var table = (TableView<?>) node("ton-firing-programs-table");
                        assertEquals(1, table.getItems().size());
                        table.getSelectionModel().selectFirst();
                        table.fireEvent(enter());
                        return null;
                    });
            await(
                    () ->
                            node("ton-firing-programs-editor").isVisible()
                                    && field(stepId(2, "hold")).getText().equals("1.15"));
            assertTrue(errors.isEmpty(), errors::toString);
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        if (stage != null) stage.close();
                        return null;
                    });
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
            Platform.exit();
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException ignored) {
            }
        }
    }

    Node node(String id) {
        return stage.getScene().getRoot().lookup("#" + id);
    }

    TextField field(String id) {
        return (TextField) node(id);
    }

    String stepId(int number, String field) {
        return "ton-firing-programs-step-" + number + "-" + field;
    }

    void step(int number, String temperature, String ramp, String hold) {
        field(stepId(number, "temperature")).setText(temperature);
        field(stepId(number, "ramp")).setText(ramp);
        field(stepId(number, "hold")).setText(hold);
    }

    Label duration() {
        return (Label) node("ton-firing-programs-duration");
    }

    Label error() {
        return (Label) node("ton-firing-programs-cycle-error");
    }

    @SuppressWarnings("unchecked")
    LineChart<Number, Number> chart() {
        return (LineChart<Number, Number>) node("ton-firing-programs-chart");
    }

    @SuppressWarnings("unchecked")
    TableView<Snapshot> versions() {
        return (TableView<Snapshot>) node("ton-firing-programs-versions-table");
    }

    void click(String id) {
        var b = (Button) Objects.requireNonNull(node(id), id);
        assertFalse(b.isDisabled(), id);
        b.fire();
    }

    KeyEvent enter() {
        return new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false);
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

    void answer(ButtonType answer) {
        var pane =
                Window.getWindows().stream()
                        .filter(Window::isShowing)
                        .filter(w -> w.getScene() != null)
                        .map(w -> w.getScene().getRoot())
                        .filter(DialogPane.class::isInstance)
                        .map(DialogPane.class::cast)
                        .findFirst()
                        .orElseThrow();
        ((Button) pane.lookupButton(answer)).fire();
    }

    <T> T fx(Callable<T> work) throws Exception {
        var task =
                new FutureTask<T>(
                        () -> {
                            if (stage != null) layout();
                            return work.call();
                        });
        Platform.runLater(task);
        return task.get(25, TimeUnit.SECONDS);
    }

    void await(BooleanSupplier check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < deadline) {
            if (fx(check::getAsBoolean)) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Firing programs UI timed out");
    }
}

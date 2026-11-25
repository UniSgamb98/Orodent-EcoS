package com.orodent.ecos.ton.features.firing.view;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;
import com.orodent.ecos.ton.features.equipment.model.Equipment;
import com.orodent.ecos.ton.features.equipment.service.EquipmentService;
import com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms;
import com.orodent.ecos.ton.features.firingprograms.service.FiringProgramService;
import com.orodent.ecos.ton.features.production.service.ProductionFixture;

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

public final class FiringUiProbe {
    Stage stage;
    ModuleSession session;
    final List<Throwable> errors = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            new FiringUiProbe().run();
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    void run() throws Exception {
        String url = "jdbc:derby:memory:firing_ui_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException failure) {
                        throw new IllegalStateException(failure);
                    }
                };
        new DatabaseSchema(db).initialize();
        var workers = Executors.newFixedThreadPool(3);
        var fixture = new ProductionFixture(db, workers);
        fixture.service.save(null, fixture.workspace(), fixture.request(false), false).join();
        new EquipmentService(db, workers)
                .save(
                        Equipment.Kind.FURNACES,
                        null,
                        new Equipment.Draft("F1", "Forno di reparto", null, "", true))
                .join();
        new EquipmentService(db, workers)
                .save(
                        Equipment.Kind.FURNACES,
                        null,
                        new Equipment.Draft("F2", "Secondo forno", null, "", true))
                .join();
        var programs = new FiringProgramService(db, workers);
        for (String code : List.of("A-CICLO", "Z-CICLO"))
            programs.save(
                            null,
                            null,
                            new FiringPrograms.Draft(
                                    code,
                                    "Sinterizzazione",
                                    "",
                                    true,
                                    List.of(
                                            new FiringPrograms.Step(
                                                    new java.math.BigDecimal("500"), 90, 5),
                                            new FiringPrograms.Step(null, 120, 60))),
                            true)
                    .join();
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
                        click("ton-tech-planning-button");
                        return null;
                    });
            await(
                    () ->
                            node("ton-firing-pending-table") != null
                                    && !node("ton-firing-editor").isDisabled());
            fx(
                    () -> {
                        assertEquals(0, table("ton-firing-pending-table").getItems().size());
                        click("ton-nav-production");
                        click("ton-tech-productions-button");
                        return null;
                    });
            await(
                    () ->
                            node("ton-productions-table") != null
                                    && table("ton-productions-table").getItems().size() == 1);
            fx(
                    () -> {
                        table("ton-productions-table").getSelectionModel().selectFirst();
                        table("ton-productions-table").fireEvent(enter());
                        return null;
                    });
            await(
                    () ->
                            node("ton-production-prepare") != null
                                    && !node("ton-production-prepare").isDisabled());
            fx(
                    () -> {
                        assertEquals(
                                "Conferma produzione",
                                ((Button) node("ton-production-prepare")).getText());
                        assertNull(node("ton-production-register"));
                        Platform.runLater(() -> answer(ButtonType.OK));
                        click("ton-production-prepare");
                        return null;
                    });
            await(
                    () ->
                            stage.getScene().getRoot().lookupAll(".production-feedback").stream()
                                    .anyMatch(
                                            n ->
                                                    n instanceof Label l
                                                            && l.getText()
                                                                    .contains(
                                                                            "Produzione"
                                                                                + " confermata")));
            fx(
                    () -> {
                        click("ton-nav-firing");
                        click("ton-tech-planning-button");
                        return null;
                    });
            await(
                    () ->
                            node("ton-firing-pending-table") != null
                                    && table("ton-firing-pending-table").getItems().size() == 3
                                    && !node("ton-firing-editor").isDisabled());
            fx(
                    () -> {
                        assertTrue(
                                table("ton-firing-pending-table").getColumns().stream()
                                        .noneMatch(c -> c.getText().equals("ID disco")));
                        var row =
                                (FiringView.GroupRow)
                                        table("ton-firing-pending-table").getItems().getFirst();
                        assertEquals(16, row.group.count());
                        row.quantity.setText("17");
                        ((ComboBox<?>) node("ton-firing-furnace"))
                                .getSelectionModel()
                                .selectFirst();
                        var choice = (ComboBox<?>) node("ton-firing-program");
                        assertNull(
                                choice.getValue(),
                                "Nessun primo programma selezionato automaticamente");
                        choice.getSelectionModel().selectLast();
                        ((TextField) node("ton-firing-final-temperature")).setText("1440");
                        ((TextField) node("ton-firing-lot")).setText("LOTTO-OPERAIO");
                        click("ton-firing-create");
                        assertTrue(((Label) node("ton-firing-feedback")).getText().contains("16"));
                        assertEquals(0, table("ton-firing-loads-table").getItems().size());
                        row.quantity.setText("3");
                        screenshot("target/firing-pending.png");
                        click("ton-firing-create");
                        return null;
                    });
            await(
                    () ->
                            table("ton-firing-pending-table").getItems().size() == 3
                                    && table("ton-firing-loads-table").getItems().size() == 1
                                    && !node("ton-firing-editor").isDisabled());
            fx(
                    () -> {
                        assertEquals(
                                13,
                                ((FiringView.GroupRow)
                                                table("ton-firing-pending-table")
                                                        .getItems()
                                                        .getFirst())
                                        .group.count());
                        var choice = (ComboBox<?>) node("ton-firing-program");
                        assertEquals(
                                "Z-CICLO", ((FiringPrograms.Program) choice.getValue()).code());
                        ((ComboBox<?>) node("ton-firing-furnace")).getSelectionModel().selectLast();
                        assertNull(
                                choice.getValue(), "La memoria dei programmi è distinta per forno");
                        ((ComboBox<?>) node("ton-firing-furnace"))
                                .getSelectionModel()
                                .selectFirst();
                        assertEquals(
                                "Z-CICLO", ((FiringPrograms.Program) choice.getValue()).code());
                        ((TabPane) node("ton-firing-tabs")).getSelectionModel().select(1);
                        table("ton-firing-loads-table").getSelectionModel().selectFirst();
                        return null;
                    });
            await(() -> table("ton-firing-loaded-table").getItems().size() == 1);
            fx(
                    () -> {
                        var loaded =
                                (com.orodent.ecos.ton.features.firing.model.Firing.Group)
                                        table("ton-firing-loaded-table").getItems().getFirst();
                        assertEquals(3, loaded.count());
                        assertEquals("ZR9814MSTA4", loaded.reference().article());
                        assertTrue(
                                ((Label) node("ton-firing-load-details"))
                                        .getText()
                                        .contains("LOTTO-OPERAIO"));
                        assertTrue(
                                ((Label) node("ton-firing-load-details"))
                                        .getText()
                                        .contains("1440"));
                        assertTrue(
                                ((Label) node("ton-firing-load-details"))
                                        .getText()
                                        .contains("Z-CICLO"));
                        screenshot("target/firing-load-history.png");
                        ((TabPane) node("ton-firing-tabs")).getSelectionModel().selectFirst();
                        stage.setWidth(1120);
                        stage.setHeight(780);
                        return null;
                    });
            await(() -> stage.getScene().getWidth() <= 1120 && stage.getScene().getHeight() <= 780);
            fx(
                    () -> {
                        screenshot("target/firing-pending-small.png");
                        assertTrue(
                                node("ton-firing-create")
                                                .localToScene(
                                                        node("ton-firing-create")
                                                                .getBoundsInLocal())
                                                .getMaxY()
                                        <= stage.getScene().getHeight());
                        return null;
                    });
            fx(
                    () -> {
                        var choice = (ComboBox<?>) node("ton-firing-program");
                        choice.getSelectionModel().selectFirst();
                        assertTrue(
                                ((Label) node("ton-firing-program-notice"))
                                        .getText()
                                        .contains("CAMBIO PROGRAMMA"));
                        ((TextField) node("ton-firing-lot")).setText("CAMBIO-UI");
                        ((TextField) node("ton-firing-final-temperature")).setText("1490");
                        ((FiringView.GroupRow)
                                        table("ton-firing-pending-table").getItems().getFirst())
                                .quantity.setText("1");
                        screenshot("target/firing-program-change.png");
                        click("ton-firing-create");
                        return null;
                    });
            await(
                    () ->
                            table("ton-firing-loads-table").getItems().size() == 2
                                    && !node("ton-firing-editor").isDisabled());
            fx(
                    () -> {
                        assertEquals(
                                "A-CICLO",
                                ((FiringPrograms.Program)
                                                ((ComboBox<?>) node("ton-firing-program"))
                                                        .getValue())
                                        .code());
                        click("ton-nav-production");
                        click("ton-nav-firing");
                        click("ton-tech-planning-button");
                        return null;
                    });
            await(
                    () ->
                            node("ton-firing-furnace") != null
                                    && !node("ton-firing-editor").isDisabled());
            fx(
                    () -> {
                        var choice = (ComboBox<?>) node("ton-firing-program");
                        assertNull(choice.getValue());
                        ((ComboBox<?>) node("ton-firing-furnace"))
                                .getSelectionModel()
                                .selectFirst();
                        assertEquals(
                                "A-CICLO", ((FiringPrograms.Program) choice.getValue()).code());
                        assertTrue(
                                ((TextField) node("ton-firing-final-temperature"))
                                        .getText()
                                        .isEmpty());
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
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
            Platform.exit();
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException ignored) {
            }
        }
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

    Node node(String id) {
        return stage.getScene().getRoot().lookup("#" + id);
    }

    TableView<?> table(String id) {
        return (TableView<?>) node(id);
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
        throw new AssertionError("Firing UI timed out");
    }
}

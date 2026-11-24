package com.orodent.ecos.ton.features.marking.view;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.*;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;
import com.orodent.ecos.ton.features.marking.model.MarkingTemplates.*;

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

public final class MarkingUiProbe {
    Stage stage;
    ModuleSession session;
    final List<Throwable> errors = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            new MarkingUiProbe().run();
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    void run() throws Exception {
        String url =
                "jdbc:derby:memory:marking_ui_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException failure) {
                        throw new IllegalStateException(failure);
                    }
                };
        new DatabaseSchema(db).initialize();
        db.withTransaction(
                c ->
                        CubatureRuleVersions.save(
                                c,
                                null,
                                null,
                                new CubatureRuleCatalogue.Draft("RITIRO", "Ritiro", "", true),
                                "D / Hmedia = fattoreRitiro",
                                true));
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
                        click("ton-nav-documents");
                        click("ton-tech-disk_marking-button");
                        return null;
                    });
            await(() -> node("ton-marking-new") != null && !node("ton-marking-new").isDisabled());
            fx(
                    () -> {
                        click("ton-marking-new");
                        return null;
                    });
            await(
                    () ->
                            node("ton-marking-code") != null
                                    && !node("ton-marking-editor").isDisabled()
                                    && node("ton-marking-editor").isVisible());
            fx(
                    () -> {
                        field("ton-marking-code").setText("DORSO-UI");
                        field("ton-marking-name").setText("Marcatura standard");
                        ((TabPane) node("ton-marking-tabs")).getSelectionModel().select(1);
                        ((ComboBox<?>) node("ton-marking-rule")).getSelectionModel().selectFirst();
                        text().setText("{codice_articolo} LOT {lotto} R {fattoreRitiro}");
                        layout();
                        var table = (TableView<?>) node("ton-marking-formats");
                        screenshot("target/marking-editor-before.png");
                        var cell =
                                table.lookupAll(".table-cell").stream()
                                        .filter(
                                                n ->
                                                        n instanceof TableCell<?, ?> c
                                                                && c.getTableRow().getItem()
                                                                        instanceof Field f
                                                                && f.name().equals("fattoreRitiro")
                                                                && c.getGraphic()
                                                                        instanceof ComboBox<?>)
                                        .map(n -> (TableCell<?, ?>) n)
                                        .findFirst()
                                        .orElseThrow();
                        ((ComboBox<Integer>) cell.getGraphic()).setValue(2);
                        assertTrue(preview().endsWith("R 6.13"), preview());
                        text().setText("{manca}");
                        assertTrue(
                                ((Label) node("ton-marking-preview-error"))
                                        .getText()
                                        .contains("manca"));
                        assertEquals("", preview());
                        text().setText("{codice_articolo} LOT {lotto} R {fattoreRitiro}");
                        screenshot("target/marking-editor.png");
                        Platform.runLater(() -> answer(ButtonType.NO));
                        click("ton-overview-button");
                        assertNotNull(node("ton-marking-editor"));
                        click("ton-marking-save");
                        return null;
                    });
            await(
                    () ->
                            versions().getItems().size() == 1
                                    && !node("ton-marking-editor").isDisabled());
            fx(
                    () -> {
                        click("ton-marking-approve");
                        return null;
                    });
            await(() -> !text().isEditable());
            fx(
                    () -> {
                        assertTrue(node("ton-marking-rule").isDisabled());
                        click("ton-marking-copy");
                        return null;
                    });
            await(() -> text().isEditable());
            fx(
                    () -> {
                        text().setText("NUOVO {codice_articolo} {fattoreRitiro}");
                        click("ton-marking-approve");
                        return null;
                    });
            await(() -> !text().isEditable() && versions().getItems().size() == 4);
            fx(
                    () -> {
                        ((TabPane) node("ton-marking-tabs")).getSelectionModel().select(2);
                        screenshot("target/marking-versions.png");
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
            await(() -> text().getText().startsWith("{codice_articolo}"));
            fx(
                    () -> {
                        assertFalse(text().isEditable());
                        assertTrue(preview().endsWith("R 6.13"));
                        ((TabPane) node("ton-marking-tabs")).getSelectionModel().select(1);
                        stage.setWidth(1024);
                        stage.setHeight(760);
                        return null;
                    });
            await(() -> stage.getScene().getWidth() <= 1024 && stage.getScene().getHeight() <= 760);
            fx(
                    () -> {
                        screenshot("target/marking-editor-small.png");
                        assertTrue(
                                node("ton-marking-cancel")
                                                .localToScene(
                                                        node("ton-marking-cancel")
                                                                .getBoundsInLocal())
                                                .getMaxY()
                                        <= stage.getScene().getHeight());
                        click("ton-marking-back");
                        return null;
                    });
            await(
                    () ->
                            node("ton-marking-list").isVisible()
                                    && !node("ton-marking-table").isDisabled());
            fx(
                    () -> {
                        var table = (TableView<?>) node("ton-marking-table");
                        assertEquals(1, table.getItems().size());
                        table.getSelectionModel().selectFirst();
                        table.fireEvent(enter());
                        return null;
                    });
            await(
                    () ->
                            node("ton-marking-editor").isVisible()
                                    && text().getText().startsWith("NUOVO"));
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

    TextArea text() {
        return (TextArea) node("ton-marking-text");
    }

    String preview() {
        return ((TextArea) node("ton-marking-preview")).getText();
    }

    @SuppressWarnings("unchecked")
    TableView<Snapshot> versions() {
        return (TableView<Snapshot>) node("ton-marking-versions-table");
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
        throw new AssertionError("Marking UI timed out");
    }
}

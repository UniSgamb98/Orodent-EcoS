package com.orodent.ecos.misurazioni.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.*;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.misurazioni.devices.*;
import com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.Key;
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
    int printAttempts, homeRequests;
    boolean failPrint;
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
        sql("UPDATE TON.CICLI_COTTURA SET temperatura_finale_c=986 WHERE ciclo_id=1");
        db.withTransaction(
                c -> {
                    String model =
                            """
{"schemaVersion":5,"purpose":"ETICHETTA","title":"Scatola UI","page":{"format":"custom","width":100,"height":65,"orientation":"portrait","margin":3},"content":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"labelField","attrs":{"key":"article"}}]},{"type":"paragraph","content":[{"type":"labelField","attrs":{"key":"lot"}}]},{"type":"paragraph","content":[{"type":"labelField","attrs":{"key":"expiryDate"}}]},{"type":"gs1Matrix","attrs":{"gtin":"{{gtin}}","lot":"{{lot}}","productionDate":"{{productionDateGs1}}","expiryDate":"{{expiryDateGs1}}","size":20}}]}}
""";
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " TON.MODELLI_DOCUMENTO(codice,numero_versione,nome,tipo,contenuto_modello,stato,approvato_il)"
                                        + " VALUES ('UI-BOX',1,'Scatola"
                                        + " UI','ETICHETTA',?,'APPROVATA',CURRENT_TIMESTAMP)")) {
                        q.setString(1, model);
                        q.executeUpdate();
                    }
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "UPDATE TON.ARTICOLI SET gtin='09506000134352' WHERE"
                                        + " articolo_id=1");
                        q.executeUpdate(
                                "UPDATE TON.CONFIGURAZIONI_PRODOTTO SET etichetta_modello_id=1"
                                        + " WHERE configurazione_id=1");
                    }
                    return null;
                });
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
                        assertNull(stage.getScene().lookup("#jobs-tools"));
                        assertEquals("986", session.view.table.getColumns().get(2).getCellData(1));
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
                        checkWorkspaceLayout();
                        assertSame(session.acquisition.root(), stage.getScene().getFocusOwner());
                        String info = ((Label) stage.getScene().lookup("#jobs-context")).getText();
                        assertTrue(info.contains("Forno reparto"));
                        assertFalse(info.contains("F1"));
                        assertFalse(info.contains("PR-"));
                        assertFalse(info.contains("RITIRO"));
                        assertFalse(session.view.saveAndPrint.isDisabled());
                        return null;
                    });
            shortEnter();
            await(
                    () ->
                            !session.acquisition
                                            .view
                                            .list
                                            .getItems()
                                            .getFirst()
                                            .micrometer()
                                            .isEmpty()
                                    && !session.acquisition.view.acquire.isDisabled());
            fx(
                    () -> {
                        assertEquals(0, count("TON.DISCO_MISURE_REVISIONI"));
                        session.acquisition.view.swap.fire();
                        session.acquisition.view.acquire.fire();
                        return null;
                    });
            await(() -> session.view.remaining.getText().equals("2 da completare"));
            fx(
                    () -> {
                        session.acquisition.view.acquire.fire();
                        return null;
                    });
            await(
                    () ->
                            !session.acquisition.view.list.getItems().get(1).scale().isEmpty()
                                    && !session.acquisition.view.acquire.isDisabled());
            fx(
                    () -> {
                        session.acquisition.view.acquire.fire();
                        return null;
                    });
            await(
                    () ->
                            !session.acquisition.view.list.getItems().get(2).scale().isEmpty()
                                    && !session.acquisition.view.acquire.isDisabled());
            fx(
                    () -> {
                        assertEquals(0, count("TON.DISCO_MISURE_REVISIONI"));
                        Platform.runLater(() -> answerDiscard(ButtonType.NO));
                        session.view.back.fire();
                        assertNotNull(session.acquisition.view.list.getScene());
                        Platform.runLater(() -> answerDiscard(ButtonType.NO));
                        session.view.home.fire();
                        assertEquals(0, homeRequests);
                        Platform.runLater(() -> answerDiscard(ButtonType.NO));
                        javafx.event.Event.fireEvent(
                                stage, new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                        assertTrue(stage.isShowing());
                        session.view.printLabel.fire();
                        return null;
                    });
            await(
                    () ->
                            printAttempts == 1
                                    && stage.getScene().getFocusOwner()
                                            == session.acquisition.root());
            fx(
                    () -> {
                        assertEquals(0, count("TON.DISCO_MISURE_REVISIONI"));
                        assertEquals(
                                "330,20", session.acquisition.view.list.getItems().get(2).scale());
                        return null;
                    });
            // A failed save must roll back every row and must not open a print job.
            sql(
                    "ALTER TABLE TON.DISCO_MISURE_REVISIONI ADD CONSTRAINT reject_ui_save"
                            + " CHECK(disco_id<>2)");
            fx(
                    () -> {
                        session.view.saveAndPrint.fire();
                        return null;
                    });
            await(() -> session.view.retry.isVisible() && !session.view.retry.isDisabled());
            fx(
                    () -> {
                        assertEquals(0, count("TON.DISCO_MISURE_REVISIONI"));
                        assertEquals(1, printAttempts);
                        assertTrue(session.view.saveState.getText().contains("non salvate"));
                        assertEquals(
                                "330,20", session.acquisition.view.list.getItems().get(2).scale());
                        return null;
                    });
            sql("ALTER TABLE TON.DISCO_MISURE_REVISIONI DROP CONSTRAINT reject_ui_save");
            fx(
                    () -> {
                        failPrint = true;
                        session.view.retry.fire();
                        return null;
                    });
            await(() -> labelFeedback().contains("Stampante simulata"));
            var printed =
                    new com.orodent.ecos.misurazioni.labels.service.LabelService(
                                    db, workers, MeasurementJobsFixture.CLOCK)
                            .source(new Key(1, 1, 1))
                            .join()
                            .latest();
            assertTrue(
                    printed.html().contains("21/11/2031"), "Expiry must render without user input");
            assertEquals(
                    "311121",
                    com.google.gson.JsonParser.parseString(printed.data())
                            .getAsJsonObject()
                            .get("expiryDateGs1")
                            .getAsString());
            fx(
                    () -> {
                        assertEquals(3, count("TON.DISCO_MISURE_REVISIONI"));
                        assertEquals(2, printAttempts);
                        assertNull(printWindow().getScene().lookup("#label-expiry"));
                        screenshot(printWindow(), "target/measurement-label-dates.png");
                        failPrint = false;
                        ((Button) printWindow().getScene().lookup("#label-print")).fire();
                        return null;
                    });
            await(() -> session.view.table.getScene() != null && !session.view.table.isDisabled());
            fx(
                    () -> {
                        assertEquals(3, printAttempts);
                        assertEquals(3, count("TON.DISCO_MISURE_REVISIONI"));
                        assertEquals(1, session.view.table.getItems().get(1).job().done());
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
                        session.view.open.fire();
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
                                "330,20", session.acquisition.view.list.getItems().get(2).scale());
                        // The new session starts on the micrometer; fill the remaining two discs
                        // locally.
                        session.acquisition.view.acquire.fire();
                        return null;
                    });
            await(
                    () ->
                            !session.acquisition.view.list.getItems().get(1).micrometer().isEmpty()
                                    && !session.acquisition.view.acquire.isDisabled());
            fx(
                    () -> {
                        session.acquisition.view.acquire.fire();
                        return null;
                    });
            await(() -> session.view.remaining.getText().equals("0 da completare"));
            fx(
                    () -> {
                        assertEquals(3, count("TON.DISCO_MISURE_REVISIONI"));
                        Platform.runLater(() -> answerDiscard(ButtonType.YES));
                        session.view.back.fire();
                        return null;
                    });
            await(() -> session.view.table.getScene() != null && !session.view.table.isDisabled());
            fx(
                    () -> {
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
                        assertEquals(
                                "", session.acquisition.view.list.getItems().get(1).micrometer());
                        assertEquals("2 da completare", session.view.remaining.getText());
                        screenshot("target/measurement-job-resumed.png");
                        return null;
                    });
            // An unrelated approved model must never replace the specification's assigned model.
            sql(
                    "INSERT INTO"
                        + " TON.MODELLI_DOCUMENTO(codice,numero_versione,nome,tipo,contenuto_modello,stato,approvato_il)"
                        + " SELECT 'AAA-OTHER',1,'Altro"
                        + " modello',tipo,contenuto_modello,'APPROVATA',CURRENT_TIMESTAMP FROM"
                        + " TON.MODELLI_DOCUMENTO WHERE modello_documento_id=1");
            sql(
                    "UPDATE TON.CONFIGURAZIONI_PRODOTTO SET etichetta_modello_id=NULL WHERE"
                            + " configurazione_id=1");
            fx(
                    () -> {
                        session.view.printLabel.fire();
                        return null;
                    });
            await(() -> labelFeedback().contains("Nessun modello etichetta assegnato"));
            fx(
                    () -> {
                        assertEquals(3, printAttempts);
                        var picker =
                                (ComboBox<?>) printWindow().getScene().lookup("#label-template");
                        assertNull(picker.getValue());
                        assertTrue(labelFeedback().contains("specifica v1"));
                        assertNull(printWindow().getScene().lookup("#label-expiry"));
                        assertTrue(picker.isDisabled());
                        ((Button)
                                        ((DialogPane) printWindow().getScene().getRoot())
                                                .lookupButton(ButtonType.CLOSE))
                                .fire();
                        return null;
                    });
            sql(
                    "UPDATE TON.CONFIGURAZIONI_PRODOTTO SET etichetta_modello_id=1 WHERE"
                            + " configurazione_id=1");
            sql("UPDATE TON.MODELLI_DOCUMENTO SET stato='RITIRATA' WHERE modello_documento_id=1");
            fx(
                    () -> {
                        session.acquisition.view.acquire.fire();
                        return null;
                    });
            await(
                    () ->
                            !session.acquisition.view.list.getItems().get(1).micrometer().isEmpty()
                                    && !session.acquisition.view.acquire.isDisabled());
            fx(
                    () -> {
                        session.acquisition.view.acquire.fire();
                        return null;
                    });
            await(() -> session.view.remaining.getText().equals("0 da completare"));
            fx(
                    () -> {
                        session.view.saveAndPrint.fire();
                        return null;
                    });
            await(() -> session.view.table.getScene() != null && !session.view.table.isDisabled());
            fx(
                    () -> {
                        assertEquals(4, printAttempts);
                        assertEquals(5, count("TON.DISCO_MISURE_REVISIONI"));
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
                        Platform.runLater(() -> answer(ButtonType.NO));
                        session.acquisition.view.reset.fire();
                        assertEquals(
                                "330,20",
                                session.acquisition.view.list.getItems().getFirst().scale());
                        stage.setWidth(1050);
                        stage.setHeight(780);
                        return null;
                    });
            await(
                    () ->
                            stage.getScene().getWidth() <= 1050
                                    && session.root().getLayoutBounds().getWidth() <= 1050);
            fx(
                    () -> {
                        checkWorkspaceLayout();
                        screenshot("target/measurement-job-small.png");
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

    int count(String table) {
        return db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        r.next();
                        return r.getInt(1);
                    }
                });
    }

    Window printWindow() {
        return Window.getWindows().stream()
                .filter(
                        w ->
                                w != stage
                                        && w.getScene() != null
                                        && w.getScene().lookup("#label-print") != null)
                .findFirst()
                .orElse(null);
    }

    String labelFeedback() {
        var w = printWindow();
        return w == null ? "" : ((Label) w.getScene().lookup("#label-feedback")).getText();
    }

    void answerDiscard(ButtonType type) {
        var pane =
                Window.getWindows().stream()
                        .filter(
                                w ->
                                        w != stage
                                                && w.getScene() != null
                                                && w.getScene().getRoot() instanceof DialogPane)
                        .map(w -> (DialogPane) w.getScene().getRoot())
                        .findFirst()
                        .orElseThrow();
        assertEquals("Modifiche non salvate", pane.getHeaderText());
        assertEquals(
                "Vuoi scartare le modifiche non salvate prima di continuare?",
                pane.getContentText());
        ((Button) pane.lookupButton(type)).fire();
    }

    void openSession() {
        var context =
                new ModuleContext(
                        db,
                        workers,
                        new ClientiCsvImportService(db, workers),
                        new VenditeCsvImportService(db, workers),
                        () -> homeRequests++);
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
                        (key, done) ->
                                new com.orodent.ecos.misurazioni.labels.controller
                                        .LabelPrintingController(
                                        context,
                                        new com.orodent.ecos.misurazioni.labels.service
                                                .LabelService(
                                                db, workers, MeasurementJobsFixture.CLOCK),
                                        stage,
                                        key,
                                        done,
                                        preview -> {
                                            assertTrue(preview.printable());
                                            var picker =
                                                    (ComboBox<?>)
                                                            printWindow()
                                                                    .getScene()
                                                                    .lookup("#label-template");
                                            assertTrue(picker.isDisabled());
                                            assertEquals(
                                                    1,
                                                    ((com.orodent.ecos.misurazioni.labels.model
                                                                            .Labels.Template)
                                                                    picker.getValue())
                                                            .id());
                                            printAttempts++;
                                            if (failPrint)
                                                throw new IllegalStateException(
                                                        "Stampante simulata non disponibile");
                                            return false; // Closing/cancelling the native dialog
                                            // still leaves the measurements saved.
                                        }));
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

    javafx.geometry.Bounds bounds(Node node) {
        return node.localToScene(node.getBoundsInLocal());
    }

    void checkWorkspaceLayout() {
        layout();
        var root = stage.getScene().getRoot();
        var toolbar = bounds(root.lookup("#measurements-toolbar"));
        var first = bounds(session.acquisition.view.reset);
        var last = bounds(session.acquisition.view.connectionButton(Instrument.SCALE));
        var back = bounds(session.view.back);
        assertTrue(back.getWidth() >= session.view.back.prefWidth(-1) - .1);
        assertTrue(
                session.acquisition.view.acquire.getHeight()
                        >= session.acquisition.view.acquire.prefHeight(
                                        session.acquisition.view.acquire.getWidth())
                                - .1);
        var context = bounds(root.lookup("#jobs-context"));
        var print = bounds(session.view.printLabel);
        assertEquals(
                (toolbar.getMinX() + toolbar.getMaxX()) / 2,
                (first.getMinX() + last.getMaxX()) / 2,
                1);
        assertTrue(
                context.getMaxY() <= bounds(session.acquisition.view.acquire).getMinY(),
                "Work info must be above acquisition");
        assertTrue(
                last.getMaxX() <= print.getMinX(), "Print must remain to the right of instruments");
        assertEquals(back.getMinX(), bounds(session.acquisition.view.copy).getMinX(), 1);
        assertEquals(print.getMaxX(), bounds(session.view.saveAndPrint).getMaxX(), 1);
        var remaining = bounds(session.view.remaining);
        var instrument = bounds(root.lookup("#measurements-instrument-column"));
        assertTrue(remaining.getMinX() >= instrument.getMinX());
        assertTrue(remaining.getMaxX() <= instrument.getMaxX());
        assertEquals(bounds(session.acquisition.view.list).getMaxY(), remaining.getMaxY(), 1);
        assertFalse(root.lookup("#jobs-save-messages").isVisible());
        var surface = session.acquisition.view.getBackground().getFills().getFirst().getFill();
        assertEquals(javafx.scene.paint.Color.web("#e4eff4"), surface);
    }

    void layout() {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    void screenshot(String file) throws Exception {
        screenshot(stage, file);
    }

    void screenshot(javafx.stage.Window window, String file) throws Exception {
        layout();
        var snap = window.getScene().snapshot(null);
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

package com.orodent.ecos.ton.features.documents.view;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.robot.Robot;
import javafx.scene.web.WebView;
import javafx.stage.*;

import netscape.javascript.JSObject;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

@Tag("javafx")
class DocumentEditorIntegrationTest {
    private Stage stage;
    private ModuleSession session;
    private final List<Throwable> errors = new CopyOnWriteArrayList<>();

    @Test
    void positionsObjectsOnPhysicalPagesAndPreservesApprovedVersions() throws Exception {
        String url = "jdbc:derby:memory:canvas_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        var workers = Executors.newFixedThreadPool(2);
        var started = new CompletableFuture<Void>();
        Platform.startup(
                () -> {
                    Platform.setImplicitExit(false);
                    Thread.currentThread().setUncaughtExceptionHandler((t, e) -> errors.add(e));
                    started.complete(null);
                });
        started.get(15, TimeUnit.SECONDS);
        try {
            fx(
                    () -> {
                        stage = new Stage();
                        session =
                                new TonModule()
                                        .open(
                                                new ModuleContext(
                                                        db,
                                                        workers,
                                                        new ClientiCsvImportService(db, workers),
                                                        new VenditeCsvImportService(db, workers),
                                                        () -> {}));
                        stage.setScene(new Scene(session.root(), 1500, 1000));
                        stage.show();
                        click("ton-nav-documents");
                        click("ton-tech-document_templates-button");
                        return null;
                    });
            await(() -> !button("ton-document-new").isDisabled());
            fx(
                    () -> {
                        assertNull(stage.getScene().lookup("#ton-document-new-label"));
                        assertNull(stage.getScene().lookup("#ton-document-import"));
                        assertNull(stage.getScene().lookup("#ton-document-export"));
                        click("ton-document-new");
                        return null;
                    });
            await(() -> web() != null && !button("ton-document-save").isDisabled());
            fx(
                    () -> {
                        assertEquals("canvas", model().get("layout").getAsString());
                        assertFalse(model().getAsJsonObject("content").has("content"));
                        assertEquals(
                                0,
                                ((Number)
                                                js(
                                                        "document.querySelectorAll('.paper-brand,.paper-footer,[data-action=export-model],[data-action=open-model],#model-file').length"))
                                        .intValue());
                        assertEquals(
                                "properties",
                                js("document.getElementById('font-size').closest('aside').id"));
                        controls(
                                new String[][] {
                                    {"model-purpose", "ETICHETTA"},
                                    {"paper-format", "custom"},
                                    {"paper-width", "102"},
                                    {"paper-height", "38"}
                                });
                        double ratio =
                                ((Number)
                                                js(
                                                        "document.getElementById('paper').getBoundingClientRect().width/document.getElementById('paper').getBoundingClientRect().height"))
                                        .doubleValue();
                        assertEquals(102.0 / 38, ratio, .015);
                        api().call("insert", "text");
                        return null;
                    });
            fx(
                    () -> {
                        for (KeyCode k : List.of(KeyCode.T, KeyCode.E, KeyCode.S, KeyCode.T)) {
                            new Robot().keyPress(k);
                            new Robot().keyRelease(k);
                        }
                        return null;
                    });
            await(() -> api().call("serialize").toString().contains("test"));
            fx(
                    () -> {
                        var r = new Robot();
                        r.keyPress(KeyCode.ESCAPE);
                        r.keyRelease(KeyCode.ESCAPE);
                        return null;
                    });
            await(
                    () ->
                            js("document.querySelector('#editor"
                                   + " .tiptap').editor.state.selection.toJSON().type")
                                    .equals("node"));
            fx(
                    () -> {
                        var r = new Robot();
                        r.keyPress(KeyCode.ENTER);
                        r.keyRelease(KeyCode.ENTER);
                        return null;
                    });
            await(
                    () ->
                            js("document.querySelector('#editor"
                                   + " .tiptap').editor.state.selection.toJSON().type")
                                    .equals("text"));
            fx(
                    () -> {
                        var r = new Robot();
                        r.keyPress(KeyCode.ESCAPE);
                        r.keyRelease(KeyCode.ESCAPE);
                        return null;
                    });
            fx(
                    () -> {
                        controls(
                                new String[][] {
                                    {"object-x", "80"},
                                    {"object-y", "40"},
                                    {"object-width", "180"},
                                    {"object-height", "50"},
                                    {"font-size", "14pt"}
                                });
                        assertEquals(
                                14,
                                Double.parseDouble(
                                        attrs().get("fontSize").getAsString().replace("pt", "")));
                        controls(new String[][] {{"zoom", "1.2"}});
                        return null;
                    });
            drag(".canvas-object-grip", 24, 12);
            await(() -> Math.abs(attrs().get("x").getAsDouble() - 100) < 1);
            fx(
                    () -> {
                        assertEquals(50, attrs().get("y").getAsDouble(), 1);
                        new Robot().keyPress(KeyCode.RIGHT);
                        new Robot().keyRelease(KeyCode.RIGHT);
                        new Robot().keyPress(KeyCode.DOWN);
                        new Robot().keyRelease(KeyCode.DOWN);
                        return null;
                    });
            await(
                    () ->
                            Math.abs(attrs().get("x").getAsDouble() - 101) < .1
                                    && Math.abs(attrs().get("y").getAsDouble() - 51) < .1);
            drag(".canvas-resize-se", 24, 12);
            fx(
                    () -> {
                        assertEquals(200, attrs().get("width").getAsDouble(), 1);
                        assertEquals(60, attrs().get("height").getAsDouble(), 1);
                        api().call("run", "undo");
                        assertEquals(180, attrs().get("width").getAsDouble(), 1);
                        api().call("run", "redo");
                        assertEquals(200, attrs().get("width").getAsDouble(), 1);
                        ((TextField) stage.getScene().lookup("#ton-document-code"))
                                .setText("CANVAS-LABEL");
                        click("ton-document-save");
                        return null;
                    });
            await(() -> feedback().contains("Bozza salvata"));
            fx(
                    () -> {
                        Platform.runLater(() -> answer(ButtonType.OK));
                        click("ton-document-approve");
                        return null;
                    });
            await(() -> feedback().contains("Modello approvato"));
            String approved = read(db, 1);
            var rendered = new CompletableFuture<String>();
            var label = fx(() -> new com.orodent.ecos.documents.LabelPreviewPane());
            final Stage[] labelWindow = {null};
            fx(
                    () -> {
                        labelWindow[0] = new Stage();
                        labelWindow[0].initOwner(stage);
                        labelWindow[0].setScene(new Scene(label, 500, 260));
                        labelWindow[0].show();
                        label.render(
                                approved,
                                "{}",
                                rendered::complete,
                                error ->
                                        rendered.completeExceptionally(
                                                new IllegalStateException(error)));
                        return null;
                    });
            String html = rendered.get(25, TimeUnit.SECONDS);
            assertTrue(html.contains("data-layout=\"canvas\""));
            Files.writeString(Path.of("target/canvas-label-rendered.html"), html);
            fx(
                    () -> {
                        assertTrue(label.printable());
                        label.close();
                        labelWindow[0].close();
                        stage.toFront();
                        return null;
                    });
            fx(
                    () -> {
                        assertTrue(
                                (Boolean) js("document.getElementById('model-purpose').disabled"));
                        api().call("insert", "text");
                        assertEquals(
                                1,
                                model().getAsJsonObject("content")
                                        .getAsJsonArray("content")
                                        .size());
                        click("ton-document-new-version");
                        return null;
                    });
            await(() -> feedback().contains("Nuova bozza creata"));
            fx(
                    () -> {
                        api().call("run", "undo");
                        controls(new String[][] {{"object-x", "120"}});
                        click("ton-document-save");
                        return null;
                    });
            await(() -> feedback().contains("Bozza salvata"));
            assertEquals(approved, read(db, 1));
            assertEquals(
                    120,
                    JsonParser.parseString(read(db, 2))
                            .getAsJsonObject()
                            .getAsJsonObject("content")
                            .getAsJsonArray("content")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("attrs")
                            .get("x")
                            .getAsDouble());
            fx(
                    () -> {
                        String before = api().call("serialize").toString();
                        String legacy =
                                """
{"schemaVersion":2,"title":"Precedente","page":{"orientation":"portrait","margin":15},"content":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Testo precedente"}]}]}}
""";
                        api().call("restore", legacy);
                        api().call("setEditable", false);
                        assertEquals("flow", model().get("layout").getAsString());
                        api().call("setEditable", true);
                        assertEquals("canvas", model().get("layout").getAsString());
                        assertTrue(api().call("serialize").toString().contains("Testo precedente"));
                        api().call("restore", before);
                        stage.setWidth(1100);
                        stage.setHeight(820);
                        screenshot("target/document-canvas-editor.png");
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
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
            Platform.exit();
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException e) {
                assertEquals("08006", e.getSQLState());
            }
        }
    }

    private String read(ConnectionProvider db, long id) {
        return db.withConnection(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "SELECT contenuto_modello FROM TON.MODELLI_DOCUMENTO WHERE"
                                        + " modello_documento_id=?")) {
                        q.setLong(1, id);
                        try (var r = q.executeQuery()) {
                            r.next();
                            return r.getString(1);
                        }
                    }
                });
    }

    private JsonObject model() {
        return JsonParser.parseString(api().call("serialize").toString()).getAsJsonObject();
    }

    private JsonObject attrs() {
        return model().getAsJsonObject("content")
                .getAsJsonArray("content")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("attrs");
    }

    private Object js(String code) {
        return web().getEngine().executeScript(code);
    }

    private JSObject api() {
        return (JSObject) js("window.OrodentDocumentEditor");
    }

    private WebView web() {
        return (WebView) stage.getScene().lookup("#ton-document-webview");
    }

    private Button button(String id) {
        return (Button) stage.getScene().lookup("#" + id);
    }

    private void click(String id) {
        button(id).fire();
    }

    private String feedback() {
        return ((Label) stage.getScene().lookup("#ton-document-feedback")).getText();
    }

    private void controls(String[][] values) {
        for (String[] v : values)
            js(
                    "(function(){var n=document.getElementById("
                            + new JsonPrimitive(v[0])
                            + ");n.value="
                            + new JsonPrimitive(v[1])
                            + ";n.dispatchEvent(new Event('change'));})()");
    }

    private void drag(String selector, double dx, double dy) throws Exception {
        Point2D start =
                fx(
                        () -> {
                            var box =
                                    (JSObject)
                                            js(
                                                    "document.querySelector("
                                                            + new JsonPrimitive(selector)
                                                            + ").getBoundingClientRect()");
                            return web().localToScreen(
                                            ((Number) box.getMember("left")).doubleValue() + 4,
                                            ((Number) box.getMember("top")).doubleValue() + 4);
                        });
        fx(
                () -> {
                    var robot = new Robot();
                    robot.mouseMove(start);
                    robot.mousePress(MouseButton.PRIMARY);
                    return null;
                });
        Thread.sleep(80);
        for (int i = 1; i <= 4; i++) {
            final int step = i;
            fx(
                    () -> {
                        new Robot()
                                .mouseMove(
                                        start.getX() + dx * step / 4, start.getY() + dy * step / 4);
                        return null;
                    });
            Thread.sleep(30);
        }
        fx(
                () -> {
                    new Robot().mouseRelease(MouseButton.PRIMARY);
                    return null;
                });
        Thread.sleep(100);
    }

    private void answer(ButtonType type) {
        for (Window w : Window.getWindows())
            if (w != stage
                    && w.getScene() != null
                    && w.getScene().getRoot() instanceof DialogPane pane) {
                ((Button) pane.lookupButton(type)).fire();
                return;
            }
    }

    private void screenshot(String file) throws Exception {
        var image = stage.getScene().snapshot(null);
        var out =
                new java.awt.image.BufferedImage(
                        (int) image.getWidth(),
                        (int) image.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                out.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(out, "png", Path.of(file).toFile());
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var done = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        done.complete(action.call());
                    } catch (Throwable error) {
                        done.completeExceptionally(error);
                    }
                });
        return done.get(25, TimeUnit.SECONDS);
    }

    private static void await(Callable<Boolean> check) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < end) {
            if (fx(check)) return;
            Thread.sleep(50);
        }
        fail("Attesa editor scaduta");
    }
}

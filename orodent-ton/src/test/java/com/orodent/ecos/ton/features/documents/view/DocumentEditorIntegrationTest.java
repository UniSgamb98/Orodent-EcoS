package com.orodent.ecos.ton.features.documents.view;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.scene.web.WebView;
import javafx.stage.*;

import netscape.javascript.JSObject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

@Tag("javafx")
class DocumentEditorIntegrationTest {
    private Stage stage;
    private ModuleSession session;
    private final List<Throwable> asyncErrors = new CopyOnWriteArrayList<>();

    @Test
    void editsOfflineFromTonNavigationAndPersistsVersionedLayouts() throws Exception {
        String oldLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/document-editor-ui.log");
        String url = "jdbc:derby:memory:editor_" + UUID.randomUUID().toString().replace("-", "");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        var workers = Executors.newFixedThreadPool(2);
        var started = new CompletableFuture<Void>();
        Platform.startup(
                () -> {
                    Platform.setImplicitExit(false);
                    Thread.currentThread()
                            .setUncaughtExceptionHandler((t, e) -> asyncErrors.add(e));
                    started.complete(null);
                });
        started.get(10, TimeUnit.SECONDS);
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
                        stage.setScene(new Scene(session.root(), 1440, 900));
                        stage.show();
                        assertNull(stage.getScene().lookup("#ton-document-webview"));
                        click("ton-nav-documents");
                        click("ton-tech-document_templates-button");
                        return null;
                    });
            await(() -> !button("ton-document-new").isDisabled());
            fx(
                    () -> {
                        click("ton-document-new");
                        return null;
                    });
            await(
                    () ->
                            web() != null
                                    && Boolean.TRUE.equals(
                                            web().getEngine()
                                                    .executeScript(
                                                            "document.documentElement.dataset.editorReady==='true'"))
                                    && !button("ton-document-save").isDisabled());
            verifiesNativePointerDrag();
            verifiesEditableColumnsAndZoom();
            verifiesAutomaticBlockProperties();
            fx(
                    () -> {
                        assertTrue(
                                web().getEngine().getLocation().startsWith("file:")
                                        || web().getEngine().getLocation().startsWith("jar:"));
                        assertEquals(
                                "none",
                                web().getEngine()
                                        .executeScript(
                                                "getComputedStyle(document.querySelector('.appbar')).display"));
                        assertEquals(
                                "none",
                                web().getEngine()
                                        .executeScript(
                                                "getComputedStyle(document.querySelector('[data-action=export-model]')).display"));
                        assertEquals(
                                6,
                                ((Number)
                                                web().getEngine()
                                                        .executeScript(
                                                                "document.querySelectorAll('#laboratory-palette"
                                                                    + " button').length"))
                                        .intValue());
                        var api = api();
                        api.call("insert", "table");
                        web().getEngine()
                                .executeScript(
                                        "document.querySelector('[data-action=insert-table]').click()");
                        assertTrue(api.call("serialize").toString().contains("tableRow"));
                        web().getEngine()
                                .executeScript(
                                        "document.getElementById('template-title').value='Foglio"
                                                + " laboratorio offline'");
                        ((TextField) stage.getScene().lookup("#ton-document-code"))
                                .setText("LAB-TEST");
                        click("ton-document-save");
                        return null;
                    });
            await(() -> feedback().contains("Bozza salvata"));
            long firstId =
                    db.withConnection(
                            c -> {
                                try (var q = c.createStatement();
                                        var r =
                                                q.executeQuery(
                                                        "SELECT"
                                                            + " modello_documento_id,nome,contenuto_modello"
                                                            + " FROM TON.MODELLI_DOCUMENTO")) {
                                    assertTrue(r.next());
                                    assertEquals("Foglio laboratorio offline", r.getString(2));
                                    assertTrue(r.getString(3).contains("tableRow"));
                                    return r.getLong(1);
                                }
                            });
            fx(
                    () -> {
                        approveNextDialog();
                        click("ton-document-approve");
                        return null;
                    });
            await(() -> feedback().contains("Modello approvato"));
            fx(
                    () -> {
                        assertTrue(button("ton-document-save").isDisabled());
                        assertFalse(button("ton-document-new-version").isDisabled());
                        assertEquals(
                                "true",
                                web().getEngine()
                                        .executeScript(
                                                "String(document.getElementById('template-title').readOnly)"));
                        click("ton-document-new-version");
                        return null;
                    });
            await(() -> feedback().contains("Nuova bozza creata"));
            fx(
                    () -> {
                        assertFalse(button("ton-document-save").isDisabled());
                        web().getEngine()
                                .executeScript(
                                        "document.getElementById('template-title').value='Modifiche"
                                                + " non salvate'");
                        Platform.runLater(() -> answerDialog(ButtonType.CANCEL));
                        click("ton-document-back");
                        assertNotNull(web(), "The dirty guard must keep the editor open");
                        Platform.runLater(() -> answerDialog(ButtonType.CANCEL));
                        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                        assertTrue(stage.isShowing(), "Window close must respect unsaved edits");
                        click("ton-document-save");
                        return null;
                    });
            await(() -> feedback().contains("Bozza salvata"));
            WebView previous = fx(() -> web());
            fx(
                    () -> {
                        click("ton-document-back");
                        return null;
                    });
            await(() -> !button("ton-document-new").isDisabled());
            fx(
                    () -> {
                        assertNull(web());
                        assertEquals("about:blank", previous.getEngine().getLocation());
                        var table = (TableView<?>) stage.getScene().lookup("#ton-document-table");
                        assertEquals(1, table.getItems().size());
                        table.getSelectionModel().selectFirst();
                        click("ton-document-open");
                        return null;
                    });
            await(() -> web() != null && !button("ton-document-save").isDisabled());
            fx(
                    () -> {
                        assertNotSame(previous, web());
                        assertTrue(
                                api().call("serialize")
                                        .toString()
                                        .contains("Modifiche non salvate"));
                        @SuppressWarnings("unchecked")
                        var versions =
                                (ComboBox<Object>)
                                        stage.getScene().lookup("#ton-document-versions");
                        assertEquals(2, versions.getItems().size());
                        versions.getSelectionModel().selectLast();
                        return null;
                    });
            await(
                    () ->
                            button("ton-document-save").isDisabled()
                                    && !button("ton-document-new-version").isDisabled());
            fx(
                    () -> {
                        assertTrue(
                                api().call("serialize")
                                        .toString()
                                        .contains("Foglio laboratorio offline"));
                        assertFalse(
                                api().call("serialize")
                                        .toString()
                                        .contains("Modifiche non salvate"));
                        stage.setWidth(1024);
                        stage.setHeight(760);
                        return null;
                    });
            // Use the real WebView at the minimum supported desktop width as well.
            await(() -> ((Number) web().getEngine().executeScript("innerWidth")).intValue() > 500);
            db.withConnection(
                    c -> {
                        try (var q =
                                c.prepareStatement(
                                        "SELECT stato,nome FROM TON.MODELLI_DOCUMENTO WHERE"
                                                + " modello_documento_id=?")) {
                            q.setLong(1, firstId);
                            try (var r = q.executeQuery()) {
                                r.next();
                                assertEquals("APPROVATA", r.getString(1));
                                assertEquals("Foglio laboratorio offline", r.getString(2));
                            }
                        }
                        return null;
                    });
            assertTrue(asyncErrors.isEmpty(), () -> asyncErrors.toString());
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        if (stage != null) stage.close();
                        return null;
                    });
            workers.shutdown();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
            Platform.exit();
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException e) {
                assertEquals("08006", e.getSQLState());
            }
            if (oldLog == null) System.clearProperty("derby.stream.error.file");
            else System.setProperty("derby.stream.error.file", oldLog);
        }
    }

    private void verifiesNativePointerDrag() throws Exception {
        String before = fx(() -> api().call("serialize").toString());
        fx(
                () -> {
                    stage.setWidth(1200);
                    stage.setHeight(760);
                    web().getEngine()
                            .executeScript(
                                    "document.querySelector('[data-insert=press]').scrollIntoView({block:'center'})");
                    return null;
                });
        Thread.sleep(200);
        Point2D source = fx(() -> point("[data-insert=press]", .5, .5));
        Point2D target = fx(() -> point(".tiptap > h1", .1, .3));
        fx(
                () -> {
                    var robot = new Robot();
                    robot.mouseMove(source);
                    robot.mousePress(MouseButton.PRIMARY);
                    return null;
                });
        try {
            for (int i = 1; i <= 12; i++) {
                final int step = i;
                fx(
                        () -> {
                            new Robot()
                                    .mouseMove(
                                            source.getX()
                                                    + (target.getX() - source.getX()) * step / 12.0,
                                            source.getY()
                                                    + (target.getY() - source.getY())
                                                            * step
                                                            / 12.0);
                            return null;
                        });
                Thread.sleep(25);
            }
        } finally {
            fx(
                    () -> {
                        new Robot().mouseRelease(MouseButton.PRIMARY);
                        return null;
                    });
        }
        await(
                () ->
                        ((Number)
                                                web().getEngine()
                                                        .executeScript(
                                                                "document.querySelectorAll('.tiptap"
                                                                    + " [data-ton-block=press]').length"))
                                        .intValue()
                                == 2);
        fx(
                () -> {
                    String after = api().call("serialize").toString();
                    assertTrue(after.contains("tonBlock"));
                    assertFalse(
                            after.contains("Parametri della pressaCompilato dalla produzione"),
                            "A drag must insert a block, not the palette label");
                    api().call("restore", before);
                    stage.setWidth(1440);
                    stage.setHeight(900);
                    return null;
                });
    }

    private void verifiesAutomaticBlockProperties() throws Exception {
        String original = fx(() -> api().call("serialize").toString());
        fx(
                () -> {
                    api().call("insert", "mixtures");
                    web().getEngine()
                            .executeScript(
                                    """
window.propertyTestId = document.querySelector('.ton-block.ProseMirror-selectednode').dataset.blockId;
document.getElementById('block-tab-content').click();
for (const id of ['block-hide-zero-powders','block-percent']) {
  const input = document.getElementById(id); input.checked = id.includes('hide');
  input.dispatchEvent(new Event('change'));
}
document.getElementById('block-tab-appearance').click();
for (const [id,value] of [['block-font-size','3pt'],['block-header-color','#aabbcc'],['block-width-0','60']]) {
  const input = document.getElementById(id); input.value = value; input.dispatchEvent(new Event('change'));
}
document.getElementById('block-tab-print').click();
const page = document.getElementById('block-page-break'); page.checked=true; page.dispatchEvent(new Event('change'));
""");
                    String payload = api().call("serialize").toString();
                    assertTrue(payload.contains("\"fontSize\":\"3pt\""));
                    assertTrue(payload.contains("\"tableWidths\":[60,40]"));
                    assertTrue(payload.contains("\"pageBreakBefore\":true"));
                    api().call("restore", payload);
                    assertEquals(payload, api().call("serialize"));
                    assertEquals(
                            2,
                            ((Number)
                                            web().getEngine()
                                                    .executeScript(
                                                            "document.querySelector('[data-block-id=\"'+propertyTestId+'\"]"
                                                                + " .lab-table thead"
                                                                + " tr').children.length"))
                                    .intValue());
                    assertEquals(
                            5,
                            ((Number)
                                            web().getEngine()
                                                    .executeScript(
                                                            "document.querySelector('[data-block-id=\"'+propertyTestId+'\"]"
                                                                + " .mixture-card"
                                                                + " tbody').children.length"))
                                    .intValue());
                    assertEquals(
                            "4px",
                            web().getEngine()
                                    .executeScript(
                                            "getComputedStyle(document.querySelector('[data-block-id=\"'+propertyTestId+'\"]"
                                                + " .lab-table')).fontSize"));
                    api().call("restore", original);
                    return null;
                });
    }

    private void verifiesEditableColumnsAndZoom() throws Exception {
        fx(
                () -> {
                    stage.setWidth(1200);
                    stage.setHeight(760);
                    web().getEngine()
                            .executeScript(
                                    "document.getElementById('zoom').value='0.65';document.getElementById('zoom').dispatchEvent(new"
                                        + " Event('change'));document.getElementById('canvas').scrollTop=0;document.querySelector('.layout-select').click()");
                    return null;
                });
        await(
                () ->
                        web().getEngine()
                                .executeScript("document.getElementById('layout-count')!==null")
                                .equals(true));
        fx(
                () -> {
                    assertEquals(4, ((Number) api().getMember("schemaVersion")).intValue());
                    web().getEngine()
                            .executeScript(
                                    "document.getElementById('layout-preset').value='60,40';document.getElementById('layout-preset').dispatchEvent(new"
                                        + " Event('change'))");
                    return null;
                });
        Thread.sleep(150);
        Point2D start =
                fx(
                        () ->
                                point(
                                        ".tiptap > .column-section > .layout-handles"
                                                + " .layout-divider",
                                        .5,
                                        .03));
        fx(
                () -> {
                    var robot = new Robot();
                    robot.mouseMove(start);
                    robot.mousePress(MouseButton.PRIMARY);
                    return null;
                });
        try {
            for (int i = 1; i <= 10; i++) {
                final int step = i;
                fx(
                        () -> {
                            new Robot().mouseMove(start.getX() + step * 3, start.getY());
                            return null;
                        });
                Thread.sleep(25);
            }
        } finally {
            fx(
                    () -> {
                        new Robot().mouseRelease(MouseButton.PRIMARY);
                        return null;
                    });
        }
        fx(
                () -> {
                    var json =
                            com.google.gson.JsonParser.parseString(
                                            api().call("serialize").toString())
                                    .getAsJsonObject();
                    var section =
                            json
                                    .getAsJsonObject("content")
                                    .getAsJsonArray("content")
                                    .asList()
                                    .stream()
                                    .map(com.google.gson.JsonElement::getAsJsonObject)
                                    .filter(n -> n.get("type").getAsString().equals("tonColumns"))
                                    .findFirst()
                                    .orElseThrow();
                    assertTrue(
                            section.getAsJsonObject("attrs")
                                            .getAsJsonArray("widths")
                                            .get(0)
                                            .getAsDouble()
                                    > 60,
                            "Native pointer resizing must change the saved width");
                    web().getEngine()
                            .executeScript(
                                    "document.querySelector('.tiptap > .column-section >"
                                            + " .layout-tools"
                                            + " .layout-column-select:last-child').click()");
                    api().call("insert", "columns");
                    assertEquals(
                            2,
                            ((Number)
                                            web().getEngine()
                                                    .executeScript(
                                                            "document.querySelectorAll('.tiptap"
                                                                    + " .column-section').length"))
                                    .intValue());
                    web().getEngine()
                            .executeScript(
                                    "document.getElementById('layout-preset').value='30,70';document.getElementById('layout-preset').dispatchEvent(new"
                                        + " Event('change'))");
                    String snapshot = api().call("serialize").toString();
                    api().call("restore", snapshot);
                    assertEquals(snapshot, api().call("serialize"));
                    web().getEngine()
                            .executeScript(
                                    "document.getElementById('zoom').value='1.5';document.getElementById('zoom').dispatchEvent(new"
                                        + " Event('change'));document.getElementById('canvas').dispatchEvent(new"
                                        + " WheelEvent('wheel',{bubbles:true,cancelable:true,ctrlKey:true,deltaY:-120}))");
                    assertEquals(
                            "1.55",
                            web().getEngine()
                                    .executeScript("document.getElementById('zoom').value"));
                    assertEquals(snapshot, api().call("serialize"));
                    web().getEngine()
                            .executeScript(
                                    "document.getElementById('zoom').value='0.8';document.getElementById('zoom').dispatchEvent(new"
                                        + " Event('change'))");
                    return null;
                });
        fx(
                () -> {
                    web().getEngine()
                            .executeScript(
                                    "document.getElementById('zoom').value='1.5';document.getElementById('zoom').dispatchEvent(new"
                                        + " Event('change'))");
                    var robot = new Robot();
                    robot.mouseMove(point("#canvas", .3, .3));
                    robot.keyPress(javafx.scene.input.KeyCode.CONTROL);
                    robot.mouseWheel(-1);
                    robot.keyRelease(javafx.scene.input.KeyCode.CONTROL);
                    return null;
                });
        await(
                () ->
                        Double.parseDouble(
                                        web().getEngine()
                                                .executeScript(
                                                        "document.getElementById('zoom').value")
                                                .toString())
                                > 1.5);
        fx(
                () -> {
                    web().getEngine()
                            .executeScript(
                                    "document.getElementById('zoom').value='0.8';document.getElementById('zoom').dispatchEvent(new"
                                        + " Event('change'))");
                    stage.setWidth(1440);
                    stage.setHeight(900);
                    return null;
                });
    }

    private Point2D point(String selector, double x, double y) {
        String quoted = new com.google.gson.Gson().toJson(selector);
        JSObject rect =
                (JSObject)
                        web().getEngine()
                                .executeScript(
                                        "document.querySelector("
                                                + quoted
                                                + ").getBoundingClientRect()");
        return web().localToScreen(
                        ((Number) rect.getMember("left")).doubleValue()
                                + ((Number) rect.getMember("width")).doubleValue() * x,
                        ((Number) rect.getMember("top")).doubleValue()
                                + ((Number) rect.getMember("height")).doubleValue() * y);
    }

    private WebView web() {
        return (WebView) stage.getScene().lookup("#ton-document-webview");
    }

    private JSObject api() {
        return (JSObject) web().getEngine().executeScript("window.OrodentDocumentEditor");
    }

    private String feedback() {
        return ((Label) stage.getScene().lookup("#ton-document-feedback")).getText();
    }

    private Button button(String id) {
        return (Button) stage.getScene().lookup("#" + id);
    }

    private void click(String id) {
        var b = button(id);
        assertNotNull(b, id);
        assertFalse(b.isDisabled(), id);
        b.fire();
    }

    private void approveNextDialog() {
        Platform.runLater(() -> answerDialog(ButtonType.OK));
    }

    private void answerDialog(ButtonType answer) {
        var dialog =
                Window.getWindows().stream()
                        .filter(Window::isShowing)
                        .filter(w -> w.getScene() != null)
                        .map(w -> w.getScene().getRoot())
                        .filter(DialogPane.class::isInstance)
                        .map(DialogPane.class::cast)
                        .findFirst()
                        .orElseThrow();
        ((Button) dialog.lookupButton(answer)).fire();
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        var f = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        f.complete(work.call());
                    } catch (Throwable e) {
                        f.completeExceptionally(e);
                    }
                });
        return f.get(20, TimeUnit.SECONDS);
    }

    private static void await(Callable<Boolean> check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (fx(check)) return;
            Thread.sleep(50);
        }
        fail("UI condition did not become true within 20 seconds");
    }
}

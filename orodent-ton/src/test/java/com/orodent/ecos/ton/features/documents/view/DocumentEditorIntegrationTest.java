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
    private String beforeFieldTyping;
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
                        assertTrue(
                                button("ton-document-back")
                                        .getStyleClass()
                                        .contains("ton-editor-back-link"));
                        assertEquals(
                                "Dati automatici",
                                js("document.getElementById('label-heading').textContent"));
                        assertEquals(
                                0,
                                ((Number)
                                                js(
                                                        "document.querySelectorAll('#page-tools"
                                                                + " .note').length"))
                                        .intValue());
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
                        String blank = api().call("serialize").toString();
                        for (String name :
                                List.of(
                                        "article",
                                        "product",
                                        "name",
                                        "line",
                                        "lot",
                                        "gtin",
                                        "diameter",
                                        "height",
                                        "temperature",
                                        "production",
                                        "quantity",
                                        "furnace",
                                        "productionDate",
                                        "expiryDate")) api().call("insert", "label:" + name);
                        assertEquals(
                                14,
                                model().getAsJsonObject("content")
                                        .getAsJsonArray("content")
                                        .size());
                        assertTrue(
                                (Boolean)
                                        js(
                                                "Array.from(document.querySelectorAll('[data-kind=field]')).every(function(o){var"
                                                    + " v=o.querySelector('.label-value'),c=v.parentElement;return"
                                                    + " !o.querySelector('p') &&"
                                                    + " c.contentEditable==='false' &&"
                                                    + " v.scrollHeight<=c.clientHeight+1 &&"
                                                    + " v.scrollWidth<=c.clientWidth+1;})"));
                        api().call("restore", blank);
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
                        api().call("insert", "label:article");
                        assertEquals(
                                "field",
                                js(
                                        "document.querySelector('#editor"
                                            + " .tiptap').editor.state.selection.node.attrs.kind"));
                        controls(new String[][] {{"object-x", "20"}, {"object-y", "110"}});
                        assertTrue(
                                (Boolean)
                                        js(
                                                "(function(){var"
                                                    + " v=document.querySelector('[data-kind=field]"
                                                    + " .label-value'),p=v.parentElement;return"
                                                    + " Math.abs(v.getBoundingClientRect().top-p.getBoundingClientRect().top)<1"
                                                    + " && Math.abs(v.getBoundingClientRect().left-p.getBoundingClientRect().left)<1"
                                                    + " && p.contentEditable==='false';})()"));
                        beforeFieldTyping = api().call("serialize").toString();
                        js("document.querySelector('#editor .tiptap').editor.view.focus()");
                        new Robot().keyPress(KeyCode.X);
                        new Robot().keyRelease(KeyCode.X);
                        return null;
                    });
            Thread.sleep(
                    250); // Allow the native WebView to paint the updated inspector before capture.
            fx(
                    () -> {
                        assertTrue(
                                (Boolean)
                                        js(
                                                "getComputedStyle(document.getElementById('text-type').closest('.text-only')).display==='none'"));
                        assertTrue(
                                (Boolean)
                                        js(
                                                "document.getElementById('font-size').clientHeight>=32"));
                        assertEquals(beforeFieldTyping, api().call("serialize").toString());
                        screenshot("target/automatic-field-editor.png");
                        js(
                                "document.querySelector('#editor"
                                        + " .tiptap').editor.commands.setNodeSelection(0)");
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
                                "{\"article\":\"ZR9812MSA0\"}",
                                rendered::complete,
                                error ->
                                        rendered.completeExceptionally(
                                                new IllegalStateException(error)));
                        return null;
                    });
            String html = rendered.get(25, TimeUnit.SECONDS);
            assertTrue(html.contains("data-layout=\"canvas\""));
            assertTrue(html.contains("ZR9812MSA0"));
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
                                2,
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
            verifyCanvasSelection();
            verifyAlignmentAndProperties();
            verifySymbolBounds();
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

    private void verifyCanvasSelection() throws Exception {
        fx(
                () -> {
                    stage.setWidth(1700);
                    stage.setHeight(1000);
                    return null;
                });
        await(() -> web().getWidth() > 1350);
        fx(
                () -> {
                    api().call("newModel", "ETICHETTA");
                    controls(
                            new String[][] {
                                {"paper-width", "102"}, {"paper-height", "65"}, {"zoom", "1.2"}
                            });
                    api().call("insert", "label:article");
                    controls(new String[][] {{"object-x", "18"}, {"object-y", "18"}});
                    api().call("insert", "label:lot");
                    controls(new String[][] {{"object-x", "80"}, {"object-y", "50"}});
                    assertEquals(
                            "Lotto di cottura",
                            js(
                                    "document.querySelector('[data-kind=field].context-selected"
                                            + " .canvas-object-grip').textContent"));
                    assertEquals(
                            "Lotto di cottura",
                            js(
                                    "document.querySelector('#selection-properties"
                                            + " h2').textContent"));
                    api().call("insert", "text");
                    controls(
                            new String[][] {
                                {"object-width", "90"},
                                {"object-height", "35"},
                                {"object-x", "250"},
                                {"object-y", "150"}
                            });
                    assertEquals(
                            "default",
                            js(
                                    "getComputedStyle(document.querySelector('#editor"
                                            + " .tiptap')).cursor"));
                    assertEquals(
                            "text",
                            js(
                                    "getComputedStyle(document.querySelector('[data-kind=text]"
                                            + " .canvas-object-content')).cursor"));
                    assertEquals(
                            "default",
                            js(
                                    "getComputedStyle(document.querySelector('[data-kind=field]"
                                            + " .canvas-object-content')).cursor"));
                    assertTrue(
                            (Boolean)
                                    js(
                                            "Array.from(document.querySelectorAll('[data-action=toggle-insert],[data-command=undo],[data-command=redo],[data-action=check-model],[data-action=preview]')).every(function(b){return"
                                                + " !!b.closest('.titlebar');})"));
                    assertEquals(
                            0,
                            ((Number) js("document.querySelectorAll('nav.toolbar').length"))
                                    .intValue());
                    return null;
                });
        String beforeSelection = fx(() -> api().call("serialize").toString());
        drag("#paper", 264, 120);
        await(
                () ->
                        "canvasMulti"
                                .equals(
                                        js(
                                                "document.querySelector('#editor"
                                                    + " .tiptap').editor.state.selection.toJSON().type")));
        fx(
                () -> {
                    assertEquals(
                            2,
                            ((Number)
                                            js(
                                                    "document.querySelector('#editor"
                                                        + " .tiptap').editor.state.selection.ids.length"))
                                    .intValue());
                    assertEquals(beforeSelection, api().call("serialize").toString());
                    return null;
                });
        drag("[data-label-value=article]", 24, 12);
        fx(
                () -> {
                    assertEquals(38, attrs().get("x").getAsDouble(), 1);
                    assertEquals(28, attrs().get("y").getAsDouble(), 1);
                    assertEquals(
                            100,
                            model().getAsJsonObject("content")
                                    .getAsJsonArray("content")
                                    .get(1)
                                    .getAsJsonObject()
                                    .getAsJsonObject("attrs")
                                    .get("x")
                                    .getAsDouble(),
                            1);
                    assertEquals(
                            250,
                            model().getAsJsonObject("content")
                                    .getAsJsonArray("content")
                                    .get(2)
                                    .getAsJsonObject()
                                    .getAsJsonObject("attrs")
                                    .get("x")
                                    .getAsDouble());
                    new Robot().keyPress(KeyCode.RIGHT);
                    new Robot().keyRelease(KeyCode.RIGHT);
                    return null;
                });
        await(() -> Math.abs(attrs().get("x").getAsDouble() - 39) < 1);
        fx(
                () -> {
                    api().call("run", "undo");
                    assertEquals(38, attrs().get("x").getAsDouble(), 1);
                    api().call("run", "undo");
                    assertEquals(18, attrs().get("x").getAsDouble(), 1);
                    new Robot().keyPress(KeyCode.DELETE);
                    new Robot().keyRelease(KeyCode.DELETE);
                    return null;
                });
        await(() -> model().getAsJsonObject("content").getAsJsonArray("content").size() == 1);
        fx(
                () -> {
                    api().call("run", "undo");
                    assertEquals(
                            3, model().getAsJsonObject("content").getAsJsonArray("content").size());
                    controls(new String[][] {{"zoom", "2"}});
                    return null;
                });
        drag("#paper", 440, 200);
        await(
                () ->
                        "canvasMulti"
                                .equals(
                                        js(
                                                "document.querySelector('#editor"
                                                    + " .tiptap').editor.state.selection.toJSON().type")));
        Thread.sleep(250);
        fx(
                () -> {
                    screenshot("target/document-marquee-selection.png");
                    return null;
                });
        double fieldRatio =
                fx(() -> attrs().get("width").getAsDouble() / attrs().get("height").getAsDouble());
        fx(
                () -> {
                    js(
                            "document.querySelector('#editor"
                                    + " .tiptap').editor.commands.setNodeSelection(0)");
                    new Robot().keyPress(KeyCode.SHIFT);
                    return null;
                });
        drag(".canvas-object.ProseMirror-selectednode .canvas-resize-se", 24, 12);
        fx(
                () -> {
                    new Robot().keyRelease(KeyCode.SHIFT);
                    assertEquals(
                            fieldRatio,
                            attrs().get("width").getAsDouble()
                                    / attrs().get("height").getAsDouble(),
                            .05);
                    return null;
                });
    }

    private void verifyAlignmentAndProperties() throws Exception {
        fx(
                () -> {
                    api().call("newModel", "ETICHETTA");
                    controls(new String[][] {{"paper-width", "102"}, {"paper-height", "65"}});
                    api().call("insert", "label:height");
                    controls(
                            new String[][] {
                                {"object-x", "13.34"},
                                {"object-y", "9.34"},
                                {"object-width", "31.59"},
                                {"object-height", "18.27"}
                            });
                    for (String zoom : List.of("0.25", "0.5", "0.65", "1", "1.2", "1.5", "2")) {
                        controls(new String[][] {{"zoom", zoom}});
                        assertCanvasChrome(Double.parseDouble(zoom));
                    }
                    controls(new String[][] {{"font-size", "9pt"}});
                    js("document.querySelector('[data-object=fit]').click()");
                    assertEquals("9pt", attrs().get("fontSize").getAsString());
                    assertTrue(attrs().get("width").getAsDouble() >= 8);
                    assertTrue(
                            (Boolean)
                                    js(
                                            "getComputedStyle(document.querySelector('.text-only')).display==='none'"));
                    api().call("insert", "text");
                    js(
                            "var e=document.querySelector('#editor"
                                + " .tiptap').editor;e.commands.setTextSelection(e.state.selection.from+2);e.commands.insertContent('Testo"
                                + " libero');");
                    controls(new String[][] {{"text-highlight", "#ffee00"}});
                    assertTrue(api().call("serialize").toString().contains("#ffee00"));
                    api().call("insert", "symbol:modelnumber");
                    assertTrue((Boolean) js("!!document.getElementById('replace-image')"));
                    controls(new String[][] {{"image-alt", "Simbolo prova"}});
                    assertTrue(api().call("serialize").toString().contains("Simbolo prova"));
                    api().call("insert", "gs1");
                    controls(new String[][] {{"gs1-lot", "LOT-PROVA"}});
                    assertTrue(api().call("serialize").toString().contains("LOT-PROVA"));
                    assertTrue((Boolean) js("document.getElementById('text-properties').hidden"));
                    api().call("insert", "table");
                    js("document.querySelector('[data-action=insert-table]').click()");
                    assertEquals(
                            6,
                            ((Number)
                                            js(
                                                    "document.querySelectorAll('[data-table-command]').length"))
                                    .intValue());
                    api().call("newModel", "PRODUZIONE");
                    api().call("insert", "identity");
                    assertTrue((Boolean) js("!!document.querySelector('.block-property-tabs')"));
                    api().call("newModel", "ETICHETTA");
                    controls(
                            new String[][] {
                                {"paper-width", "102"}, {"paper-height", "65"}, {"zoom", "2"}
                            });
                    api().call("insert", "label:article");
                    controls(new String[][] {{"object-x", "40"}, {"object-y", "25"}});
                    api().call("insert", "label:lot");
                    controls(
                            new String[][] {
                                {"object-width", "70"}, {"object-x", "220"}, {"object-y", "90"}
                            });
                    String before = api().call("serialize").toString();
                    assertEquals(
                            "false",
                            js(
                                    "document.getElementById('snap-toggle').getAttribute('aria-pressed')"));
                    js("document.getElementById('snap-toggle').click()");
                    assertEquals(before, api().call("serialize").toString());
                    return null;
                });
        drag(
                "[data-label-value=lot]",
                -356,
                0,
                () -> {
                    assertEquals(
                            40,
                            ((Number)
                                            js(
                                                    "Number(document.querySelector('.alignment-guide-x').dataset.value)"))
                                    .doubleValue(),
                            .01);
                    screenshot("target/document-snap-guides.png");
                    return null;
                });
        fx(
                () -> {
                    assertEquals(40, attrs(1).get("x").getAsDouble(), .01);
                    assertEquals(
                            0,
                            ((Number) js("document.querySelectorAll('.alignment-guide').length"))
                                    .intValue());
                    api().call("run", "undo");
                    assertEquals(220, attrs(1).get("x").getAsDouble(), .01);
                    js("document.getElementById('snap-toggle').click()");
                    return null;
                });
        drag(
                "[data-label-value=lot]",
                -356,
                0,
                () -> {
                    assertEquals(
                            0,
                            ((Number) js("document.querySelectorAll('.alignment-guide').length"))
                                    .intValue());
                    return null;
                });
        fx(
                () -> {
                    assertEquals(42, attrs(1).get("x").getAsDouble(), .01);
                    api().call("run", "undo");
                    js("document.getElementById('snap-toggle').click()");
                    return null;
                });
        drag("#paper", 640, 240);
        await(
                () ->
                        "canvasMulti"
                                .equals(
                                        js(
                                                "document.querySelector('#editor"
                                                    + " .tiptap').editor.state.selection.toJSON().type")));
        drag("[data-label-value=article]", 50, 0);
        fx(
                () -> {
                    double centre =
                            (attrs(0).get("x").getAsDouble() + attrs(1).get("x").getAsDouble() + 70)
                                    / 2;
                    assertEquals(102 * 96 / 25.4 / 2, centre, .01);
                    api().call("run", "undo");
                    assertEquals(40, attrs(0).get("x").getAsDouble(), .01);
                    js(
                            "document.querySelector('#editor"
                                    + " .tiptap').editor.commands.setNodeSelection(0)");
                    return null;
                });
        drag(
                ".context-selected .canvas-resize-e",
                150,
                0,
                () -> {
                    assertEquals(
                            220,
                            ((Number)
                                            js(
                                                    "Number(document.querySelector('.alignment-guide-x').dataset.value)"))
                                    .doubleValue(),
                            .01);
                    return null;
                });
        fx(
                () -> {
                    assertEquals(180, attrs(0).get("width").getAsDouble(), .01);
                    api().call("run", "undo");
                    assertEquals(103, attrs(0).get("width").getAsDouble(), 1);
                    return null;
                });
    }

    private void assertCanvasChrome(double zoom) {
        var measurement =
                JsonParser.parseString(
                                js("""
JSON.stringify((()=>{
  const object=document.querySelector('.context-selected'), box=object.getBoundingClientRect();
  const anchors={nw:[0,0],n:[.5,0],ne:[1,0],e:[1,.5],se:[1,1],s:[.5,1],sw:[0,1],w:[0,.5]};
  const handles=Array.from(object.querySelectorAll('.canvas-resize')).map(handle=>{
    const rect=handle.getBoundingClientRect(), anchor=anchors[handle.dataset.edge];
    return {edge:handle.dataset.edge, width:rect.width,height:rect.height,
      dx:rect.left+rect.width/2-box.left-anchor[0]*box.width,
      dy:rect.top+rect.height/2-box.top-anchor[1]*box.height,
      border:parseFloat(getComputedStyle(handle).borderLeftWidth)};
  });
  const stroke=getComputedStyle(object,'::before'), grip=object.querySelector('.canvas-object-grip').getBoundingClientRect();
  return {handles, gripHeight:grip.height, width:box.width,height:box.height,
    strokeWidth:parseFloat(stroke.width),strokeHeight:parseFloat(stroke.height),
    strokeLeft:parseFloat(stroke.left),strokeTop:parseFloat(stroke.top),
    strokeBorder:parseFloat(stroke.borderLeftWidth),strokeTransform:stroke.transform,
    outlineOffset:getComputedStyle(object).outlineOffset};
})())
""")
                                        .toString())
                        .getAsJsonObject();
        for (var entry : measurement.getAsJsonArray("handles")) {
            var h = entry.getAsJsonObject();
            assertEquals(8, h.get("width").getAsDouble(), .15, entry.toString());
            assertEquals(8, h.get("height").getAsDouble(), .15, entry.toString());
            assertEquals(0, h.get("dx").getAsDouble(), .15, entry.toString());
            assertEquals(0, h.get("dy").getAsDouble(), .15, entry.toString());
            assertEquals(1, h.get("border").getAsDouble(), .01, entry.toString());
        }
        assertEquals(22, measurement.get("gripHeight").getAsDouble(), .15);
        assertEquals("0px", measurement.get("outlineOffset").getAsString());
        // The pseudo-element is counter-scaled once: its CSS width matches screen width + 1px.
        assertEquals(
                measurement.get("width").getAsDouble() + 1,
                measurement.get("strokeWidth").getAsDouble(),
                .15);
        assertEquals(
                measurement.get("height").getAsDouble() + 1,
                measurement.get("strokeHeight").getAsDouble(),
                .15);
        assertEquals(-.5, measurement.get("strokeLeft").getAsDouble() * zoom, .05);
        assertEquals(-.5, measurement.get("strokeTop").getAsDouble() * zoom, .05);
        assertEquals(1, measurement.get("strokeBorder").getAsDouble(), .01);
        assertTrue(measurement.get("strokeTransform").getAsString().startsWith("matrix("));
    }

    private void verifySymbolBounds() throws Exception {
        var catalogue =
                JsonParser.parseString(
                                Files.readString(
                                        Path.of(
                                                "../orodent-documenti/src/main/frontend/document-editor/symbols.json")))
                        .getAsJsonArray();
        JsonObject ref = null;
        for (var entry : catalogue)
            if (entry.getAsJsonObject().get("key").getAsString().equals("cataloguenumber"))
                ref = entry.getAsJsonObject();
        final var symbol = Objects.requireNonNull(ref);
        fx(
                () -> {
                    api().call("newModel", "ETICHETTA");
                    controls(
                            new String[][] {
                                {"paper-width", "102"}, {"paper-height", "65"}, {"zoom", "2"}
                            });
                    api().call("insert", "symbol:cataloguenumber");
                    assertEquals(
                            338.0 / 212,
                            attrs().get("width").getAsDouble()
                                    / attrs().get("height").getAsDouble(),
                            1e-8);
                    assertTrue(attrs().get("keepRatio").getAsBoolean());
                    controls(new String[][] {{"object-width", "40"}});
                    assertEquals(40.0 * 212 / 338, attrs().get("height").getAsDouble(), 1e-8);
                    controls(new String[][] {{"object-height", "20"}});
                    assertEquals(20.0 * 338 / 212, attrs().get("width").getAsDouble(), 1e-8);
                    var old = model();
                    var object =
                            old.getAsJsonObject("content")
                                    .getAsJsonArray("content")
                                    .get(0)
                                    .getAsJsonObject();
                    var a = object.getAsJsonObject("attrs");
                    a.addProperty("x", 10.25);
                    a.addProperty("y", 20.75);
                    a.addProperty("width", 25);
                    a.addProperty("height", 25);
                    object.getAsJsonArray("content")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("attrs")
                            .addProperty(
                                    "src",
                                    symbol.getAsJsonObject("legacy").get("src").getAsString());
                    api().call("setEditable", false);
                    api().call("restore", old.toString());
                    assertEquals(
                            old,
                            model(),
                            "Passive display must preserve approved artwork and geometry");
                    api().call("setEditable", true);
                    assertEquals(10.25 + 31.0 / 16, attrs().get("x").getAsDouble(), 1e-8);
                    assertEquals(20.75 + 93.0 / 16, attrs().get("y").getAsDouble(), 1e-8);
                    assertEquals(338.0 / 16, attrs().get("width").getAsDouble(), 1e-8);
                    assertEquals(212.0 / 16, attrs().get("height").getAsDouble(), 1e-8);
                    var normalized = model();
                    assertEquals(
                            symbol.get("src"),
                            normalized
                                    .getAsJsonObject("content")
                                    .getAsJsonArray("content")
                                    .get(0)
                                    .getAsJsonObject()
                                    .getAsJsonArray("content")
                                    .get(0)
                                    .getAsJsonObject()
                                    .getAsJsonObject("attrs")
                                    .get("src"));
                    api().call("setEditable", true);
                    assertEquals(normalized, model(), "Normalization must be idempotent");
                    js(
                            "document.querySelector('.canvas-object-grip').dispatchEvent(new"
                                + " MouseEvent('mousedown',{bubbles:true,button:0}));document.dispatchEvent(new"
                                + " MouseEvent('mouseup',{bubbles:true,button:0}));");
                    return null;
                });
        Thread.sleep(250);
        fx(
                () -> {
                    screenshot("target/document-symbol-bounds.png");
                    return null;
                });
    }

    private JsonObject model() {
        return JsonParser.parseString(api().call("serialize").toString()).getAsJsonObject();
    }

    private JsonObject attrs() {
        return attrs(0);
    }

    private JsonObject attrs(int index) {
        return model().getAsJsonObject("content")
                .getAsJsonArray("content")
                .get(index)
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
        drag(selector, dx, dy, null);
    }

    private void drag(String selector, double dx, double dy, Callable<Void> beforeRelease)
            throws Exception {
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
        try {
            if (beforeRelease != null) fx(beforeRelease);
        } finally {
            fx(
                    () -> {
                        new Robot().mouseRelease(MouseButton.PRIMARY);
                        return null;
                    });
        }
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

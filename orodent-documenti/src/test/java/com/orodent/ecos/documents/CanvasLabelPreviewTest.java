package com.orodent.ecos.documents;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

@Tag("javafx")
class CanvasLabelPreviewTest {
    private static String model(double height, double lineHeight) {
        var paragraphs = new JsonArray();
        for (String text : List.of("(01)", "(10)", "(11)", "(17)"))
            paragraphs.add(
                    JsonParser.parseString(
                            "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
                                    + text
                                    + "\"}]}"));
        var object =
                JsonParser.parseString(
                                """
{"type":"canvasObject","attrs":{"id":"obj-gs1-captions","kind":"text","x":200,"y":10,"width":40,"height":125,"z":0,"page":0,"fontFamily":"Arial","fontSize":"11pt","lineHeight":2,"color":"#000000","textAlign":"left","bold":false,"italic":false,"underline":false,"keepRatio":false,"groupId":""}}
""")
                        .getAsJsonObject();
        object.getAsJsonObject("attrs").addProperty("height", height);
        object.getAsJsonObject("attrs").addProperty("lineHeight", lineHeight);
        object.add("content", paragraphs);
        return "{\"schemaVersion\":8,\"purpose\":\"ETICHETTA\",\"title\":\"Test\",\"layout\":\"canvas\",\"page\":{\"format\":\"custom\",\"orientation\":\"landscape\",\"width\":102,\"height\":38,\"margin\":0,\"count\":1},\"content\":{\"type\":\"doc\",\"content\":["
                + object
                + "]}}";
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }

    private static double lineHeight(WebView web) {
        return ((Number)
                        web.getEngine()
                                .executeScript(
                                        "parseFloat(getComputedStyle(document.querySelector('.canvas-object-content"
                                            + " > p')).lineHeight)"))
                .doubleValue();
    }

    @Test
    void editorAndPrintHonorTheSameLineHeightAndDetectRealClipping() throws Exception {
        var started = new CompletableFuture<Void>();
        Platform.startup(
                () -> {
                    Platform.setImplicitExit(false);
                    started.complete(null);
                });
        started.get(15, TimeUnit.SECONDS);
        final Stage[] stage = {null};
        var editor = fx(DocumentEditorPane::new);
        var preview = fx(LabelPreviewPane::new);
        try {
            var ready = new CompletableFuture<Void>();
            fx(
                    () -> {
                        stage[0] = new Stage();
                        stage[0].setScene(new Scene(new VBox(editor, preview), 1000, 900));
                        stage[0].show();
                        editor.start(
                                () -> ready.complete(null),
                                e -> ready.completeExceptionally(new AssertionError(e)));
                        return null;
                    });
            ready.get(20, TimeUnit.SECONDS);
            double editorHeight =
                    fx(
                            () -> {
                                editor.restore(model(125, 2));
                                editor.editable(false);
                                return lineHeight((WebView) editor.getCenter());
                            });
            var rendered = new CompletableFuture<String>();
            fx(
                    () -> {
                        preview.render(
                                model(125, 2),
                                "{}",
                                rendered::complete,
                                e -> rendered.completeExceptionally(new AssertionError(e)));
                        return null;
                    });
            rendered.get(20, TimeUnit.SECONDS);
            fx(
                    () -> {
                        var web = (WebView) preview.getCenter();
                        assertEquals(
                                editorHeight,
                                lineHeight(web),
                                .1,
                                "Editor and issued label must use identical spacing");
                        assertEquals(2 * 11 * 96.0 / 72, lineHeight(web), .1);
                        assertTrue(CanvasPrintLayout.fits(web));
                        return null;
                    });
            var clipped = new CompletableFuture<String>();
            fx(
                    () -> {
                        preview.render(
                                model(78.25, 2),
                                "{}",
                                value -> clipped.complete("unexpected success"),
                                clipped::complete);
                        return null;
                    });
            assertTrue(clipped.get(20, TimeUnit.SECONDS).contains("altezza"));
            var fixed = new CompletableFuture<String>();
            fx(
                    () -> {
                        preview.render(
                                model(78.25, 1.2),
                                "{}",
                                fixed::complete,
                                e -> fixed.completeExceptionally(new AssertionError(e)));
                        return null;
                    });
            fixed.get(20, TimeUnit.SECONDS);
            assertTrue(fx(preview::printable));
        } finally {
            fx(
                    () -> {
                        preview.close();
                        editor.dispose();
                        if (stage[0] != null) stage[0].close();
                        return null;
                    });
            Platform.exit();
        }
    }
}

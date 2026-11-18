package com.orodent.ecos.ton.features.powders.view;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.ton.features.powders.model.PowderModel.Oxide;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Uses the JavaFX toolkit owned by DocumentEditorIntegrationTest. */
public final class OxidesEditorChecks {
    Stage stage;
    OxidesEditor editor;

    public void typingKeepsCaretSelectionAndImmediateValuesAcrossCellUpdates() throws Exception {
        try {
            fx(
                    () -> {
                        editor = new OxidesEditor();
                        editor.show(List.of(new Oxide("Y2O3", BigDecimal.ONE)), false);
                        stage = new Stage();
                        stage.setScene(new Scene(editor, 900, 500));
                        stage.show();
                        layout();
                        return null;
                    });
            type("ton-oxide-percent-0", "5,150");
            fx(
                    () -> {
                        var field = field("ton-oxide-percent-0");
                        field.positionCaret(1);
                        field.replaceSelection("0");
                        layout();
                        return null;
                    });
            fx(
                    () -> {
                        var field = field("ton-oxide-percent-0");
                        assertEquals("50,150", field.getText());
                        assertEquals(2, field.getCaretPosition());
                        field.deletePreviousChar();
                        layout();
                        return null;
                    });
            fx(
                    () -> {
                        var field = field("ton-oxide-percent-0");
                        assertEquals("5,150", field.getText());
                        assertEquals(1, field.getCaretPosition());
                        field.selectRange(2, 5);
                        field.replaceSelection("250");
                        layout();
                        return null;
                    });
            fx(
                    () -> {
                        assertEquals("5,250", field("ton-oxide-percent-0").getText());
                        assertEquals(
                                0,
                                new BigDecimal("5.250")
                                        .compareTo(editor.values().getFirst().weightPercent()));
                        return null;
                    });
            type("ton-oxide-code-0", "HfO2");
            fx(
                    () -> {
                        assertEquals("HFO2", editor.values().getFirst().code());
                        editor.show(List.of(new Oxide("AL2O3", new BigDecimal("0.250"))), true);
                        layout();
                        return null;
                    });
            fx(
                    () -> {
                        assertEquals("0,250", field("ton-oxide-percent-0").getText());
                        assertTrue(field("ton-oxide-percent-0").isDisabled());
                        assertTrue(field("ton-oxide-code-0").isDisabled());
                        return null;
                    });
        } finally {
            fx(
                    () -> {
                        if (stage != null) stage.hide();
                        return null;
                    });
        }
    }

    private void type(String id, String value) throws Exception {
        fx(
                () -> {
                    field(id).requestFocus();
                    field(id).clear();
                    layout();
                    return null;
                });
        for (int i = 0; i < value.length(); i++) {
            final int position = i;
            fx(
                    () -> {
                        field(id).replaceSelection(value.substring(position, position + 1));
                        layout();
                        return null;
                    });
            fx(
                    () -> {
                        assertEquals(
                                value.substring(0, position + 1),
                                field(id).getText(),
                                "Typing must preserve character order");
                        assertEquals(
                                position + 1,
                                field(id).getCaretPosition(),
                                "Refreshing a cell must not reset its caret");
                        return null;
                    });
        }
    }

    private TextField field(String id) {
        return (TextField) editor.lookup("#" + id);
    }

    private void layout() {
        editor.applyCss();
        editor.layout();
    }

    private <T> T fx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        result.complete(action.call());
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                });
        return result.get(30, TimeUnit.SECONDS);
    }
}

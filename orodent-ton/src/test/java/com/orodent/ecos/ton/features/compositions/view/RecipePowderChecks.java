package com.orodent.ecos.ton.features.compositions.view;

import static com.orodent.ecos.ton.features.compositions.model.Compositions.*;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Runs on the FX thread owned by the catalogue integration probe. */
public final class RecipePowderChecks {
    public void verify() {
        var powders =
                List.of(
                        powder(1, "BIANCA", 3),
                        powder(2, "BIANCA", 4),
                        powder(3, "GIALLA", 4),
                        powder(4, "ROSA", 5),
                        powder(5, "GRIGIA", 6));
        var selected = powders.get(3);
        var state = new RecipeState();
        state.show(
                List.of(
                        new Layer(
                                1,
                                "",
                                List.of(new Ingredient(selected.id(), new BigDecimal("100"))))),
                Map.of(selected.id(), selected));
        var editor = new RecipeEditor(state);
        editor.show(false);
        editor.choices(powders);
        var root = new BorderPane(editor);
        root.getStyleClass().add("ton-technical-detail");
        root.getStylesheets()
                .add(getClass().getResource("/ton/css/technical-editor.css").toExternalForm());
        var stage = new Stage();
        try {
            stage.setScene(new Scene(root, 1100, 700));
            stage.show();
            root.applyCss();
            root.layout();
            @SuppressWarnings("unchecked")
            var box = (ComboBox<PowderRef>) root.lookup("#ton-composition-ingredient-1-powder");
            assertEquals(powders, box.getItems(), "Selection must not reorder the choices");
            assertEquals(selected, box.getValue());
            box.show();
            int checked = 0;
            for (var window : Window.getWindows()) {
                if (window == stage || !window.isShowing()) continue;
                var popup = window.getScene().getRoot();
                popup.applyCss();
                popup.layout();
                for (var node : popup.lookupAll(".ton-comp-powder-option"))
                    if (node instanceof ListCell<?> cell
                            && cell.getItem() instanceof PowderRef powder) {
                        assertEquals(
                                color(powder.color()),
                                cell.getBackground().getFills().getFirst().getFill());
                        assertTrue(cell.getWidth() > 200, "Colour must fill the dropdown row");
                        checked++;
                    }
            }
            assertEquals(powders.size(), checked, "Every popup option has the powder background");
            box.hide();
            assertEquals(
                    color("ROSA"),
                    box.getButtonCell().getBackground().getFills().getFirst().getFill());
            var cell = new PowderOptionCell();
            root.setBottom(cell);
            for (var powder : powders) {
                cell.updateItem(powder, false);
                root.applyCss();
                root.layout();
                assertEquals(
                        color(powder.color()),
                        cell.getBackground().getFills().getFirst().getFill());
            }
            cell.updateItem(null, true);
            root.applyCss();
            assertNull(cell.getText());
            assertNull(cell.getTooltip());
            assertTrue(cell.getStyleClass().stream().noneMatch(s -> s.startsWith("powder-")));
            var historical = powder(99, "BIANCA", 3);
            state.show(
                    List.of(
                            new Layer(
                                    1,
                                    "",
                                    List.of(
                                            new Ingredient(
                                                    historical.id(), new BigDecimal("100"))))),
                    Map.of(historical.id(), historical));
            editor.show(false);
            editor.choices(powders);
            @SuppressWarnings("unchecked")
            var pinned = (ComboBox<PowderRef>) root.lookup("#ton-composition-ingredient-1-powder");
            assertEquals(historical, pinned.getValue());
            assertTrue(
                    pinned.getItems().contains(historical),
                    "A historical revision stays selectable");
        } finally {
            stage.close();
        }
    }

    private PowderRef powder(long id, String color, int yttria) {
        return new PowderRef(
                id,
                id,
                "P-" + id,
                "Polvere " + id,
                color,
                yttria,
                1,
                State.APPROVATA,
                true,
                Map.of());
    }

    private Color color(String color) {
        return Color.web(
                switch (color) {
                    case "BIANCA" -> "#f6f6f0";
                    case "GIALLA" -> "#fff1b8";
                    case "ROSA" -> "#fbe1e8";
                    case "GRIGIA" -> "#e3e7ed";
                    default -> "#edf3f7";
                });
    }
}

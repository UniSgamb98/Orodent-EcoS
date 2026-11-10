package com.orodent.ecos.costi.scenarios;

import static com.orodent.ecos.costi.scenarios.ScenarioTables.*;

import javafx.beans.binding.Bindings;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.function.BiConsumer;

/** Always-visible controls; no hidden double-click editing for powder prices. */
final class ScenarioPriceCells {
    private ScenarioPriceCells() {}

    static TableCell<EditRow, String> mode(BiConsumer<EditRow, String> changed) {
        return new TableCell<>() {
            private final ComboBox<String> choice = new ComboBox<>();
            private EditRow row;
            private boolean updating;

            {
                choice.getItems().setAll("Invariato", "Variazione %", "Prezzo €/kg");
                choice.setMaxWidth(Double.MAX_VALUE);
                choice.getStyleClass().add("scenario-price-mode");
                choice.setOnShowing(
                        e -> {
                            if (row != null) getTableView().getSelectionModel().select(row);
                        });
                choice.setOnAction(
                        e -> {
                            if (!updating && row != null && choice.getValue() != null)
                                changed.accept(row, choice.getValue());
                        });
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                updating = true;
                try {
                    row = empty || getTableRow() == null ? null : getTableRow().getItem();
                    setText(null);
                    setGraphic(row == null ? null : choice);
                    choice.setValue(value);
                    if (row != null) {
                        choice.setId("scenario-price-mode-" + row.id);
                        choice.setAccessibleText("Tipo di modifica per " + row.get(0));
                    }
                } finally {
                    updating = false;
                }
            }
        };
    }

    static TableCell<EditRow, String> value(Runnable changed) {
        return new TableCell<>() {
            private final TextField field = new TextField();
            private final Label unit = new Label();
            private final HBox content = new HBox(5, field, unit);
            private EditRow row;
            private boolean updating;

            {
                HBox.setHgrow(field, Priority.ALWAYS);
                field.setMinWidth(45);
                unit.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                field.getStyleClass().add("scenario-price-value");
                field.focusedProperty()
                        .addListener(
                                (o, b, a) -> {
                                    if (a && row != null)
                                        getTableView().getSelectionModel().select(row);
                                });
                field.textProperty()
                        .addListener(
                                (o, b, a) -> {
                                    if (!updating && row != null && !row.get(3).equals(a)) {
                                        row.cells[3].set(a);
                                        changed.run();
                                    }
                                });
                content.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                EditRow next = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (next == row) return;
                updating = true;
                try {
                    if (row != null) field.textProperty().unbindBidirectional(row.cells[3]);
                    field.disableProperty().unbind();
                    unit.textProperty().unbind();
                    row = next;
                    setText(null);
                    setGraphic(row == null ? null : content);
                    if (row != null) {
                        field.textProperty().bindBidirectional(row.cells[3]);
                        field.disableProperty().bind(row.cells[2].isEqualTo("Invariato"));
                        unit.textProperty()
                                .bind(
                                        Bindings.createStringBinding(
                                                () ->
                                                        row.get(2).equals("Variazione %")
                                                                ? "%"
                                                                : row.get(2).equals("Invariato")
                                                                        ? ""
                                                                        : "€/kg",
                                                row.cells[2]));
                        field.setId("scenario-price-value-" + row.id);
                        field.setAccessibleText("Valore della modifica per " + row.get(0));
                    }
                } finally {
                    updating = false;
                }
            }
        };
    }
}

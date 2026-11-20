package com.orodent.ecos.module.view;

import com.orodent.ecos.core.PowderAppearance;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.Region;

import java.util.function.Function;

/** Consistent whole-width badges without changing row selection or economic/order grouping. */
public final class PowderCells {
    private PowderCells() {}

    public static void color(Region node, String color) {
        if (!node.getStyleClass().contains("powder-tint")) node.getStyleClass().add("powder-tint");
        var appearance = new PowderAppearance(0, "", "", color, null);
        node.setStyle(
                "-fx-background-color:"
                        + appearance.background()
                        + ";-fx-background-radius:5px;"
                        + "-fx-border-color:derive("
                        + appearance.background()
                        + ",-12%);-fx-border-radius:5px;"
                        + "-fx-border-width:1px;-fx-text-fill:#173f59;"
                        + (node.getStyleClass().contains("powder-badge-common")
                                ? "-fx-padding:2px 7px;"
                                : ""));
    }

    public static Label badge(String text, String color) {
        var label = new Label(text);
        label.getStyleClass().add("powder-badge-common");
        label.setMaxWidth(Double.MAX_VALUE);
        color(label, color);
        return label;
    }

    public static <T> void column(
            TableColumn<T, T> column, Function<T, String> text, Function<T, String> color) {
        column.setComparator(
                java.util.Comparator.comparing(
                        text, java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        column.setCellFactory(
                c ->
                        new TableCell<>() {
                            private final Label label = badge("", null);

                            {
                                label.prefWidthProperty().bind(widthProperty().subtract(16));
                            }

                            @Override
                            protected void updateItem(T item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(null);
                                setGraphic(empty || item == null ? null : label);
                                if (!empty && item != null) {
                                    label.setText(text.apply(item));
                                    color(label, color.apply(item));
                                }
                                setTooltip(
                                        empty || item == null
                                                ? null
                                                : new Tooltip(text.apply(item)));
                            }
                        });
    }
}

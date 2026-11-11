package com.orodent.ecos.costi.orders;

import com.orodent.ecos.costi.orders.PowderOrders.*;
import com.orodent.ecos.costi.view.PriceFormats;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

final class OrderTableSupport {
    private OrderTableSupport() {}

    static <T> TableCell<T, T> powderCell(Function<T, Powder> powder, Function<T, String> text) {
        return new TableCell<>() {
            private final Label chip = new Label();

            {
                chip.setMaxWidth(Double.MAX_VALUE);
            }

            @Override
            protected void updateItem(T value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setGraphic(empty || value == null ? null : chip);
                if (!empty && value != null) {
                    Powder p = powder.apply(value);
                    badge(chip, text.apply(value), p);
                    chip.setTooltip(
                            new Tooltip(
                                    text.apply(value)
                                            + (p == null
                                                    ? "\nDa collegare al catalogo TON"
                                                    : "\nColore: " + p.color())));
                }
            }
        };
    }

    static ListCell<Powder> powderOption() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Powder item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    Label label = new Label();
                    badge(label, item.toString(), item);
                    setGraphic(label);
                    setTooltip(new Tooltip(item.toString()));
                }
            }
        };
    }

    static void badge(Label label, String text, Powder powder) {
        label.setText(text);
        String color = powder == null ? "ALTRE" : Objects.toString(powder.color(), "ALTRE");
        label.getStyleClass()
                .setAll(
                        "orders-powder-badge",
                        switch (color) {
                            case "BIANCA" -> "powder-white";
                            case "GIALLA" -> "powder-yellow";
                            case "ROSA" -> "powder-pink";
                            case "GRIGIA" -> "powder-gray";
                            default -> "powder-other";
                        });
    }

    static String date(LocalDate date) {
        return date == null
                ? "—"
                : java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu").format(date);
    }

    static String number(BigDecimal n) {
        return PriceFormats.number(n, 0, 4);
    }

    static Label info(String message) {
        Label label = new Label("ⓘ");
        label.setTooltip(new Tooltip(message));
        return label;
    }

    static <T, V> TableColumn<T, V> column(
            TableView<T> table,
            String name,
            int width,
            Function<T, V> value,
            Function<V, String> text) {
        TableColumn<T, V> col = new TableColumn<>(name);
        if (name.equals("Ordinati") || name.equals("Residuo kg") || name.equals("€/kg"))
            col.getStyleClass().add("orders-number");
        col.setPrefWidth(width);
        col.setMinWidth(width * .72);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(value.apply(c.getValue())));
        col.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(V value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(empty ? null : text.apply(value));
                                setTooltip(empty ? null : new Tooltip(text.apply(value)));
                                setStyle(
                                        value instanceof BigDecimal
                                                ? "-fx-alignment: center-right;"
                                                : value instanceof LocalDate
                                                        ? "-fx-alignment: center;"
                                                        : "");
                            }
                        });
        table.getColumns().add(col);
        return col;
    }
}

package com.orodent.ecos.statistiche.core.components;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.function.Function;

/** Keep sortable domain values separate from the localized text shown in cells. */
public final class TableColumns {
    private TableColumns() { }

    public static <S> TableColumn<S, String> text(String title, Function<S, String> value) {
        TableColumn<S, String> column = create(title, value, Function.identity(), "");
        column.setComparator(Comparator.nullsFirst(java.text.Collator.getInstance()::compare));
        return column;
    }

    public static <S, T extends Comparable<? super T>> TableColumn<S, T> value(
            String title, Function<S, T> value, Function<T, String> format) {
        return create(title, value, format, "—");
    }

    public static <S> TableColumn<S, BigDecimal> numeric(String title, Function<S, BigDecimal> value,
                                                        Function<BigDecimal, String> format) {
        TableColumn<S, BigDecimal> column = value(title, value, format);
        column.setMinWidth(80);
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
        return column;
    }

    private static <S, T extends Comparable<? super T>> TableColumn<S, T> create(
            String title, Function<S, T> value, Function<T, String> format, String missing) {
        TableColumn<S, T> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setComparator(Comparator.nullsFirst(Comparator.naturalOrder()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? missing : format.apply(item));
            }
        });
        return column;
    }
}

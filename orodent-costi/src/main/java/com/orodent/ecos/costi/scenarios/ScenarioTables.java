package com.orodent.ecos.costi.scenarios;

import javafx.beans.property.*;
import javafx.collections.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.*;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;

final class ScenarioTables {
    private ScenarioTables() {}

    static final class EditRow {
        final long id;
        final int version;
        final String label;
        final StringProperty[] cells;

        EditRow(long id, int version, String label, String... values) {
            this.id = id;
            this.version = version;
            this.label = label;
            cells =
                    Arrays.stream(values)
                            .map(SimpleStringProperty::new)
                            .toArray(StringProperty[]::new);
        }

        String get(int index) {
            return cells[index].get();
        }
    }

    static TableView<EditRow> edit(String[] headers, boolean[] editable, Runnable changed) {
        var table = new TableView<EditRow>();
        table.setEditable(true);
        table.setFixedCellSize(36);
        table.setPrefHeight(250);
        table.setPlaceholder(new Label("Nessuna riga. Usa Aggiungi per compilare."));
        for (int i = 0; i < headers.length; i++) {
            final int index = i;
            var col = new TableColumn<EditRow, String>(headers[i]);
            col.setPrefWidth(i == 0 ? 210 : 125);
            col.setCellValueFactory(c -> c.getValue().cells[index]);
            col.setSortable(false);
            if (editable[i]) {
                col.setCellFactory(
                        c ->
                                new TextFieldTableCell<EditRow, String>(
                                        new javafx.util.converter.DefaultStringConverter()) {
                                    private TextField watched;

                                    public void startEdit() {
                                        super.startEdit();
                                        if (getGraphic() instanceof TextField field
                                                && field != watched) {
                                            watched = field;
                                            field.focusedProperty()
                                                    .addListener(
                                                            (o, b, a) -> {
                                                                if (!a && isEditing())
                                                                    commitEdit(field.getText());
                                                            });
                                        }
                                    }
                                });
                col.setOnEditCommit(
                        e -> {
                            e.getRowValue().cells[index].set(e.getNewValue());
                            changed.run();
                        });
            }
            table.getColumns().add(col);
        }
        return table;
    }

    static <T> void col(TableView<T> table, String name, int width, Function<T, String> value) {
        var col = new TableColumn<T, String>(name);
        col.setPrefWidth(width);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(value.apply(c.getValue())));
        col.setSortable(false);
        table.getColumns().add(col);
    }

    static BigDecimal decimal(String text, String name, boolean optional) {
        if (text == null || text.isBlank()) {
            if (optional) return null;
            throw new IllegalArgumentException(name + ": valore obbligatorio.");
        }
        try {
            String value = text.strip().replace(',', '.');
            if (!value.matches("[-+]?\\d+(\\.\\d+)?")) throw new NumberFormatException();
            BigDecimal result = new BigDecimal(value);
            if (result.precision() > 19 || result.scale() > 8) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    name
                            + ": numero valido, massimo 8 decimali e 19 cifre, senza separatori"
                            + " delle migliaia.");
        }
    }

    static String edit(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    static <T> void searchable(ComboBox<T> box, Function<T, String> text) {
        box.setEditable(true);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setConverter(
                new StringConverter<>() {
                    public String toString(T value) {
                        return value == null ? "" : text.apply(value);
                    }

                    public T fromString(String value) {
                        return box.getItems().stream()
                                .filter(item -> text.apply(item).equals(value))
                                .findFirst()
                                .orElse(null);
                    }
                });
        box.setCellFactory(
                l ->
                        new ListCell<>() {
                            protected void updateItem(T item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty || item == null ? null : text.apply(item));
                            }
                        });
    }

    static <T> void values(ComboBox<T> box, List<T> items, T keep) {
        box.setItems(FXCollections.observableArrayList(items));
        box.setValue(items.contains(keep) ? keep : items.isEmpty() ? null : items.getFirst());
    }

    static boolean confirm(javafx.stage.Window owner, String text) {
        Alert alert =
                new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.CANCEL, ButtonType.OK);
        if (owner != null) alert.initOwner(owner);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    static void message(javafx.stage.Window owner, String title, String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        var area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefSize(720, 380);
        alert.getDialogPane().setContent(area);
        alert.showAndWait();
    }
}

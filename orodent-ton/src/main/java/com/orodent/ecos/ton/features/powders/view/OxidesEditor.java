package com.orodent.ecos.ton.features.powders.view;

import com.orodent.ecos.ton.features.powders.model.PowderModel.Oxide;

import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Cells edit their row properties immediately: clicking Save never loses an uncommitted table edit.
 */
final class OxidesEditor extends VBox {
    private final TableView<Row> table = new TableView<>();
    private final Label total = new Label();
    private final Label error = new Label();
    private final SimpleBooleanProperty readOnly = new SimpleBooleanProperty();
    private final Button add = new Button("+ Aggiungi ossido");
    private final Button paste = new Button("Incolla da Excel");

    OxidesEditor() {
        super(12);
        table.setId("ton-oxides-table");
        add.setId("ton-oxide-add");
        paste.setId("ton-oxide-paste");
        add.getStyleClass().add("secondary-button");
        paste.getStyleClass().add("secondary-button");
        add.disableProperty().bind(readOnly);
        paste.disableProperty().bind(readOnly);
        add.setOnAction(
                event -> {
                    table.getItems().add(new Row("", ""));
                    table.scrollTo(table.getItems().size() - 1);
                });
        paste.setOnAction(event -> paste());
        TableColumn<Row, String> formula = new TableColumn<>("Ossido");
        formula.setPrefWidth(260);
        formula.setMinWidth(180);
        formula.setCellValueFactory(cell -> cell.getValue().code);
        formula.setCellFactory(ignored -> new FormulaCell());
        TableColumn<Row, String> percentage = new TableColumn<>("Peso percentuale · wt%");
        percentage.setPrefWidth(240);
        percentage.setMinWidth(180);
        percentage.setCellValueFactory(cell -> cell.getValue().amount);
        percentage.setCellFactory(ignored -> new AmountCell());
        TableColumn<Row, String> remove = new TableColumn<>("");
        remove.setPrefWidth(65);
        remove.setMinWidth(60);
        remove.setMaxWidth(65);
        remove.setCellValueFactory(cell -> cell.getValue().code);
        remove.setCellFactory(
                ignored ->
                        new TableCell<>() {
                            private final Button button = new Button("×");

                            {
                                button.setAccessibleText("Rimuovi ossido");
                                button.disableProperty().bind(readOnly);
                                button.setOnAction(
                                        event -> {
                                            if (getTableRow().getItem() != null)
                                                table.getItems().remove(getTableRow().getItem());
                                        });
                            }

                            @Override
                            protected void updateItem(String value, boolean empty) {
                                super.updateItem(value, empty);
                                setGraphic(empty ? null : button);
                            }
                        });
        table.getColumns().setAll(List.of(formula, percentage, remove));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setMinHeight(140);
        table.setPrefHeight(270);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setPlaceholder(
                new Label("Nessun ossido dichiarato. Aggiungi una riga per iniziare."));
        table.setItems(
                FXCollections.observableArrayList(row -> new Observable[] {row.code, row.amount}));
        table.getItems().addListener((ListChangeListener<Row>) change -> summary());
        total.setId("ton-oxides-total");
        total.getStyleClass().add("ton-powder-total");
        total.setMinHeight(Region.USE_PREF_SIZE);
        error.setId("ton-oxides-error");
        error.setWrapText(true);
        error.getStyleClass().add("ton-error-feedback");
        error.setMinHeight(Region.USE_PREF_SIZE);
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
        Label hint =
                new Label(
                        "Valori in percentuale in peso (wt%). Le analisi parziali sono ammesse; il"
                            + " totale non viene normalizzato automaticamente.");
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        hint.getStyleClass().add("muted-label");
        getChildren().setAll(hint, table, new FlowPane(10, 8, add, paste), total, error);
    }

    void show(List<Oxide> oxides, boolean locked) {
        readOnly.set(locked);
        table.getItems()
                .setAll(
                        oxides.stream()
                                .map(
                                        value ->
                                                new Row(
                                                        PowderFormats.oxide(value.code()),
                                                        PowderFormats.weight(
                                                                value.weightPercent())))
                                .toList());
        summary();
    }

    List<Oxide> values() {
        var result = new ArrayList<Oxide>();
        var codes = new HashSet<String>();
        for (Row row : table.getItems()) {
            if (row.code.get().isBlank() && row.amount.get().isBlank()) continue;
            var value =
                    new Oxide(
                            row.code.get(),
                            PowderFormats.decimal(
                                    row.amount.get(), "Percentuale di " + row.code.get()));
            if (!codes.add(value.code()))
                throw new IllegalArgumentException("Ossido duplicato: " + value.code());
            result.add(value);
        }
        return List.copyOf(result);
    }

    List<String> fingerprint() {
        return table.getItems().stream()
                .map(row -> row.code.get().strip() + "=" + row.amount.get().strip())
                .toList();
    }

    private void summary() {
        try {
            BigDecimal sum =
                    values().stream()
                            .map(Oxide::weightPercent)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            total.setText("Totale dichiarato: " + PowderFormats.weight(sum) + " wt%");
            error.setText(
                    sum.compareTo(BigDecimal.valueOf(100)) > 0
                            ? "Il totale supera il 100 wt%. Puoi conservare la bozza, ma devi"
                                  + " correggerla prima dell'approvazione."
                            : "");
        } catch (IllegalArgumentException invalid) {
            total.setText("Totale da verificare");
            error.setText(invalid.getMessage());
        }
    }

    private void paste() {
        try {
            String content = Clipboard.getSystemClipboard().getString();
            if (content == null || content.isBlank())
                throw new IllegalArgumentException(
                        "Il clipboard è vuoto. Copia due colonne: ossido e percentuale.");
            var pasted = new ArrayList<Oxide>();
            String[] lines = content.strip().split("\\R");
            for (int index = 0; index < lines.length; index++) {
                if (lines[index].isBlank()) continue;
                String[] cells = lines[index].split("[\\t;]", -1);
                if (index == 0 && cells[0].strip().equalsIgnoreCase("ossido")) continue;
                String amount;
                if (cells.length == 2) amount = cells[1];
                else if (cells.length == 3 && cells[1].strip().equalsIgnoreCase("wt%"))
                    amount = cells[2];
                else
                    throw new IllegalArgumentException(
                            "Incolla gli ossidi e una sola colonna di valori per questa polvere."
                                + " Sono ammesse due colonne, oppure ossido / wt% / valore.");
                pasted.add(
                        new Oxide(
                                cells[0],
                                PowderFormats.decimal(
                                        amount, "Percentuale alla riga " + (index + 1))));
            }
            if (pasted.isEmpty())
                throw new IllegalArgumentException(
                        "Non sono presenti righe di ossidi da inserire.");
            var codes = new HashSet<String>();
            values().forEach(value -> codes.add(value.code()));
            for (Oxide value : pasted)
                if (!codes.add(value.code()))
                    throw new IllegalArgumentException("Ossido duplicato: " + value.code());
            Dialog<ButtonType> preview = new Dialog<>();
            preview.setTitle("Anteprima degli ossidi");
            if (getScene() != null) preview.initOwner(getScene().getWindow());
            TableView<Oxide> rows = new TableView<>(FXCollections.observableArrayList(pasted));
            TableColumn<Oxide, String> code = new TableColumn<>("Ossido");
            code.setCellValueFactory(
                    cell -> new ReadOnlyStringWrapper(PowderFormats.oxide(cell.getValue().code())));
            TableColumn<Oxide, String> amount = new TableColumn<>("wt%");
            amount.setCellValueFactory(
                    cell ->
                            new ReadOnlyStringWrapper(
                                    PowderFormats.weight(cell.getValue().weightPercent())));
            rows.getColumns().setAll(List.of(code, amount));
            rows.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            rows.setPrefSize(440, 250);
            preview.getDialogPane()
                    .setContent(
                            new VBox(
                                    12,
                                    new Label(
                                            "Le "
                                                    + pasted.size()
                                                    + " righe verranno aggiunte alla bozza."),
                                    rows));
            ButtonType insert = new ButtonType("Aggiungi righe", ButtonBar.ButtonData.OK_DONE);
            preview.getDialogPane().getButtonTypes().setAll(insert, ButtonType.CANCEL);
            if (preview.showAndWait().orElse(ButtonType.CANCEL) == insert) {
                table.getItems()
                        .removeIf(row -> row.code.get().isBlank() && row.amount.get().isBlank());
                table.getItems()
                        .addAll(
                                pasted.stream()
                                        .map(
                                                value ->
                                                        new Row(
                                                                PowderFormats.oxide(value.code()),
                                                                PowderFormats.weight(
                                                                        value.weightPercent())))
                                        .toList());
            }
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage());
        }
    }

    private static final class Row {
        final StringProperty code;
        final StringProperty amount;

        Row(String code, String amount) {
            this.code = new SimpleStringProperty(code);
            this.amount = new SimpleStringProperty(amount);
        }
    }

    private final class FormulaCell extends TableCell<Row, String> {
        private final ComboBox<String> field =
                new ComboBox<>(FXCollections.observableArrayList(PowderFormats.OXIDES.values()));
        private Row bound;

        FormulaCell() {
            field.setEditable(true);
            field.setMaxWidth(Double.MAX_VALUE);
            field.disableProperty().bind(readOnly);
        }

        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            if (bound != null) field.getEditor().textProperty().unbindBidirectional(bound.code);
            bound = null;
            setGraphic(null);
            if (!empty && getTableRow() != null && getTableRow().getItem() != null) {
                bound = getTableRow().getItem();
                field.setValue(bound.code.get());
                field.getEditor().setText(bound.code.get());
                field.getEditor().setId("ton-oxide-code-" + getIndex());
                field.getEditor()
                        .setAccessibleText("Formula dell'ossido, riga " + (getIndex() + 1));
                field.getEditor().textProperty().bindBidirectional(bound.code);
                setGraphic(field);
            }
        }
    }

    private final class AmountCell extends TableCell<Row, String> {
        private final TextField field = new TextField();
        private Row bound;

        AmountCell() {
            field.setPromptText("5,150");
            field.disableProperty().bind(readOnly);
        }

        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            if (bound != null) field.textProperty().unbindBidirectional(bound.amount);
            bound = null;
            setGraphic(null);
            if (!empty && getTableRow() != null && getTableRow().getItem() != null) {
                bound = getTableRow().getItem();
                field.setText(bound.amount.get());
                field.setId("ton-oxide-percent-" + getIndex());
                field.setAccessibleText("Percentuale in peso, riga " + (getIndex() + 1));
                field.textProperty().bindBidirectional(bound.amount);
                setGraphic(field);
            }
        }
    }
}

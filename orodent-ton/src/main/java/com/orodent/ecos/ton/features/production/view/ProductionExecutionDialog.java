package com.orodent.ecos.ton.features.production.view;

import com.orodent.ecos.ton.features.production.model.ProductionExecution;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.*;

/** Confirms actual counts; partial declarations can be followed by further registrations. */
public final class ProductionExecutionDialog extends Dialog<ProductionExecution.Request> {
    private record Row(ProductionExecution.Row source, TextField quantity) {}

    private final List<Row> rows = new ArrayList<>();
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final TextArea notes = new TextArea();
    private final Label error = TechnicalUi.label("", "ton-error-feedback");

    public ProductionExecutionDialog(Window owner, ProductionExecution.Workspace workspace) {
        if (owner != null) initOwner(owner);
        setTitle("Registra dischi prodotti · " + workspace.prepared().number());
        getDialogPane().setId("ton-production-execution-dialog");
        var table = new TableView<Row>();
        table.setId("ton-production-execution-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        for (var source : workspace.rows()) {
            var input = new TextField("" + source.remaining());
            input.setId("ton-production-actual-" + source.format().article().id());
            input.setDisable(source.remaining() == 0);
            input.setTextFormatter(
                    new TextFormatter<String>(
                            change ->
                                    change.getControlNewText().matches("[0-9]{0,7}")
                                            ? change
                                            : null));
            rows.add(new Row(source, input));
        }
        table.getItems().setAll(rows);
        TechnicalUi.column(table, "Articolo", 240, r -> r.source().format().article().code());
        TechnicalUi.column(table, "Previsti", 100, r -> "" + r.source().format().quantity());
        TechnicalUi.column(table, "Già registrati", 110, r -> "" + r.source().produced());
        var count = new TableColumn<Row, Row>("Prodotti ora");
        count.setCellValueFactory(
                v -> new javafx.beans.property.SimpleObjectProperty<>(v.getValue()));
        count.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(Row row, boolean empty) {
                                super.updateItem(row, empty);
                                setGraphic(empty || row == null ? null : row.quantity());
                            }
                        });
        count.setSortable(false);
        table.getColumns().add(count);
        date.setId("ton-production-execution-date");
        var format =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT);
        date.setConverter(new javafx.util.converter.LocalDateStringConverter(format, format));
        date.setPromptText("gg/mm/aaaa");
        notes.setId("ton-production-execution-notes");
        notes.setPrefRowCount(2);
        notes.setWrapText(true);
        error.setId("ton-production-execution-error");
        error.setWrapText(true);
        var content =
                new VBox(
                        12,
                        TechnicalUi.label(
                                "Indica i dischi realmente prodotti. Puoi registrare quantità"
                                        + " parziali; i pezzi entreranno nella lista da caricare in"
                                        + " forno.",
                                "muted-label"),
                        TechnicalUi.field("Data di produzione", date),
                        table,
                        TechnicalUi.field("Note di produzione", notes),
                        error);
        content.setPrefSize(760, 480);
        VBox.setVgrow(table, Priority.ALWAYS);
        getDialogPane().setContent(content);
        var register = new ButtonType("Registra dischi", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, register);
        getDialogPane().lookupButton(register).setId("ton-production-execution-confirm");
        getDialogPane()
                .lookupButton(register)
                .addEventFilter(
                        ActionEvent.ACTION,
                        e -> {
                            try {
                                request();
                            } catch (IllegalArgumentException invalid) {
                                error.setText(invalid.getMessage());
                                e.consume();
                            }
                        });
        setResultConverter(button -> button == register ? request() : null);
    }

    private ProductionExecution.Request request() {
        var quantities = new LinkedHashMap<Long, Integer>();
        for (var row : rows) {
            int count;
            try {
                count = Integer.parseInt(row.quantity().getText());
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(
                        "Inserisci una quantità intera per ogni articolo.");
            }
            if (count > row.source().remaining())
                throw new IllegalArgumentException(
                        row.source().format().article().code()
                                + ": restano "
                                + row.source().remaining()
                                + " dischi da registrare.");
            quantities.put(row.source().format().article().id(), count);
        }
        LocalDate value;
        try {
            value = date.getConverter().fromString(date.getEditor().getText());
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Data di produzione: usa gg/mm/aaaa.");
        }
        return new ProductionExecution.Request(value, quantities, notes.getText());
    }
}

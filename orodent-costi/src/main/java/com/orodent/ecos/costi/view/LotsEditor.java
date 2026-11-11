package com.orodent.ecos.costi.view;

import com.orodent.ecos.costi.model.PowderPrices;
import com.orodent.ecos.costi.model.PowderPrices.Lot;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

/** Small editable purchase rows; the lot deliberately is not treated as a unique identifier. */
public final class LotsEditor extends VBox {
    public record Input(String code, String quantity, String price) {}

    private final List<Line> lines = new ArrayList<>();
    private final VBox rows = new VBox(7);
    private final Button add = new Button("+ Aggiungi lotto");
    private final Label empty =
            new Label(
                    "Nessun lotto inserito. Usa Aggiungi lotto per dettagliare la disponibilità.");
    private Runnable changed = () -> {};

    public LotsEditor() {
        super(9);
        setId("costi-lots-editor");
        GridPane header = grid();
        String[] headings = {"Lotto", "Quantità kg", "Prezzo €/kg", "Valore €", ""};
        for (int i = 0; i < headings.length; i++) {
            Label label = new Label(headings[i]);
            label.getStyleClass().add("costs-table-heading");
            header.add(label, i, 0);
        }
        add.setId("costi-lot-add");
        add.getStyleClass().add("secondary-button");
        add.setOnAction(
                e -> {
                    if (lines.size() < PowderPrices.MAX_LOTS) {
                        append(new Input("", "", ""));
                        changed.run();
                    }
                });
        Label title = new Label("Lotti di acquisto");
        title.getStyleClass().add("powders-section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox tools = new HBox(10, title, spacer, add);
        tools.setAlignment(Pos.CENTER_LEFT);
        var scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setMinHeight(0);
        scroll.getStyleClass().add("costs-detail-scroll");
        empty.setWrapText(true);
        empty.getStyleClass().add("costs-muted");
        empty.setMaxWidth(330);
        var body = new StackPane(scroll, empty);
        body.setMinHeight(60);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(tools, header, body);
        setMinHeight(0);
    }

    public void onChanged(Runnable action) {
        changed = action;
    }

    public void show(List<Lot> lots) {
        lines.clear();
        rows.getChildren().clear();
        for (Lot lot : lots)
            append(
                    new Input(
                            lot.code(),
                            PriceFormats.edit(lot.quantity()),
                            PriceFormats.edit(lot.unitPrice())));
        reindex();
    }

    public List<Input> inputs() {
        return lines.stream().map(Line::input).toList();
    }

    public List<Lot> values() {
        return lines.stream()
                .map(
                        line -> {
                            Input input = line.input();
                            return new Lot(
                                    input.code(),
                                    PriceFormats.decimal(input.quantity(), "Quantità del lotto"),
                                    PriceFormats.decimal(input.price(), "Prezzo del lotto"));
                        })
                .toList();
    }

    private void append(Input value) {
        Line line = new Line(value);
        lines.add(line);
        rows.getChildren().add(line.root);
        reindex();
    }

    private void reindex() {
        empty.setVisible(lines.isEmpty());
        empty.setManaged(lines.isEmpty());
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            String id = "costi-lot-" + i;
            line.code.setId(id + "-code");
            line.quantity.setId(id + "-quantity");
            line.price.setId(id + "-price");
            line.remove.setId(id + "-remove");
        }
        add.setDisable(lines.size() >= PowderPrices.MAX_LOTS);
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setAlignment(Pos.CENTER_LEFT);
        for (double percent : new double[] {28, 20, 22, 24, 6}) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(percent);
            grid.getColumnConstraints().add(c);
        }
        grid.setMaxWidth(Double.MAX_VALUE);
        return grid;
    }

    private final class Line {
        final GridPane root = grid();
        final TextField code = new TextField(), quantity = new TextField(), price = new TextField();
        final Label value = new Label();
        final Button remove = new Button("×");

        Line(Input input) {
            code.setPromptText("Non indicato");
            quantity.setPromptText("kg");
            price.setPromptText("€/kg");
            code.setAccessibleText("Lotto");
            quantity.setAccessibleText("Quantità in kg");
            price.setAccessibleText("Prezzo in euro per kg");
            code.setText(input.code());
            quantity.setText(input.quantity());
            price.setText(input.price());
            for (TextField field : List.of(code, quantity, price)) {
                field.setMinWidth(0);
                field.setMaxWidth(Double.MAX_VALUE);
                field.textProperty()
                        .addListener(
                                (o, b, a) -> {
                                    updateValue();
                                    changed.run();
                                });
            }
            value.getStyleClass().add("costs-lot-value");
            value.setMaxWidth(Double.MAX_VALUE);
            value.setMinWidth(0);
            value.setTooltip(
                    new Tooltip("Valore calcolato; i prezzi non vengono arrotondati nei calcoli."));
            remove.setAccessibleText("Elimina riga");
            remove.getStyleClass().add("costs-delete");
            remove.setOnAction(
                    e -> {
                        lines.remove(this);
                        rows.getChildren().remove(root);
                        reindex();
                        changed.run();
                    });
            root.addRow(0, code, quantity, price, value, remove);
            updateValue();
        }

        Input input() {
            return new Input(code.getText(), quantity.getText(), price.getText());
        }

        void updateValue() {
            try {
                var q = PriceFormats.decimal(quantity.getText(), "Quantità");
                var p = PriceFormats.decimal(price.getText(), "Prezzo");
                value.setText(
                        q == null || p == null || q.signum() < 0 || p.signum() < 0
                                ? "—"
                                : PriceFormats.currency(q.multiply(p)));
            } catch (IllegalArgumentException invalid) {
                value.setText("Da verificare");
            }
        }
    }
}

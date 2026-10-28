package com.orodent.ecos.ton.features.diskmodels.view;

import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public final class LayersEditor extends VBox {
    private final List<TextField> rows = new ArrayList<>();
    private final VBox content = new VBox(8);
    private final Label total = TechnicalUi.label("", "ton-model-total");
    private final Button
            add = TechnicalUi.button("+ Aggiungi strato", "ton-model-layer-add", false),
            distribute =
                    TechnicalUi.button(
                            "Distribuisci equamente", "ton-model-layer-distribute", false);
    private final Runnable changed;
    private boolean frozen;

    LayersEditor(Runnable changed) {
        super(15);
        this.changed = changed;
        total.setId("ton-model-layer-total");
        getChildren().addAll(content, total, new FlowPane(10, 8, add, distribute));
        add.setOnAction(
                e -> {
                    append("");
                    layoutRows();
                    changed.run();
                });
    }

    void show(List<BigDecimal> values, boolean frozen) {
        this.frozen = frozen;
        rows.clear();
        for (BigDecimal value : values) append(TechnicalUi.number(value));
        layoutRows();
        add.setDisable(frozen);
        distribute.setDisable(frozen || rows.isEmpty());
    }

    private void append(String value) {
        TextField field = TechnicalUi.input("", "Percentuale dello strato");
        field.setText(value);
        field.setPrefWidth(130);
        field.setMaxWidth(150);
        field.textProperty()
                .addListener(
                        (o, a, b) -> {
                            updateTotal();
                            changed.run();
                        });
        rows.add(field);
    }

    private void layoutRows() {
        content.getChildren().clear();
        for (int i = 0; i < rows.size(); i++) {
            TextField field = rows.get(i);
            field.setId("ton-model-layer-" + (i + 1));
            field.setDisable(frozen);
            var up = TechnicalUi.button("↑", "ton-model-layer-up-" + (i + 1), false);
            up.setAccessibleText("Sposta strato verso l'alto");
            up.setDisable(frozen || i == 0);
            up.setOnAction(e -> move(field, -1));
            var down = TechnicalUi.button("↓", "ton-model-layer-down-" + (i + 1), false);
            down.setAccessibleText("Sposta strato verso il basso");
            down.setDisable(frozen || i == rows.size() - 1);
            down.setOnAction(e -> move(field, 1));
            var remove = TechnicalUi.button("×", "ton-model-layer-remove-" + (i + 1), false);
            remove.setAccessibleText("Rimuovi strato");
            for (Button button : List.of(up, down, remove))
                button.getStyleClass().add("ton-model-row-action");
            remove.setDisable(frozen || rows.size() == 1);
            remove.setOnAction(
                    e -> {
                        rows.remove(field);
                        layoutRows();
                        changed.run();
                    });
            var spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            var row =
                    new HBox(
                            10,
                            new Label("Strato " + (i + 1)),
                            spacer,
                            field,
                            new Label("%"),
                            up,
                            down,
                            remove);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            content.getChildren().add(row);
        }
        distribute.setDisable(frozen || rows.isEmpty());
        updateTotal();
    }

    private void move(TextField field, int amount) {
        int i = rows.indexOf(field);
        Collections.swap(rows, i, i + amount);
        layoutRows();
        changed.run();
    }

    public void distribute() {
        if (rows.isEmpty()) return;
        BigDecimal share =
                BigDecimal.valueOf(100)
                        .divide(BigDecimal.valueOf(rows.size()), 4, RoundingMode.DOWN);
        for (int i = 0; i < rows.size(); i++)
            rows.get(i)
                    .setText(
                            TechnicalUi.number(
                                    i == rows.size() - 1
                                            ? BigDecimal.valueOf(100)
                                                    .subtract(share.multiply(BigDecimal.valueOf(i)))
                                            : share));
        changed.run();
    }

    private void updateTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        boolean valid = !rows.isEmpty();
        for (TextField f : rows) {
            BigDecimal n = TechnicalUi.preview(f.getText());
            if (n == null || n.signum() <= 0 || n.scale() > 4) valid = false;
            if (n != null) sum = sum.add(n);
        }
        valid &= sum.compareTo(BigDecimal.valueOf(100)) == 0;
        total.setText(
                (valid ? "✓ Totale degli strati: " : "Totale da completare: ")
                        + TechnicalUi.number(sum)
                        + "%");
        total.getStyleClass().remove("ton-model-invalid");
        if (!valid) total.getStyleClass().add("ton-model-invalid");
    }

    public List<BigDecimal> data() {
        var result = new ArrayList<BigDecimal>();
        for (int i = 0; i < rows.size(); i++)
            result.add(TechnicalUi.decimal(rows.get(i).getText(), "Percentuale strato " + (i + 1)));
        return result;
    }

    public List<String> texts() {
        return rows.stream().map(TextField::getText).toList();
    }

    public Button distributeButton() {
        return distribute;
    }

    void dispose() {
        add.setOnAction(null);
        distribute.setOnAction(null);
        content.getChildren().clear();
        rows.clear();
    }
}

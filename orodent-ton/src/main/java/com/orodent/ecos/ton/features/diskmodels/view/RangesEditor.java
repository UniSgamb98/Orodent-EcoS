package com.orodent.ecos.ton.features.diskmodels.view;

import com.orodent.ecos.ton.features.diskmodels.model.DiskModels.Range;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

final class RangesEditor extends VBox {
    private final List<List<TextField>> rows = new ArrayList<>();
    private final VBox content = new VBox(10);
    private final Label feedback = TechnicalUi.label("", "muted-label");
    private final Button add =
            TechnicalUi.button("+ Aggiungi fascia", "ton-model-range-add", false);
    private final Runnable changed;
    private boolean frozen;

    RangesEditor(Runnable changed) {
        super(15);
        this.changed = changed;
        feedback.setId("ton-model-range-feedback");
        getChildren().addAll(content, add, feedback);
        add.setOnAction(
                e -> {
                    append(List.of("", "", "", ""));
                    layoutRows();
                    changed.run();
                });
    }

    void show(List<Range> ranges, boolean frozen) {
        this.frozen = frozen;
        rows.clear();
        for (Range r : ranges)
            append(
                    List.of(
                            TechnicalUi.number(r.from()),
                            TechnicalUi.number(r.to()),
                            TechnicalUi.number(r.top()),
                            TechnicalUi.number(r.bottom())));
        layoutRows();
        add.setDisable(frozen);
    }

    private void append(List<String> values) {
        var fields = new ArrayList<TextField>();
        for (int i = 0; i < 4; i++) {
            var field = TechnicalUi.input("", "Valore in mm");
            field.setText(values.get(i));
            field.setMinWidth(45);
            field.textProperty()
                    .addListener(
                            (o, a, b) -> {
                                validate();
                                changed.run();
                            });
            fields.add(field);
        }
        rows.add(fields);
    }

    private void layoutRows() {
        content.getChildren().clear();
        for (int row = 0; row < rows.size(); row++) {
            var fields = rows.get(row);
            var grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(7);
            grid.setMinWidth(0);
            String[] titles = {
                "Da, incluso · mm", "Fino a, escluso · mm", "Superiore · mm", "Inferiore · mm"
            };
            String[] keys = {"from", "to", "top", "bottom"};
            for (int i = 0; i < 4; i++) {
                var c = new ColumnConstraints();
                c.setPercentWidth(23);
                c.setHgrow(Priority.ALWAYS);
                grid.getColumnConstraints().add(c);
                fields.get(i).setId("ton-model-range-" + (row + 1) + "-" + keys[i]);
                fields.get(i).setDisable(frozen);
                grid.add(TechnicalUi.field(titles[i], fields.get(i)), i, 0);
            }
            var remove = TechnicalUi.button("×", "ton-model-range-remove-" + (row + 1), false);
            remove.setAccessibleText("Rimuovi fascia");
            remove.setDisable(frozen);
            remove.setOnAction(
                    e -> {
                        rows.remove(fields);
                        layoutRows();
                        changed.run();
                    });
            grid.add(remove, 4, 0);
            content.getChildren().add(grid);
        }
        validate();
    }

    List<Range> data() {
        var result = new ArrayList<Range>();
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            String prefix = "Fascia " + (i + 1) + " · ";
            result.add(
                    new Range(
                            TechnicalUi.decimal(r.get(0).getText(), prefix + "inizio"),
                            TechnicalUi.decimal(r.get(1).getText(), prefix + "fine"),
                            TechnicalUi.decimal(r.get(2).getText(), prefix + "superiore"),
                            TechnicalUi.decimal(r.get(3).getText(), prefix + "inferiore")));
        }
        return result;
    }

    List<String> texts() {
        return rows.stream().flatMap(List::stream).map(TextField::getText).toList();
    }

    private void validate() {
        feedback.getStyleClass().remove("ton-error-feedback");
        try {
            var sorted = data().stream().sorted(Comparator.comparing(Range::from)).toList();
            for (int i = 1; i < sorted.size(); i++)
                if (sorted.get(i).from().compareTo(sorted.get(i - 1).to()) < 0)
                    throw new IllegalArgumentException("Le fasce di altezza si sovrappongono.");
            feedback.setText(
                    rows.isEmpty()
                            ? "Nessuna eccezione: vengono usati i valori predefiniti."
                            : "✓ Fasce valide, senza sovrapposizioni.");
        } catch (IllegalArgumentException invalid) {
            feedback.setText(invalid.getMessage());
            feedback.getStyleClass().add("ton-error-feedback");
        }
    }

    void dispose() {
        add.setOnAction(null);
        content.getChildren().clear();
        rows.clear();
    }
}

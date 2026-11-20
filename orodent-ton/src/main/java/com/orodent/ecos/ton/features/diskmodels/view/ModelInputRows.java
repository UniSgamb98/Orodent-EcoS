package com.orodent.ecos.ton.features.diskmodels.view;

import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.*;

import java.util.List;

/** Aligned headings and compact model input rows share the same column geometry. */
final class ModelInputRows {
    static final double HEIGHT = 34;

    private ModelInputRows() {}

    static HBox headings(String id, double[] widths, String... labels) {
        var values =
                java.util.Arrays.stream(labels)
                        .map(s -> (Node) TechnicalUi.label(s, "muted-label"))
                        .toList();
        var spacer = new Region();
        spacer.setMinWidth(HEIGHT);
        spacer.setPrefWidth(HEIGHT);
        spacer.setMaxWidth(HEIGHT);
        var line = line(widths, values, spacer);
        line.setId(id);
        line.getStyleClass().add("ton-model-input-headings");
        return line;
    }

    static HBox row(String id, double[] widths, List<? extends Region> fields, Button remove) {
        for (var field : fields) {
            field.setMinWidth(0);
            field.setMaxWidth(Double.MAX_VALUE);
            field.setMinHeight(HEIGHT);
            field.setPrefHeight(HEIGHT);
            field.setMaxHeight(HEIGHT);
        }
        remove.setMinSize(HEIGHT, HEIGHT);
        remove.setPrefSize(HEIGHT, HEIGHT);
        remove.setMaxSize(HEIGHT, HEIGHT);
        remove.getStyleClass().add("ton-model-row-action");
        var line = line(widths, fields, remove);
        line.setId(id);
        line.getStyleClass().add("ton-model-input-row");
        return line;
    }

    private static HBox line(double[] widths, List<? extends Node> fields, Node action) {
        var grid = new GridPane();
        grid.setHgap(10);
        grid.setMinWidth(0);
        for (int i = 0; i < widths.length; i++) {
            var column = new ColumnConstraints();
            column.setPercentWidth(widths[i]);
            grid.getColumnConstraints().add(column);
            grid.add(fields.get(i), i, 0);
        }
        HBox.setHgrow(grid, Priority.ALWAYS);
        var line = new HBox(10, grid, action);
        line.setAlignment(Pos.CENTER_LEFT);
        line.setMinWidth(0);
        return line;
    }
}

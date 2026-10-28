package com.orodent.ecos.ton.features.diskmodels.view;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.*;

/** Schematic proportions only. No assumptions about mass or nominal/pressed heights. */
final class DiskModelPreview extends VBox {
    private static final String[] COLORS = {
        "#d2e5ed", "#b5d3e1", "#91b9ce", "#699bb9", "#bdd9d3", "#8cb8ad"
    };
    private final VBox stack = new VBox(2);
    private final Label top = new Label(), bottom = new Label(), diameter = new Label();

    DiskModelPreview() {
        super(8);
        setMinWidth(0);
        setMaxWidth(310);
        getStyleClass().add("ton-model-panel");
        setId("ton-model-preview");
        top.getStyleClass().add("ton-model-overmaterial");
        bottom.getStyleClass().add("ton-model-overmaterial");
        for (Label l : List.of(top, bottom)) {
            l.setWrapText(true);
            l.setMaxWidth(Double.MAX_VALUE);
        }
        stack.setId("ton-model-preview-stack");
        stack.setMinWidth(0);
        getChildren()
                .addAll(
                        DiskUi.label("Anteprima del modello", "ton-model-panel-title"),
                        new Label("SUPERIORE"),
                        top,
                        stack,
                        bottom,
                        new Label("INFERIORE"),
                        diameter,
                        DiskUi.label(
                                "Colori e sovramateriali schematici. Nessun calcolo di pesi o quote"
                                    + " produttive.",
                                "ton-model-caption"));
    }

    void update(String topText, String bottomText, String diameterText, List<String> values) {
        top.setText("Sovramateriale superiore · " + value(topText) + " mm");
        bottom.setText("Sovramateriale inferiore · " + value(bottomText) + " mm");
        diameter.setText("Diametro pressatura · " + value(diameterText) + " mm");
        stack.getChildren().clear();
        List<BigDecimal> parsed = values.stream().map(DiskUi::preview).toList();
        if (parsed.isEmpty() || parsed.stream().anyMatch(v -> v == null || v.signum() <= 0)) {
            stack.getChildren()
                    .add(
                            DiskUi.label(
                                    "Completa le percentuali per vedere la sezione.",
                                    "muted-label"));
            return;
        }
        BigDecimal total = parsed.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.valueOf(100)) > 0) {
            stack.getChildren()
                    .add(
                            DiskUi.label(
                                    "Il totale supera 100%: correggi le percentuali.",
                                    "ton-error-feedback"));
            return;
        }
        for (int i = 0; i < parsed.size(); i++)
            segment(
                    "Strato " + (i + 1) + " · " + DiskUi.number(parsed.get(i)) + "%",
                    parsed.get(i),
                    COLORS[i % COLORS.length]);
        if (total.compareTo(BigDecimal.valueOf(100)) < 0)
            segment(
                    "Da assegnare · "
                            + DiskUi.number(BigDecimal.valueOf(100).subtract(total))
                            + "%",
                    BigDecimal.valueOf(100).subtract(total),
                    "#f0f4f7");
    }

    private void segment(String text, BigDecimal percentage, String color) {
        double height = percentage.doubleValue() * 1.8;
        var label = new Label(text);
        label.setStyle("-fx-font-size: 11px;");
        label.setVisible(height >= 19);
        var panel = new StackPane(label);
        panel.setMinHeight(height);
        panel.setPrefHeight(height);
        panel.setMaxHeight(height);
        panel.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2px;");
        Tooltip.install(panel, new Tooltip(text));
        stack.getChildren().add(panel);
    }

    private static String value(String text) {
        BigDecimal n = DiskUi.preview(text);
        return n == null ? "—" : DiskUi.number(n);
    }
}

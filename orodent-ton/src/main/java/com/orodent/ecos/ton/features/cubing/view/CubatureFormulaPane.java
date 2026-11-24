package com.orodent.ecos.ton.features.cubing.view;

import com.orodent.ecos.core.math.FormulaProgram;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;

public final class CubatureFormulaPane extends VBox {
    private static final String ID = "ton-cubature-rules";
    public final TextArea source = new TextArea();
    public final TextField diameter = TechnicalUi.input(ID + "-test-d", "Diametro"),
            height = TechnicalUi.input(ID + "-test-h", "Altezza media"),
            weight = TechnicalUi.input(ID + "-test-p", "Peso");
    public final Button test = TechnicalUi.button("Prova formule", ID + "-test", false);

    private record Output(String name, BigDecimal value) {}

    private final TableView<Output> results = new TableView<>();
    private final Label available = TechnicalUi.label("", "muted-label"),
            error = TechnicalUi.label("", "ton-error-feedback");

    public CubatureFormulaPane() {
        super(12);
        source.setId(ID + "-formulas");
        source.setPromptText("Hmedia + D = a1\na1 * P = risultato");
        source.setWrapText(false);
        source.setPrefRowCount(11);
        source.setStyle("-fx-font-family: monospace; -fx-font-size: 14px;");
        VBox.setVgrow(source, Priority.ALWAYS);
        diameter.setText("98");
        height.setText("16");
        weight.setText("400");
        for (var field : java.util.List.of(diameter, height, weight)) {
            field.setPrefWidth(145);
            field.setMaxWidth(145);
        }
        var inputs =
                new FlowPane(
                        12,
                        8,
                        TechnicalUi.field("D · diametro · mm", diameter),
                        TechnicalUi.field("Hmedia · altezza · mm", height),
                        TechnicalUi.field("P · peso · g", weight),
                        test);
        inputs.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
        results.setId(ID + "-test-results");
        results.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TechnicalUi.column(results, "Risultato", 160, Output::name);
        TechnicalUi.column(results, "Valore", 320, v -> TechnicalUi.number(v.value()));
        results.setPrefHeight(185);
        results.setMinHeight(110);
        results.setPlaceholder(new Label("Inserisci valori di prova e premi Prova formule."));
        error.setId(ID + "-formula-error");
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
        getChildren()
                .addAll(
                        TechnicalUi.label("Formule · una per riga", "ton-technical-panel-title"),
                        TechnicalUi.label(
                                "Scrivi espressione = risultato, oppure risultato = espressione."
                                    + " Operatori: + − * / ^, parentesi, PI, sqrt(), cbrt(), abs()."
                                    + " Decimali con virgola o punto; # per i commenti.",
                                "muted-label"),
                        available,
                        source,
                        TechnicalUi.label("Valori di prova", "ton-technical-panel-title"),
                        inputs,
                        error,
                        results);
        source.textProperty().addListener((o, a, b) -> clearResults());
        for (var input : java.util.List.of(diameter, height, weight))
            input.textProperty().addListener((o, a, b) -> clearResults());
        test.setOnAction(e -> runTest());
        clearResults();
    }

    private void clearResults() {
        results.getItems().clear();
        error.setText("");
        String outputs = "";
        try {
            outputs =
                    " · Risultati: "
                            + String.join(", ", FormulaProgram.compile(source.getText()).outputs());
        } catch (IllegalArgumentException invalid) {
        }
        available.setText("Variabili iniziali: D, Hmedia, P · Costante: PI" + outputs);
    }

    private void runTest() {
        results.getItems().clear();
        error.setText("");
        try {
            var values =
                    FormulaProgram.compile(source.getText())
                            .evaluate(
                                    TechnicalUi.decimal(diameter.getText(), "Diametro"),
                                    TechnicalUi.decimal(height.getText(), "Altezza media"),
                                    TechnicalUi.decimal(weight.getText(), "Peso"));
            values.forEach((name, value) -> results.getItems().add(new Output(name, value)));
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage());
        }
    }

    public void editable(boolean editable) {
        source.setEditable(editable);
    }

    public void dispose() {
        test.setOnAction(null);
    }
}

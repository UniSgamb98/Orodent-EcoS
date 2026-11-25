package com.orodent.ecos.ton.features.firingprograms.view;

import static com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms.*;

import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.geometry.*;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.*;

/** Ordered step editor. The initial temperature belongs only to the illustrative preview. */
public final class FiringCyclePane extends VBox {
    private static final String ID = "ton-firing-programs";

    private static final class Row {
        final Label number = new Label();
        final TextField temperature = new TextField(),
                ramp = new TextField("0.00"),
                hold = new TextField("0.00");
        final Button up = TechnicalUi.button("↑", null, false),
                down = TechnicalUi.button("↓", null, false),
                remove = TechnicalUi.button("×", null, false);
        final GridPane pane = columns();

        Row() {
            for (var field : List.of(temperature, ramp, hold)) {
                field.setMinWidth(0);
                field.setMaxWidth(Double.MAX_VALUE);
            }
            temperature.setPromptText("°C");
            ramp.setPromptText("ore.minuti");
            hold.setPromptText("ore.minuti");
            var buttons = new HBox(5, up, down, remove);
            buttons.setAlignment(Pos.CENTER_RIGHT);
            for (var button : List.of(up, down, remove)) {
                button.setMinSize(30, 32);
                button.setPrefSize(30, 32);
                button.setMaxSize(30, 32);
                button.setStyle("-fx-padding: 0;");
            }
            up.setTooltip(new Tooltip("Sposta step su"));
            down.setTooltip(new Tooltip("Sposta step giù"));
            remove.setTooltip(new Tooltip("Rimuovi step"));
            pane.getStyleClass().add("ton-program-step");
            pane.setAlignment(Pos.CENTER_LEFT);
            pane.addRow(0, number, temperature, ramp, hold, buttons);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final VBox stepList = new VBox(10);
    private final Button add = TechnicalUi.button("+ Aggiungi step", ID + "-add-step", false);
    private final TextField initial =
            TechnicalUi.input(ID + "-initial-temperature", "Temperatura iniziale di esempio");
    private final Label total = TechnicalUi.label("", "ton-technical-panel-title"),
            error = TechnicalUi.label("", "ton-error-feedback");
    private final NumberAxis timeAxis = new NumberAxis(), temperatureAxis = new NumberAxis();
    private final LineChart<Number, Number> chart = new LineChart<>(timeAxis, temperatureAxis);
    private boolean editable = true, loading;

    public FiringCyclePane() {
        super(14);
        setMinWidth(0);
        var headers = columns();
        headers.getStyleClass().add("ton-program-step-header");
        headers.addRow(
                0,
                new Label("Step"),
                new Label("Temperatura finale · °C"),
                new Label("Tempo di rampa · h.mm"),
                new Label("Mantenimento · h.mm"),
                new Region());
        headers.getChildren()
                .forEach(
                        node -> {
                            if (node instanceof Label label) label.setWrapText(true);
                        });
        var instructions =
                TechnicalUi.label(
                        "Tempi in ore.minuti: 1.30 = 1 ora e 30 minuti; 0.05 = 5 minuti. I minuti"
                            + " vanno da 00 a 59. Usa 0.00 per un tempo nullo.",
                        "muted-label");
        var edit = new VBox(12, headers, stepList, add);
        edit.setMinWidth(0);
        add.setOnAction(
                e -> {
                    if (rows.size() >= 200) return;
                    addRow(null);
                    renderRows();
                    preview();
                });
        initial.setText("20");
        initial.setMaxWidth(160);
        initial.textProperty().addListener((o, a, b) -> preview());
        chart.setId(ID + "-chart");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(false);
        chart.setMinSize(0, 220);
        chart.setPrefHeight(300);
        timeAxis.setLabel("Tempo · ore.minuti");
        timeAxis.setAutoRanging(false);
        timeAxis.setLowerBound(0);
        timeAxis.setUpperBound(1);
        timeAxis.setTickUnit(1);
        timeAxis.setTickLabelFormatter(
                new javafx.util.StringConverter<>() {
                    public String toString(Number value) {
                        return time(Math.max(0, Math.round(value.doubleValue())));
                    }

                    public Number fromString(String text) {
                        return parseTime(text, "Tempo");
                    }
                });
        temperatureAxis.setLabel("Temperatura · °C");
        total.setId(ID + "-duration");
        error.setId(ID + "-cycle-error");
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
        var visual =
                TechnicalUi.panel(
                        "Anteprima del ciclo",
                        "La temperatura iniziale è un valore di esempio, modificabile per la"
                            + " prova.",
                        TechnicalUi.field("Temperatura iniziale di esempio · °C", initial),
                        total,
                        chart);
        visual.setMinWidth(0);
        var grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(18);
        grid.setMinWidth(0);
        Runnable layout =
                () -> {
                    int cols = grid.getWidth() >= 1000 ? 2 : 1;
                    if (grid.getColumnConstraints().size() == cols) return;
                    grid.getChildren().clear();
                    grid.getColumnConstraints().clear();
                    if (cols == 2) {
                        var left = new ColumnConstraints();
                        left.setPercentWidth(64);
                        left.setHgrow(Priority.ALWAYS);
                        var right = new ColumnConstraints();
                        right.setPercentWidth(36);
                        right.setHgrow(Priority.ALWAYS);
                        grid.getColumnConstraints().addAll(left, right);
                        grid.add(edit, 0, 0);
                        grid.add(visual, 1, 0);
                    } else {
                        var only = new ColumnConstraints();
                        only.setPercentWidth(100);
                        only.setHgrow(Priority.ALWAYS);
                        grid.getColumnConstraints().add(only);
                        grid.add(edit, 0, 0);
                        grid.add(visual, 0, 1);
                    }
                };
        grid.widthProperty().addListener((o, a, b) -> layout.run());
        layout.run();
        getChildren().addAll(instructions, grid, error);
    }

    private static GridPane columns() {
        var pane = new GridPane();
        pane.setHgap(10);
        pane.setMinWidth(0);
        var index = new ColumnConstraints(35);
        var actions = new ColumnConstraints(100);
        pane.getColumnConstraints().add(index);
        for (int i = 0; i < 3; i++) {
            var field = new ColumnConstraints();
            field.setHgrow(Priority.ALWAYS);
            field.setPrefWidth(1);
            field.setFillWidth(true);
            field.setMinWidth(40);
            pane.getColumnConstraints().add(field);
        }
        pane.getColumnConstraints().add(actions);
        return pane;
    }

    private void addRow(Step step) {
        var row = new Row();
        if (step != null) {
            row.temperature.setText(TechnicalUi.number(step.temperature()));
            row.ramp.setText(time(step.rampMinutes()));
            row.hold.setText(time(step.holdMinutes()));
        }
        rows.add(row);
        for (var field : List.of(row.temperature, row.ramp, row.hold))
            field.textProperty().addListener((o, a, b) -> preview());
        row.up.setOnAction(e -> move(row, -1));
        row.down.setOnAction(e -> move(row, 1));
        row.remove.setOnAction(
                e -> {
                    rows.remove(row);
                    renderRows();
                    preview();
                });
    }

    private void move(Row row, int delta) {
        int index = rows.indexOf(row), next = index + delta;
        if (next < 0 || next >= rows.size()) return;
        Collections.swap(rows, index, next);
        renderRows();
        preview();
    }

    private void renderRows() {
        stepList.getChildren().setAll(rows.stream().map(r -> r.pane).toList());
        add.setDisable(!editable || rows.size() >= 200);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            row.number.setText("" + (i + 1));
            row.temperature.setId(ID + "-step-" + (i + 1) + "-temperature");
            row.ramp.setId(ID + "-step-" + (i + 1) + "-ramp");
            row.hold.setId(ID + "-step-" + (i + 1) + "-hold");
            row.temperature.setAccessibleText("Step " + (i + 1) + " temperatura finale");
            row.ramp.setAccessibleText("Step " + (i + 1) + " tempo di rampa ore.minuti");
            row.hold.setAccessibleText("Step " + (i + 1) + " mantenimento ore.minuti");
            row.up.setId(ID + "-step-" + (i + 1) + "-up");
            row.down.setId(ID + "-step-" + (i + 1) + "-down");
            row.remove.setId(ID + "-step-" + (i + 1) + "-remove");
            for (var field : List.of(row.temperature, row.ramp, row.hold))
                field.setEditable(editable);
            for (var button : List.of(row.up, row.down, row.remove))
                TechnicalUi.visible(button, editable);
            row.up.setDisable(i == 0);
            row.down.setDisable(i == rows.size() - 1);
        }
    }

    public List<Step> steps() {
        var steps = new ArrayList<Step>();
        int i = 0;
        for (var row : rows) {
            String label = "Step " + (++i);
            steps.add(
                    new Step(
                            TechnicalUi.decimal(
                                    row.temperature.getText(), label + " · temperatura finale"),
                            parseTime(row.ramp.getText(), label + " · rampa"),
                            parseTime(row.hold.getText(), label + " · mantenimento")));
        }
        return List.copyOf(steps);
    }

    private void preview() {
        if (loading) return;
        chart.getData().clear();
        error.setText("");
        total.setText("Durata totale: —");
        try {
            var steps = steps();
            long duration = steps.stream().mapToLong(Step::minutes).sum();
            total.setText("Durata totale: " + time(duration) + " · " + steps.size() + " step");
            if (steps.isEmpty()) return;
            BigDecimal start =
                    TechnicalUi.decimal(initial.getText(), "Temperatura iniziale di esempio");
            if (start.compareTo(new BigDecimal("99999.99")) > 0)
                throw new IllegalArgumentException(
                        "Temperatura iniziale di esempio troppo grande.");
            var series = new XYChart.Series<Number, Number>();
            long t = 0;
            series.getData().add(new XYChart.Data<>(t, start));
            for (var step : steps) {
                t += step.rampMinutes();
                series.getData().add(new XYChart.Data<>(t, step.temperature()));
                t += step.holdMinutes();
                series.getData().add(new XYChart.Data<>(t, step.temperature()));
            }
            timeAxis.setUpperBound(Math.max(1, duration));
            timeAxis.setTickUnit(Math.max(1, Math.ceil(duration / 6.0)));
            chart.getData().add(series);
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage());
        }
    }

    public void show(List<Step> steps, boolean newProgram) {
        loading = true;
        try {
            rows.clear();
            for (var step : steps) addRow(step);
            if (newProgram) addRow(null);
            renderRows();
        } finally {
            loading = false;
        }
        preview();
        if (newProgram) error.setText("");
    }

    public void editable(boolean value) {
        editable = value;
        renderRows();
    }

    public List<String> fingerprint() {
        var result = new ArrayList<String>();
        for (var row : rows)
            result.addAll(
                    List.of(row.temperature.getText(), row.ramp.getText(), row.hold.getText()));
        return List.copyOf(result);
    }

    public void dispose() {
        add.setOnAction(null);
        chart.getData().clear();
    }
}

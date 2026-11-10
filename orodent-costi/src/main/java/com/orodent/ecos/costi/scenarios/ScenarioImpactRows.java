package com.orodent.ecos.costi.scenarios;

import static com.orodent.ecos.costi.scenarios.ScenarioPresentation.*;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Consumer;

/** Paired horizontal bars with exact labels, a common scale and bounded rendering. */
final class ScenarioImpactRows extends VBox {
    private static final int PAGE_SIZE = 12;
    private final VBox rows = new VBox(8);
    private final Label pageLabel = new Label();
    private final Button previous = new Button("← Precedenti"), next = new Button("Successivi →");
    private List<ScenarioComparison.Display> items = List.of();
    private Consumer<ScenarioComparison.Display> open = r -> {};
    private int page;

    ScenarioImpactRows() {
        setSpacing(12);
        setId("scenarios-impact-rows");
        previous.setId("scenarios-comparison-previous");
        next.setId("scenarios-comparison-next");
        pageLabel.setId("scenarios-comparison-page");
        var legend =
                new HBox(
                        16,
                        legend("Base", "impact-base"),
                        legend("Scenario", "impact-scenario"),
                        new Label("Stessa scala per tutte le righe · €/articolo"));
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getStyleClass().add("impact-caption");
        previous.setOnAction(
                e -> {
                    page--;
                    render();
                });
        next.setOnAction(
                e -> {
                    page++;
                    render();
                });
        var pages = new HBox(12, previous, pageLabel, next);
        pages.setAlignment(Pos.CENTER_RIGHT);
        getChildren().addAll(legend, rows, pages);
    }

    void show(List<ScenarioComparison.Display> items, Consumer<ScenarioComparison.Display> open) {
        this.items = List.copyOf(items);
        this.open = open;
        page = 0;
        render();
    }

    private void render() {
        rows.getChildren().clear();
        // Shared across pages too; filtering or ordering cannot alter relative bar lengths.
        BigDecimal max =
                items.stream()
                        .flatMap(r -> java.util.stream.Stream.of(r.before(), r.after()))
                        .filter(java.util.Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);
        int start = page * PAGE_SIZE, end = Math.min(items.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) rows.getChildren().add(row(items.get(i), max));
        if (items.isEmpty())
            rows.getChildren().add(new Label("Nessun risultato con i filtri selezionati."));
        pageLabel.setText(
                items.isEmpty() ? "0 risultati" : (start + 1) + "–" + end + " di " + items.size());
        previous.setDisable(page == 0);
        next.setDisable(end >= items.size());
    }

    private GridPane row(ScenarioComparison.Display data, BigDecimal max) {
        var pane = new GridPane();
        pane.setHgap(18);
        pane.getStyleClass().add("impact-row");
        var name = new Button(data.name());
        name.getStyleClass().add("impact-link");
        name.setWrapText(true);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setOnAction(e -> open.accept(data));
        name.setTooltip(
                new Tooltip(
                        data.name() + "\n" + data.coverage() + "\nClic per aprire il dettaglio"));
        String subtitle = data.row() == null ? "Costo medio per articolo" : format(data.row());
        var caption = new Label(subtitle);
        caption.getStyleClass().add("impact-caption");
        caption.setWrapText(true);
        var identity = new VBox(5, name, caption);
        var bars =
                new VBox(
                        7,
                        bar("Base", data.before(), max, "impact-base"),
                        bar("Scenario", data.after(), max, "impact-scenario"));
        var amount =
                new Label(
                        data.delta() != null && data.delta().signum() == 0
                                ? "Invariato"
                                : delta(data.delta()));
        amount.getStyleClass().addAll("impact-delta", tone(data.delta()));
        precise(amount, data.delta(), "€");
        var percentage =
                new Label(data.percent() == null ? "Δ% non disponibile" : percent(data.percent()));
        percentage.getStyleClass().add(tone(data.delta()));
        precise(percentage, data.percent(), "%");
        amount.setWrapText(true);
        percentage.setWrapText(true);
        var difference = new VBox(5, amount, percentage);
        difference.setAlignment(Pos.CENTER_RIGHT);
        var left = new ColumnConstraints(150, 190, 230);
        var middle = new ColumnConstraints();
        middle.setHgrow(Priority.ALWAYS);
        middle.setMinWidth(180);
        var right = new ColumnConstraints(140, 170, 180);
        pane.getColumnConstraints().addAll(left, middle, right);
        pane.add(identity, 0, 0);
        pane.add(bars, 1, 0);
        pane.add(difference, 2, 0);
        return pane;
    }

    private HBox bar(String title, BigDecimal value, BigDecimal max, String style) {
        var label = new Label(title);
        label.getStyleClass().add("impact-caption");
        label.setMinWidth(54);
        var track = new Pane();
        track.setMinWidth(0);
        track.setVisible(value != null);
        track.setPrefHeight(10);
        track.setMaxHeight(10);
        track.getStyleClass().add("impact-track");
        var fill = new Region();
        fill.getStyleClass().add(style);
        fill.setPrefHeight(10);
        double ratio =
                value == null || max.signum() <= 0
                        ? 0
                        : value.max(BigDecimal.ZERO)
                                .divide(max, 12, RoundingMode.HALF_UP)
                                .doubleValue();
        fill.prefWidthProperty().bind(track.widthProperty().multiply(ratio));
        track.getChildren().add(fill);
        HBox.setHgrow(track, Priority.ALWAYS);
        var price = new Label(money(value));
        price.getStyleClass().add("impact-price");
        price.setMinWidth(105);
        price.setAlignment(Pos.CENTER_RIGHT);
        precise(price, value, "€/articolo");
        if (value == null)
            price.setTooltip(new Tooltip("Costo non calcolabile: consulta il dettaglio."));
        var box = new HBox(10, label, track, price);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static String format(Scenarios.Row row) {
        var a = row.before().article();
        return a == null
                ? "Senza articoli"
                : "Ø "
                        + ScenarioTables.edit(a.diameter())
                        + " × "
                        + ScenarioTables.edit(a.height())
                        + " mm";
    }

    private static HBox legend(String title, String style) {
        var swatch = new Region();
        swatch.setMinSize(18, 8);
        swatch.setMaxSize(18, 8);
        swatch.getStyleClass().add(style);
        var result = new HBox(6, swatch, new Label(title));
        result.setAlignment(Pos.CENTER_LEFT);
        return result;
    }
}

package com.orodent.ecos.statistiche.features.sales.projection.view;

import com.orodent.ecos.statistiche.core.components.ChartPointTooltip;
import com.orodent.ecos.statistiche.features.sales.projection.model.MonthlyProjection;
import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import java.math.BigDecimal;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Reusable presentation layer: toggle, forecast series, legend, note and tooltips. No calculations. */
public final class MonthlyForecastLayer implements AutoCloseable {
    private final CheckBox toggle = new CheckBox("Mostra proiezione");
    private final Label note = new Label();
    private final List<ChartPointTooltip<String, Number>> tooltips = new ArrayList<>();
    private LineChart<String, Number> chart;
    private XYChart.Series<String, Number> series;
    private MonthlyProjection projection;
    private Function<BigDecimal, String> format;

    public MonthlyForecastLayer() {
        toggle.setSelected(true);
        toggle.getStyleClass().add("monthly-forecast-toggle");
        note.getStyleClass().add("monthly-forecast-note");
        note.setWrapText(true);
        note.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        visible(toggle, false); visible(note, false);
        toggle.setOnAction(event -> render());
    }
    public CheckBox toggle() { return toggle; }
    public Label note() { return note; }
    public void show(LineChart<String, Number> chart, MonthlyProjection projection, Function<BigDecimal, String> format) {
        close();
        if (!chart.getStyleClass().contains("monthly-forecast-chart")) chart.getStyleClass().add("monthly-forecast-chart");
        this.chart = chart; this.projection = projection; this.format = format;
        visible(toggle, projection != null);
        render();
    }
    private void render() {
        clearSeries();
        visible(note, projection != null && toggle.isSelected());
        if (projection == null || !toggle.isSelected()) return;
        var annual = projection.annual();
        int month = annual.dataThrough().getMonthValue();
        boolean partial = !annual.dataThrough().equals(YearMonth.from(annual.dataThrough()).atEndOfMonth());
        int anchor = partial ? month - 1 : month;
        series = new XYChart.Series<>();
        series.setName(annual.year() + " · proiezione");
        series.nodeProperty().addListener((observable, before, after) -> {
            if (after != null) after.getStyleClass().add("monthly-forecast-line");
        });
        for (var value : projection.points()) {
            if (value.month() < Math.max(1, anchor)) continue;
            boolean registered = value.month() == anchor;
            var point = new XYChart.Data<String, Number>(
                    Month.of(value.month()).getDisplayName(TextStyle.SHORT, Locale.ITALY),
                    registered ? value.actual() : value.total());
            point.nodeProperty().addListener((observable, before, after) -> {
                if (after != null) after.getStyleClass().add("monthly-forecast-symbol");
            });
            String period = Month.of(value.month()).getDisplayName(TextStyle.FULL, Locale.ITALY) + " " + annual.year();
            tooltips.add(new ChartPointTooltip<>(point, () -> registered ? period + "\nRegistrato: " + format.apply(value.actual())
                    : period + " · proiezione\nRegistrato: " + format.apply(value.actual())
                    + "\nResiduo previsto: " + format.apply(value.remaining())
                    + "\nTotale mese previsto: " + format.apply(value.total())));
            series.getData().add(point);
        }
        chart.getData().add(series);
        styleSeries(series);
        var expected = series;
        Platform.runLater(() -> {
            if (series != expected) return;
            styleSeries(expected);
            chart.applyCss();
            for (var item : chart.lookupAll(".chart-legend-item")) {
                if (item instanceof Label label && expected.getName().equals(label.getText()) && label.getGraphic() != null)
                    label.getGraphic().getStyleClass().add("monthly-forecast-legend-symbol");
            }
        });
        note.setText("Proiezione al 31/12 · " + (projection.seasonal() ? "distribuzione stagionale" : "distribuzione per giorni residui, storico insufficiente")
                + (partial ? " · il mese corrente include il residuo previsto." : ".")
                + " Dati registrati fino al " + annual.dataThrough().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
    }
    private void styleSeries(XYChart.Series<String, Number> value) {
        if (value.getNode() != null && !value.getNode().getStyleClass().contains("monthly-forecast-line"))
            value.getNode().getStyleClass().add("monthly-forecast-line");
        for (var point : value.getData()) {
            if (point.getNode() != null && !point.getNode().getStyleClass().contains("monthly-forecast-symbol"))
                point.getNode().getStyleClass().add("monthly-forecast-symbol");
        }
    }
    private void clearSeries() {
        tooltips.forEach(ChartPointTooltip::close); tooltips.clear();
        if (chart != null && series != null) chart.getData().remove(series);
        series = null;
    }
    @Override public void close() { clearSeries(); }
    private static void visible(javafx.scene.Node node, boolean value) { node.setVisible(value); node.setManaged(value); }
}

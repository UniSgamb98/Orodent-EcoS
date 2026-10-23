package com.orodent.ecos.statistiche.features.sales.customers.view;

import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerMonthlyValue;
import com.orodent.ecos.statistiche.core.components.ChartPointTooltip;
import com.orodent.ecos.statistiche.features.sales.projection.view.MonthlyForecastLayer;
import com.orodent.ecos.statistiche.features.sales.projection.model.MonthlyProjections;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Owns the monthly chart, its display metric and comparison controls. */
final class CustomerMonthlyPane extends VBox {
    private final MonthlyForecastLayer forecast = new MonthlyForecastLayer();
    private MonthlyProjections projections = MonthlyProjections.empty();
    private final ComboBox<Integer> comparisonYears = new ComboBox<>();
    private final ComboBox<Metric> metric = new ComboBox<>();
    private final NumberAxis valueAxis = new NumberAxis();
    private final LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), valueAxis);
    private final Label error = new Label();
    private final ProgressIndicator loading = new ProgressIndicator();
    private final List<ChartPointTooltip<String, Number>> pointTooltips = new ArrayList<>();
    private final Map<Integer, List<CustomerMonthlyValue>> history = new TreeMap<>(Comparator.reverseOrder());

    CustomerMonthlyPane() {
        super(8);
        comparisonYears.setItems(FXCollections.observableArrayList(1, 2, 3, 4, 5));
        comparisonYears.setValue(1);
        metric.setItems(FXCollections.observableArrayList(Metric.values()));
        metric.setValue(Metric.REVENUE);
        metric.getStyleClass().add("customer-monthly-metric");
        metric.setAccessibleText("Metrica del grafico mensile");
        metric.setOnAction(event -> updateMetric());
        Label metricLabel = new Label("Mostra:");
        metricLabel.setLabelFor(metric);
        FlowPane controls = new FlowPane(8, 8, new Label("Mostra l'anno corrente e:"),
                comparisonYears, new Label("anni precedenti"), loading, metricLabel, metric, forecast.toggle());
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().add("customer-comparison-bar");
        loading.setMaxSize(28, 28);
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        setLoading(false);
        setErrorVisible(false);
        CategoryAxis months = (CategoryAxis) chart.getXAxis();
        months.setAutoRanging(false);
        months.setCategories(FXCollections.observableArrayList(Arrays.stream(Month.values())
                .map(month -> month.getDisplayName(TextStyle.SHORT, Locale.ITALY)).toList()));
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.BOTTOM);
        chart.setMinHeight(260);
        chart.setPrefHeight(260);
        chart.getStyleClass().add("customer-monthly-chart");
        VBox.setVgrow(chart, Priority.ALWAYS);
        getChildren().addAll(controls, error, chart, forecast.note());
        updateMetric();
    }

    int comparisonYears() {
        return comparisonYears.getValue() == null ? 1 : comparisonYears.getValue();
    }

    void onComparisonYearsChanged(Runnable handler) {
        comparisonYears.setOnAction(event -> handler.run());
    }

    void showLoading() {
        setErrorVisible(false);
        setLoading(true);
    }

    void showProjections(MonthlyProjections projections) {
        this.projections = projections;
    }

    void showHistory(List<CustomerMonthlyValue> values) {
        forecast.close();
        pointTooltips.forEach(ChartPointTooltip::close);
        pointTooltips.clear();
        history.clear();
        values.stream().map(CustomerMonthlyValue::year).distinct().forEach(year ->
                history.put(year, values.stream().filter(value -> value.year() == year)
                        .sorted(Comparator.comparingInt(CustomerMonthlyValue::month)).toList()));
        var cutoff = projections.revenue() != null ? projections.revenue() : projections.quantity();
        if (cutoff != null) {
            int year = cutoff.annual().year();
            List<CustomerMonthlyValue> existing = history.getOrDefault(year, List.of());
            List<CustomerMonthlyValue> complete = new ArrayList<>();
            for (int month = 1; month <= cutoff.annual().dataThrough().getMonthValue(); month++) {
                final int index = month;
                complete.add(existing.stream().filter(value -> value.month() == index).findFirst()
                        .orElse(new CustomerMonthlyValue(year, month, BigDecimal.ZERO, BigDecimal.ZERO, 0)));
            }
            history.put(year, complete);
        }
        chart.getData().clear();
        history.forEach((year, months) -> {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(Integer.toString(year));
            months.forEach(value -> {
                XYChart.Data<String, Number> point = new XYChart.Data<>(
                        Month.of(value.month()).getDisplayName(TextStyle.SHORT, Locale.ITALY),
                        metric.getValue().value(value));
                pointTooltips.add(new ChartPointTooltip<>(point,
                        () -> Month.of(value.month()).getDisplayName(TextStyle.FULL, Locale.ITALY)
                                + " " + series.getName() + "\n" + metric.getValue()
                                + ": " + formatPointValue(point.getYValue()),
                        metric.valueProperty(), series.nameProperty()));
                series.getData().add(point);
            });
            chart.getData().add(series);
        });
        refreshForecast();
        setErrorVisible(false);
        setLoading(false);
    }

    void showError(Throwable failure) {
        setLoading(false);
        error.setText(failure == null || failure.getMessage() == null
                ? "Impossibile aggiornare il confronto mensile." : failure.getMessage());
        setErrorVisible(true);
    }

    void dispose() {
        forecast.close();
        pointTooltips.forEach(ChartPointTooltip::close);
        pointTooltips.clear();
        chart.getData().clear();
        history.clear();
        projections = MonthlyProjections.empty();
    }

    private void updateMetric() {
        Metric selected = metric.getValue();
        valueAxis.setLabel(selected == Metric.REVENUE ? "Fatturato (€)" : "Quantità (unità)");
        NumberFormat format = selected == Metric.REVENUE
                ? NumberFormat.getCurrencyInstance(Locale.ITALY)
                : NumberFormat.getNumberInstance(Locale.ITALY);
        valueAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number value) { return format.format(value); }
            @Override public Number fromString(String value) { throw new UnsupportedOperationException(); }
        });
        forecast.close();
        // Update values in place to preserve each year's series and color.
        for (XYChart.Series<String, Number> series : chart.getData()) {
            List<CustomerMonthlyValue> months = history.get(Integer.parseInt(series.getName()));
            for (int index = 0; index < months.size(); index++) {
                series.getData().get(index).setYValue(selected.value(months.get(index)));
            }
        }
        refreshForecast();
    }

    private void refreshForecast() {
        forecast.show(chart, metric.getValue() == Metric.REVENUE ? projections.revenue() : projections.quantity(), this::formatPointValue);
    }

    private String formatPointValue(Number value) {
        NumberFormat format = metric.getValue() == Metric.REVENUE
                ? NumberFormat.getCurrencyInstance(Locale.ITALY)
                : NumberFormat.getNumberInstance(Locale.ITALY);
        format.setMaximumFractionDigits(340);
        return format.format(value) + (metric.getValue() == Metric.QUANTITY ? " unità" : "");
    }

    private void setLoading(boolean active) {
        loading.setVisible(active);
        loading.setManaged(active);
        comparisonYears.setDisable(active);
    }

    private void setErrorVisible(boolean visible) {
        error.setVisible(visible);
        error.setManaged(visible);
    }

    private enum Metric {
        REVENUE("Fatturato"), QUANTITY("Quantità");

        private final String label;
        Metric(String label) { this.label = label; }
        BigDecimal value(CustomerMonthlyValue value) {
            return this == REVENUE ? value.revenue() : value.quantity();
        }
        @Override public String toString() { return label; }
    }
}

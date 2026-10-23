package com.orodent.ecos.statistiche.features.sales.customers.view;

import com.orodent.ecos.statistiche.features.sales.customers.model.*;
import com.orodent.ecos.statistiche.core.components.ChartPointTooltip;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;

final class CustomerTrendPane extends VBox {
    private final List<ChartPointTooltip<String, Number>> pointTooltips = new ArrayList<>();
    private final Label status = new Label();
    private final Label explanation = new Label();
    private final Label chartTitle = new Label("Ordini mensili");
    private final CustomerTrendComparisonPane rolling = new CustomerTrendComparisonPane("Ultimi 12 mesi completi");
    private final CustomerTrendComparisonPane annual = new CustomerTrendComparisonPane("Anno a pari data");
    private final LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), new NumberAxis());

    CustomerTrendPane() {
        setSpacing(12);
        getStyleClass().add("customer-trend-pane");
        status.getStyleClass().add("customer-trend-status");
        explanation.getStyleClass().add("muted-label");
        explanation.setWrapText(true);
        explanation.setMinWidth(0);
        explanation.setMaxWidth(Double.MAX_VALUE);
        explanation.setMinHeight(Region.USE_PREF_SIZE);
        annual.getStyleClass().add("customer-trend-annual-row");
        VBox comparisons = new VBox(rolling, annual);
        comparisons.getStyleClass().add("customer-trend-comparisons");
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);
        chart.setPrefHeight(270);
        chart.setMinHeight(Region.USE_PREF_SIZE);
        chart.getStyleClass().add("customer-trend-chart");
        VBox.setVgrow(chart, Priority.ALWAYS);
        HBox legend = new HBox(18, legendItem("Ordini mensili", "actual-orders"),
                legendItem("Media mobile 3 mesi", "moving-average"));
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getStyleClass().add("customer-trend-legend");
        chartTitle.getStyleClass().add("customer-trend-chart-title");
        getChildren().addAll(status, comparisons, explanation, chartTitle, legend, chart);
    }

    void show(CustomerTrendData data) {
        CustomerTrendSummary summary = data.summary();
        status.setText(summary.status().label());
        status.getStyleClass().removeIf(style -> style.startsWith("trend-status-"));
        status.getStyleClass().add("trend-status-" + summary.status().name().toLowerCase(Locale.ROOT).replace('_', '-'));
        rolling.show(summary.rolling());
        annual.show(summary.annual());
        explanation.setText(summary.explanation());
        var through = summary.annual().period().through();
        chartTitle.setText("Ordini mensili" + (through.equals(java.time.YearMonth.from(through).atEndOfMonth())
                ? "" : " · ultimo mese parziale"));

        pointTooltips.forEach(ChartPointTooltip::close);
        pointTooltips.clear();
        XYChart.Series<String, Number> actual = new XYChart.Series<>();
        actual.setName("Ordini mensili");
        XYChart.Series<String, Number> average = new XYChart.Series<>();
        average.setName("Media mobile 3 mesi");
        for (CustomerTrendPoint point : data.points()) {
            String month = Month.of(point.month()).getDisplayName(TextStyle.SHORT, Locale.ITALY)
                    + " " + Integer.toString(point.year()).substring(2);
            String period = Month.of(point.month()).getDisplayName(TextStyle.FULL, Locale.ITALY) + " " + point.year();
            XYChart.Data<String, Number> actualPoint = new XYChart.Data<>(month, point.orders());
            XYChart.Data<String, Number> averagePoint = new XYChart.Data<>(month, point.movingAverage());
            pointTooltips.add(new ChartPointTooltip<>(actualPoint,
                    () -> period + "\nOrdini mensili: " + actualPoint.getYValue()));
            pointTooltips.add(new ChartPointTooltip<>(averagePoint,
                    () -> period + "\nMedia mobile 3 mesi: " + formatAverage(averagePoint.getYValue()) + " ordini/mese"));
            actual.getData().add(actualPoint);
            average.getData().add(averagePoint);
        }
        chart.getData().setAll(actual, average);
    }

    void dispose() {
        pointTooltips.forEach(ChartPointTooltip::close);
        pointTooltips.clear();
        chart.getData().clear();
    }

    private String formatAverage(Number value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ITALY);
        format.setMaximumFractionDigits(340);
        return format.format(value);
    }

    private HBox legendItem(String text, String seriesStyle) {
        Region line = new Region();
        line.getStyleClass().addAll("customer-trend-legend-line", seriesStyle);
        Label label = new Label(text);
        HBox item = new HBox(7, line, label);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("customer-trend-legend-item");
        return item;
    }

}

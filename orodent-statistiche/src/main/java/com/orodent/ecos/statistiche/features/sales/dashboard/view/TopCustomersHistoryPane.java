package com.orodent.ecos.statistiche.features.sales.dashboard.view;

import com.orodent.ecos.statistiche.core.components.ChartPointTooltip;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.TopCustomerHistory;
import javafx.geometry.Side;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Presents the annual history of the selected year's five leading customers. */
final class TopCustomersHistoryPane extends VBox {
    private final Label description = new Label();
    private final Label empty = new Label("Nessuno storico clienti disponibile.");
    private final LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), new NumberAxis());
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final List<ChartPointTooltip<String, Number>> tooltips = new ArrayList<>();

    TopCustomersHistoryPane() {
        super(10);
        description.getStyleClass().add("muted-label");
        description.setWrapText(true);
        description.setMinHeight(Region.USE_PREF_SIZE);
        description.setMaxWidth(Double.MAX_VALUE);
        empty.getStyleClass().add("muted-label");
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(true);
        chart.setLegendSide(Side.BOTTOM);
        chart.setMinHeight(260);
        chart.setPrefHeight(300);
        chart.getStyleClass().add("top-customers-chart");
        ((NumberAxis) chart.getYAxis()).setLabel("Fatturato netto (€)");
        VBox.setVgrow(chart, Priority.ALWAYS);
        getChildren().addAll(description, empty, chart);
    }

    void show(int selectedYear, List<TopCustomerHistory> history) {
        tooltips.forEach(ChartPointTooltip::close);
        tooltips.clear();
        description.setText("I 5 migliori clienti per fatturato netto del " + selectedYear
                + " · Storico annuale indipendente dal filtro mensile.");
        Map<String, XYChart.Series<String, Number>> series = new LinkedHashMap<>();
        history.stream().sorted(java.util.Comparator.comparingInt(TopCustomerHistory::year)
                .thenComparing(TopCustomerHistory::customerCode)).forEach(value -> {
            XYChart.Series<String, Number> customer = series.computeIfAbsent(value.customerCode(), code -> {
                XYChart.Series<String, Number> created = new XYChart.Series<>();
                created.setName(value.customerName() + " · " + code);
                return created;
            });
            XYChart.Data<String, Number> point = new XYChart.Data<>(Integer.toString(value.year()), value.revenue());
            tooltips.add(new ChartPointTooltip<>(point,
                    () -> value.customerName() + " · " + value.customerCode() + "\n" + point.getXValue()
                            + "\nFatturato netto: " + currency.format(point.getYValue())));
            customer.getData().add(point);
        });
        chart.getData().setAll(series.values());
        empty.setVisible(history.isEmpty());
        empty.setManaged(history.isEmpty());
        chart.setVisible(!history.isEmpty());
        chart.setManaged(!history.isEmpty());
    }

    void dispose() {
        tooltips.forEach(ChartPointTooltip::close);
        tooltips.clear();
        chart.getData().clear();
    }
}

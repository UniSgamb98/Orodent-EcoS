package com.orodent.ecos.statistiche.features.sales.products.view;

import com.orodent.ecos.statistiche.core.components.ChartPointTooltip;
import com.orodent.ecos.statistiche.features.sales.projection.view.MonthlyForecastLayer;
import com.orodent.ecos.statistiche.features.sales.projection.model.MonthlyProjections;
import com.orodent.ecos.statistiche.features.sales.products.model.*;
import javafx.scene.chart.*;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ProductMonthlyPane extends VBox {
    private final MonthlyForecastLayer forecast = new MonthlyForecastLayer();
    private final ProductFormats formats = new ProductFormats();
    private final ComboBox<ProductMetric> metric = new ComboBox<>();
    private final LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), new NumberAxis());
    private final Label insight = new Label();
    private final List<ChartPointTooltip<String, Number>> tooltips = new ArrayList<>();
    private ProductDetailData data;

    ProductMonthlyPane() {
        setSpacing(12);
        getStyleClass().add("product-tab-content");
        metric.getItems().setAll(ProductMetric.values()); metric.setValue(ProductMetric.QUANTITY);
        metric.valueProperty().addListener((observable, before, after) -> render());
        Label title = new Label("Andamento mensile"); title.getStyleClass().add("product-section-title");
        FlowPane heading = new FlowPane(10, 8, title, new Label("Mostra"), metric, forecast.toggle());
        chart.setAnimated(false); chart.setCreateSymbols(true); chart.setPrefHeight(300); chart.setMinHeight(280);
        chart.getStyleClass().add("product-monthly-chart");
        ((NumberAxis)chart.getYAxis()).setForceZeroInRange(true);

        insight.setWrapText(true); insight.setMinHeight(Region.USE_PREF_SIZE);
        insight.getStyleClass().add("product-insight");
        getChildren().addAll(heading, chart, forecast.note(), insight);
    }

    void show(ProductDetailData data) { this.data = data; render(); }
    private void render() {
        if (data == null) return;
        dispose();
        ProductMetric selected = metric.getValue();
        ((NumberAxis)chart.getYAxis()).setTickLabelFormatter(new javafx.util.StringConverter<>() {
            @Override public String toString(Number value) {
                var decimal = java.math.BigDecimal.valueOf(value.doubleValue());
                return selected == ProductMetric.REVENUE ? formats.money(decimal) : formats.number(decimal);
            }
            @Override public Number fromString(String value) { return null; }
        });
        XYChart.Series<String, Number> current = new XYChart.Series<>(), previous = new XYChart.Series<>();
        current.setName(Integer.toString(data.snapshot().year())); previous.setName(Integer.toString(data.snapshot().year() - 1));
        List<String> months = new ArrayList<>();
        for (Month month : Month.values()) months.add(month.getDisplayName(TextStyle.SHORT, Locale.ITALY));
        ((CategoryAxis)chart.getXAxis()).setAutoRanging(false);
        ((CategoryAxis)chart.getXAxis()).getCategories().setAll(months);
        ((NumberAxis)chart.getYAxis()).setLabel(selected == ProductMetric.QUANTITY ? "Unità nette" : "Fatturato netto (€)");
        for (ProductMonthlyValue value : data.monthly()) {
            String month = months.get(value.month() - 1);
            var point = new XYChart.Data<String, Number>(month, selected.value(value.totals()));
            (value.year() == data.snapshot().year() ? current : previous).getData().add(point);
            String period = Month.of(value.month()).getDisplayName(TextStyle.FULL, Locale.ITALY) + " " + value.year();
            tooltips.add(new ChartPointTooltip<>(point, () -> period + "\n" + selected + ": "
                    + (selected == ProductMetric.REVENUE ? formats.money(selected.value(value.totals())) : formats.number(selected.value(value.totals())))));
        }
        chart.getData().setAll(current, previous);
        forecast.show(chart, selected == ProductMetric.REVENUE ? data.monthlyProjections().revenue() : data.monthlyProjections().quantity(),
                value -> selected == ProductMetric.REVENUE ? formats.money(value) : formats.number(value) + " unità");
        var group = data.group();
        String unit = "Valore medio netto per unità: " + formats.money(com.orodent.ecos.statistiche.features.sales.products.service.ProductTotalsCalculator.unitValue(group.current()))
                + ". Può variare anche per il mix di altezze e diametri.";
        boolean partial = !data.snapshot().reference().equals(YearMonth.from(data.snapshot().reference()).atEndOfMonth());
        insight.setText(unit + (partial ? " Ultimo mese parziale, fino al " + data.snapshot().reference().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "." : ""));
    }
    void dispose() { forecast.close(); tooltips.forEach(ChartPointTooltip::close); tooltips.clear(); }
}

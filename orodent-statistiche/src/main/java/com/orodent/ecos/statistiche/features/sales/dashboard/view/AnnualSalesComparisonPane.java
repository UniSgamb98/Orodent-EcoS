package com.orodent.ecos.statistiche.features.sales.dashboard.view;

import com.orodent.ecos.statistiche.core.components.ChartPointTooltip;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.AnnualSalesComparison;
import com.orodent.ecos.statistiche.features.sales.projection.model.ProjectionMethod;
import com.orodent.ecos.statistiche.features.sales.projection.model.RevenueProjection;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Presents annual actuals and estimates independently of the monthly filter. */
final class AnnualSalesComparisonPane extends VBox {
    private final StackedBarChart<String, Number> chart = new StackedBarChart<>(new CategoryAxis(), new NumberAxis());
    private final Label note = new Label();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final List<ChartPointTooltip<String, Number>> tooltips = new ArrayList<>();

    AnnualSalesComparisonPane() {
        super(10);
        chart.setAnimated(false);
        chart.setPrefHeight(310);
        chart.getStyleClass().add("annual-sales-comparison-chart");
        ((NumberAxis) chart.getYAxis()).setLabel("Fatturato (€)");
        note.getStyleClass().add("annual-sales-projection-note");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        note.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(chart, Priority.ALWAYS);
        getChildren().addAll(chart, note);
    }

    void show(List<AnnualSalesComparison> comparisons) {
        tooltips.forEach(ChartPointTooltip::close);
        tooltips.clear();
        XYChart.Series<String, Number> actual = new XYChart.Series<>();
        actual.setName("Fatturato registrato");
        XYChart.Series<String, Number> forecast = new XYChart.Series<>();
        forecast.setName("Residuo previsto · Stima");
        RevenueProjection activeProjection = null;
        for (AnnualSalesComparison annual : comparisons) {
            String year = Integer.toString(annual.year());
            XYChart.Data<String, Number> actualPoint = new XYChart.Data<>(year, annual.summary().netRevenue());
            tooltips.add(new ChartPointTooltip<>(actualPoint,
                    () -> year + " — Fatturato registrato\n" + currency.format(actualPoint.getYValue())
                            + "\nVariazione registrata rispetto all'anno precedente disponibile: "
                            + percentage(annual.revenueChangePercentage())));
            actual.getData().add(actualPoint);
            RevenueProjection projection = annual.projection();
            if (projection != null && projection.method() != ProjectionMethod.ACTUAL) {
                XYChart.Data<String, Number> forecastPoint = new XYChart.Data<>(year, projection.projectedRemainingRevenue());
                tooltips.add(new ChartPointTooltip<>(forecastPoint,
                        () -> year + " — Residuo previsto · Stima\n" + currency.format(forecastPoint.getYValue())
                                + "\nTotale annuo proiettato: " + currency.format(projection.projectedAnnualRevenue())));
                forecast.getData().add(forecastPoint);
                activeProjection = projection;
            }
        }
        chart.getData().setAll(actual);
        if (!forecast.getData().isEmpty()) chart.getData().add(forecast);
        chart.setLegendVisible(true);
        updateNote(activeProjection, comparisons);
    }

    void dispose() {
        tooltips.forEach(ChartPointTooltip::close);
        tooltips.clear();
        chart.getData().clear();
    }

    private void updateNote(RevenueProjection projection, List<AnnualSalesComparison> comparisons) {
        String scope = "Confronto su anni interi, indipendente dal filtro del grafico mensile.";
        if (projection == null) {
            boolean currentYearPresent = comparisons.stream().anyMatch(value -> value.year() == Year.now().getValue()
                    && (value.projection() == null || value.projection().method() != ProjectionMethod.ACTUAL));
            note.setText(scope + (currentYearPresent ? " Proiezione dell'anno corrente non disponibile." : ""));
            return;
        }
        String method = projection.method() == ProjectionMethod.SEASONAL
                ? "stagionalità di " + projection.historicalYears() + " anni storici"
                : "andamento lineare (storico insufficiente)";
        note.setText(scope + "\nProiezione " + projection.year() + " al 31/12 basata su " + method
                + " · Affidabilità " + projection.confidence().label()
                + " · Dati al " + projection.dataThrough().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    private String percentage(BigDecimal value) {
        return value == null ? "non disponibile" : (value.signum() > 0 ? "+" : "")
                + value.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }
}

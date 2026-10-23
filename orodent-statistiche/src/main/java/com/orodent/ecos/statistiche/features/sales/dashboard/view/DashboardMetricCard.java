package com.orodent.ecos.statistiche.features.sales.dashboard.view;

import com.orodent.ecos.statistiche.features.sales.projection.model.MetricProjection;
import com.orodent.ecos.statistiche.features.sales.projection.model.ProjectionMethod;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.function.Function;

/** One KPI card, with side-by-side forecast values and a compact layout at narrow widths. */
final class DashboardMetricCard extends VBox {
    private static final double SIDE_BY_SIDE_WIDTH = 340;
    private final Label actualValue = label("dashboard-metric-value");
    private final Label projectedValue = label("dashboard-projected-value");
    private final DashboardComparisonLabel comparison = new DashboardComparisonLabel();
    private final GridPane values = new GridPane();
    private final VBox actual;
    private final VBox forecast;
    private boolean hasForecast;
    private boolean stacked;
    private VBox secondaryHeading;
    private DashboardComparisonLabel secondaryComparison;
    private Label secondaryValue;

    DashboardMetricCard(String title) {
        setSpacing(7); setMinWidth(0); setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("dashboard-metric");
        Label heading = label("muted-label"); heading.setText(title);
        Label forecastHeading = label("muted-label"); forecastHeading.setText("Proiezione al 31/12");
        actual = new VBox(7, heading, actualValue);
        forecast = new VBox(7, forecastHeading, projectedValue);
        actual.setMinWidth(0); forecast.setMinWidth(0);
        forecast.getStyleClass().add("dashboard-forecast-values");
        values.setHgap(12); values.setVgap(10);
        getChildren().addAll(values, comparison);
        widthProperty().addListener((observable, before, after) -> arrange(after.doubleValue()));
        arrange(SIDE_BY_SIDE_WIDTH);
    }

    void show(BigDecimal recorded, BigDecimal previous, int year, MetricProjection projection,
              Function<BigDecimal, String> format) {
        actualValue.setText(format.apply(recorded));
        hasForecast = projection != null && projection.method() != ProjectionMethod.ACTUAL;
        projectedValue.setText(hasForecast ? format.apply(projection.projectedAnnualValue()) : "");
        forecast.setVisible(hasForecast); forecast.setManaged(hasForecast);
        comparison.show(recorded, previous, year, projection, format);
        arrange(getWidth());
    }

    void showSecondary(String title, BigDecimal current, BigDecimal previous, int year,
                       Function<BigDecimal, String> format) {
        if (secondaryHeading == null) {
            Label heading = label("metric-secondary-label"); heading.setText(title);
            secondaryComparison = new DashboardComparisonLabel();
            secondaryHeading = new VBox(5, heading, secondaryComparison);
            secondaryHeading.getStyleClass().add("dashboard-secondary-heading");
            secondaryValue = label("metric-secondary-value");
            getChildren().addAll(secondaryHeading, secondaryValue);
        }
        secondaryValue.setText(format.apply(current));
        secondaryComparison.show(current, previous, year, null, format);
    }

    @Override protected double computePrefHeight(double width) {
        if (width >= 0) arrange(width);
        return super.computePrefHeight(width);
    }

    private void arrange(double width) {
        boolean narrow = width < SIDE_BY_SIDE_WIDTH;
        int expected = hasForecast ? 2 : 1;
        if (stacked == narrow && values.getChildren().size() == expected) return;
        stacked = narrow;
        values.getChildren().clear(); values.getColumnConstraints().clear();
        ColumnConstraints first = new ColumnConstraints(); first.setMinWidth(0); first.setHgrow(Priority.ALWAYS);
        first.setPercentWidth(hasForecast && !narrow ? 50 : 100);
        values.getColumnConstraints().add(first); values.add(actual, 0, 0);
        forecast.getStyleClass().remove("dashboard-forecast-stacked");
        if (hasForecast) {
            if (narrow) {
                forecast.getStyleClass().add("dashboard-forecast-stacked"); values.add(forecast, 0, 1);
            } else {
                ColumnConstraints second = new ColumnConstraints(); second.setMinWidth(0); second.setPercentWidth(50);
                values.getColumnConstraints().add(second); values.add(forecast, 1, 0);
            }
        }
    }

    private static Label label(String style) {
        Label value = new Label(); value.getStyleClass().add(style);
        value.setWrapText(true); value.setMinWidth(0); value.setMaxWidth(Double.MAX_VALUE);
        value.setMinHeight(Region.USE_PREF_SIZE); return value;
    }
}

package com.orodent.ecos.statistiche.features.sales.customers.view;

import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerTrendComparison;
import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerTrendDirection;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Displays one comparison; dates, totals and percentages are supplied by the service. */
final class CustomerTrendComparisonPane extends VBox {
    private static final double COMPACT_WIDTH = 620;
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final NumberFormat number = NumberFormat.getNumberInstance(Locale.ITALY);
    private final DateTimeFormatter dates = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final Label period = wrappedLabel("customer-trend-period");
    private final Label note = wrappedLabel("customer-trend-period-note");
    private final Label revenue = new Label();
    private final Label revenueChange = new Label();
    private final Label quantity = new Label();
    private final Label quantityChange = new Label();
    private final Label orders = wrappedLabel("customer-trend-orders");
    private final HBox metrics = new HBox(18);
    private final GridPane layout = new GridPane();
    private final VBox heading;
    private boolean compact;

    CustomerTrendComparisonPane(String title) {
        getStyleClass().add("customer-trend-comparison-row");
        setSpacing(8);
        setMinWidth(0);
        setMinHeight(Region.USE_PREF_SIZE);
        number.setMaximumFractionDigits(3);
        Label name = wrappedLabel("customer-trend-period-title");
        name.setText(title);
        heading = new VBox(4, name, period, note);
        VBox revenueMetric = metric("Fatturato", revenue, revenueChange);
        VBox quantityMetric = metric("Quantità", quantity, quantityChange);
        metrics.getChildren().addAll(revenueMetric, quantityMetric);
        metrics.setAlignment(Pos.TOP_LEFT);
        metrics.setMinWidth(0);
        HBox.setHgrow(revenueMetric, Priority.ALWAYS);
        HBox.setHgrow(quantityMetric, Priority.ALWAYS);
        layout.setHgap(20);
        layout.setVgap(10);
        layout.setMinWidth(0);
        getChildren().addAll(layout, orders);
        widthProperty().addListener((observable, before, after) -> arrange(after.doubleValue() < COMPACT_WIDTH));
        arrange(false);
    }

    @Override
    protected double computePrefHeight(double width) {
        if (width >= 0) arrange(width < COMPACT_WIDTH);
        return super.computePrefHeight(width);
    }

    void show(CustomerTrendComparison comparison) {
        var window = comparison.period();
        period.setText(dates.format(window.from()) + " – " + dates.format(window.through())
                + "\nRispetto a " + dates.format(window.previousFrom()) + " – " + dates.format(window.previousThrough()));
        String message = !comparison.completeHistory() ? "Storico incompleto: confronto non disponibile"
                : comparison.preliminary() ? "Confronto preliminare · periodo breve o pochi ordini" : "";
        note.setText(message);
        note.setVisible(!message.isEmpty());
        note.setManaged(!message.isEmpty());
        revenue.setText(currency.format(comparison.current().revenue()));
        quantity.setText(number.format(comparison.current().quantity()) + " unità");
        showChange(revenueChange, comparison.revenueChangePercentage());
        showChange(quantityChange, comparison.quantityChangePercentage());
        String revenueTooltip = "Fatturato attuale: " + currency.format(comparison.current().revenue())
                + "\nFatturato precedente: " + currency.format(comparison.previous().revenue());
        String quantityTooltip = "Quantità attuale: " + number.format(comparison.current().quantity())
                + "\nQuantità precedente: " + number.format(comparison.previous().quantity());
        installTooltip(revenue, revenueChange, revenueTooltip, comparison.completeHistory(), comparison.previous().revenue());
        installTooltip(quantity, quantityChange, quantityTooltip, comparison.completeHistory(), comparison.previous().quantity());
        orders.setText("Ordini: " + comparison.current().orders() + " · precedenti: " + comparison.previous().orders()
                + (comparison.orderChangePercentage() == null ? "" : " · " + percentage(comparison.orderChangePercentage())));
    }

    private void arrange(boolean narrow) {
        if (!layout.getChildren().isEmpty() && compact == narrow) return;
        compact = narrow;
        layout.getChildren().clear();
        layout.getColumnConstraints().clear();
        ColumnConstraints first = new ColumnConstraints();
        first.setMinWidth(0);
        first.setHgrow(Priority.ALWAYS);
        layout.getColumnConstraints().add(first);
        layout.add(heading, 0, 0);
        if (narrow) {
            layout.add(metrics, 0, 1);
        } else {
            first.setPercentWidth(48);
            ColumnConstraints second = new ColumnConstraints();
            second.setPercentWidth(52);
            second.setMinWidth(0);
            layout.getColumnConstraints().add(second);
            layout.add(metrics, 1, 0);
        }
    }

    private VBox metric(String title, Label total, Label change) {
        Label name = wrappedLabel("customer-trend-metric-title");
        name.setText(title);
        total.getStyleClass().add("customer-trend-value");
        total.setWrapText(true);
        total.setMinWidth(0);
        total.setMaxWidth(Double.MAX_VALUE);
        total.setMinHeight(Region.USE_PREF_SIZE);
        change.getStyleClass().add("customer-trend-direction");
        change.setWrapText(true);
        change.setMinWidth(0);
        change.setMaxWidth(Double.MAX_VALUE);
        change.setMinHeight(Region.USE_PREF_SIZE);
        VBox box = new VBox(5, name, total, change);
        box.setMinWidth(0);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void showChange(Label label, BigDecimal change) {
        label.getStyleClass().removeAll("metric-growing", "metric-stable", "metric-declining");
        CustomerTrendDirection direction = CustomerTrendDirection.forValue(change);
        String style = switch (direction) {
            case UP -> "metric-growing";
            case DOWN -> "metric-declining";
            default -> "metric-stable";
        };
        String arrow = switch (direction) {
            case UP -> "↗ ";
            case DOWN -> "↘ ";
            default -> "→ ";
        };
        label.setText(change == null ? "Non confrontabile" : arrow + percentage(change));
        label.getStyleClass().add(style);
    }

    private String percentage(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + number.format(value) + "%";
    }

    private void installTooltip(Label total, Label change, String values, boolean complete, BigDecimal previous) {
        String reason = !complete ? "\nStorico incompleto"
                : previous.signum() == 0 ? "\nPercentuale non calcolabile: valore precedente nullo" : "";
        total.setTooltip(new Tooltip(values + reason));
        change.setTooltip(new Tooltip(values + reason));
    }

    private static Label wrappedLabel(String style) {
        Label label = new Label();
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }
}

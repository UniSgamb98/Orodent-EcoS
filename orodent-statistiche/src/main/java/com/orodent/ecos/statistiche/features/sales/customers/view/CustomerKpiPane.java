package com.orodent.ecos.statistiche.features.sales.customers.view;

import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerDetail;
import com.orodent.ecos.statistiche.features.sales.projection.model.RevenueProjection;
import com.orodent.ecos.statistiche.features.sales.projection.model.ProjectionMethod;
import javafx.scene.control.Label;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

final class CustomerKpiPane extends TilePane {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final NumberFormat number = NumberFormat.getNumberInstance(Locale.ITALY);
    private final Map<Metric, Label> values = new EnumMap<>(Metric.class);
    private final Map<Metric, Label> titles = new EnumMap<>(Metric.class);

    CustomerKpiPane() {
        setHgap(10);
        setVgap(10);
        setPrefTileWidth(175);
        setPrefTileHeight(92);
        getStyleClass().add("customer-kpi-pane");
        for (Metric metric : Metric.values()) {
            getChildren().add(createCard(metric));
        }
    }

    void show(CustomerDetail detail, RevenueProjection projection) {
        value(Metric.REVENUE, currency.format(detail.revenue()));
        value(Metric.DOCUMENTS, number.format(detail.documents()));
        value(Metric.AVERAGE_VALUE, currency.format(detail.averageDocumentValue()));
        value(Metric.AVERAGE_QUANTITY, number.format(detail.averageDocumentQuantity()));
        value(Metric.AVERAGE_FREQUENCY, frequency(detail.averageFrequencyDays()));
        value(Metric.TYPICAL_FREQUENCY, frequency(detail.typicalFrequencyDays()));
        value(Metric.LAST_PURCHASE, detail.lastPurchase().format(DATE_FORMAT));
        value(Metric.RECENCY, detail.daysSinceLastPurchase() + " giorni fa");
        value(Metric.PROJECTED_REVENUE, currency.format(projection.projectedAnnualRevenue()));
        titles.get(Metric.PROJECTED_REVENUE).setText(projection.method() == ProjectionMethod.ACTUAL
                ? "Fatturato consuntivo" : "Fatturato proiettato");
    }

    private VBox createCard(Metric metric) {
        Label title = new Label(metric.title);
        titles.put(metric, title);
        title.getStyleClass().add("muted-label");
        Label value = new Label("—");
        value.setWrapText(true);
        value.getStyleClass().add("dashboard-metric-value");
        values.put(metric, value);
        VBox card = new VBox(7, title, value);
        card.getStyleClass().addAll("dashboard-metric", "customer-kpi-card");
        return card;
    }

    private void value(Metric metric, String text) {
        values.get(metric).setText(text);
    }

    private String frequency(java.math.BigDecimal days) {
        return days == null ? "Non disponibile" : number.format(days) + " giorni";
    }

    private enum Metric {
        REVENUE("Fatturato"),
        DOCUMENTS("Documenti"),
        AVERAGE_VALUE("Valore medio"),
        AVERAGE_QUANTITY("Quantità media"),
        AVERAGE_FREQUENCY("Frequenza media"),
        TYPICAL_FREQUENCY("Frequenza tipica"),
        LAST_PURCHASE("Ultimo acquisto"),
        RECENCY("Recenza"),
        PROJECTED_REVENUE("Fatturato proiettato");

        private final String title;

        Metric(String title) {
            this.title = title;
        }
    }
}

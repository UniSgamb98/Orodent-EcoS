package com.orodent.ecos.statistiche.features.sales.products.view;

import com.orodent.ecos.statistiche.core.components.ContentSizedTabPane;
import com.orodent.ecos.statistiche.features.sales.products.model.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.function.Consumer;

final class ProductDetailPane extends VBox {
    private final ProductFormats formats = new ProductFormats();
    private final Label title = label("Seleziona una famiglia", "product-detail-title");
    private final Label subtitle = label("Clicca una riga della classifica per aprire l'analisi.", "muted-label");
    private final FlowPane metrics = new FlowPane(10, 10);
    private final Label corrections = label("", "muted-label");
    private final Label period = label("", "product-comparison-period");
    private final Label quantityChange = label("", "product-change");
    private final Label revenueChange = label("", "product-change");
    private final ProductMonthlyPane monthly = new ProductMonthlyPane();
    private final ProductCustomersPane customers = new ProductCustomersPane();
    private final ProductVariantsPane variants = new ProductVariantsPane();
    private final ContentSizedTabPane tabs = new ContentSizedTabPane();
    private final VBox body;

    ProductDetailPane() {
        setSpacing(12); getStyleClass().add("product-detail"); setMinWidth(400);
        tabs.getTabs().setAll(new Tab("Andamento", monthly), new Tab("Clienti", customers), new Tab("Varianti", variants));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); tabs.getStyleClass().add("product-detail-tabs");
        FlowPane changes = new FlowPane(16, 8, quantityChange, revenueChange);
        VBox comparison = new VBox(8, period, changes); comparison.getStyleClass().add("product-comparison");
        body = new VBox(12, metrics, corrections, comparison, tabs); body.setVisible(false); body.setManaged(false);
        getChildren().addAll(title, subtitle, body);
    }
    void onCustomer(Consumer<String> action) { customers.onCustomer(action); }
    void clear(String message) {
        monthly.dispose(); title.setText("Analisi del prodotto"); subtitle.setText(message);
        body.setVisible(false); body.setManaged(false);
    }
    void loading(ProductGroupKey group) {
        clear("Caricamento del dettaglio…"); title.setText(group.label());
    }
    void show(ProductDetailData data) {
        var group = data.group();
        title.setText(group.key().label());
        subtitle.setText(group.key().allFamilies() ? "Tutte le famiglie, i colori, i diametri e le altezze"
                : "Diametri e altezze aggregati · " + (group.key().family().colored()
                ? group.key().color().isEmpty() ? "Tutti i colori" : "Colore " + group.key().color() : "Colore non previsto"));
        metrics.getChildren().setAll(metric("Quantità netta", formats.number(group.current().quantity())),
                metric("Fatturato netto", formats.money(group.current().revenue())),
                metric("Clienti acquirenti", Integer.toString(group.current().customers())));
        corrections.setText("Ordini di vendita: " + group.current().orders() + " · Rettifiche: "
                + formats.number(group.current().returnedQuantity()) + " unità · " + formats.money(group.current().returnedRevenue()));
        var date = data.snapshot().reference(); var format = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        period.setText("Confronto a pari data: 01/01/" + data.snapshot().year() + " – " + date.format(format)
                + "\nRispetto a 01/01/" + (data.snapshot().year() - 1) + " – " + date.minusYears(1).format(format));
        change(quantityChange, "Quantità", group.quantityChange(), formats.number(group.current().quantity()), formats.number(group.previous().quantity()));
        change(revenueChange, "Fatturato", group.revenueChange(), formats.money(group.current().revenue()), formats.money(group.previous().revenue()));
        monthly.show(data); customers.show(data); variants.show(data);
        body.setVisible(true); body.setManaged(true);
    }
    private void change(Label label, String title, BigDecimal percentage, String current, String previous) {
        label.setText(title + " " + formats.change(percentage));
        label.getStyleClass().removeAll("product-positive", "product-negative", "product-neutral");
        label.getStyleClass().add(percentage == null || percentage.signum() == 0 ? "product-neutral" : percentage.signum() > 0 ? "product-positive" : "product-negative");
        label.setTooltip(new Tooltip("Attuale: " + current + "\nPrecedente: " + previous
                + (percentage == null ? "\nPercentuale non disponibile: storico incompleto o base precedente non positiva." : "")));
    }
    private VBox metric(String title, String value) {
        VBox card = new VBox(6, label(title, "muted-label"), label(value, "product-metric-value"));
        card.getStyleClass().add("product-metric"); return card;
    }
    private static Label label(String text, String style) {
        Label label = new Label(text); label.getStyleClass().add(style); label.setWrapText(true);
        label.setMinWidth(0); label.setMinHeight(Region.USE_PREF_SIZE); return label;
    }
    void dispose() { monthly.dispose(); }
}

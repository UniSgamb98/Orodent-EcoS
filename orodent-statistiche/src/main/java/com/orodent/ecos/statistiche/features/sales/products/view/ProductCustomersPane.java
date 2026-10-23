package com.orodent.ecos.statistiche.features.sales.products.view;

import com.orodent.ecos.statistiche.core.components.TableColumns;
import com.orodent.ecos.statistiche.features.sales.products.model.*;
import com.orodent.ecos.statistiche.features.sales.products.service.ProductTotalsCalculator;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.function.Consumer;

final class ProductCustomersPane extends VBox {
    private final ProductFormats formats = new ProductFormats();
    private final ComboBox<ProductMetric> metric = new ComboBox<>();
    private final Label summary = new Label();
    private final TableView<ProductCustomerValue> table = new TableView<>();
    private final Button open = new Button("Apri analisi cliente");
    private Consumer<String> onCustomer = ignored -> { };
    private ProductDetailData data;

    ProductCustomersPane() {
        setSpacing(12); getStyleClass().add("product-tab-content");
        metric.getItems().setAll(ProductMetric.values()); metric.setValue(ProductMetric.QUANTITY);
        metric.valueProperty().addListener((observable, before, after) -> render());
        summary.setWrapText(true); summary.setMinHeight(Region.USE_PREF_SIZE); summary.getStyleClass().add("product-insight");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(320); table.setPlaceholder(new Label("Nessun cliente nel periodo"));
        table.getColumns().addAll(TableColumns.text("Cliente", ProductCustomerValue::name),
                TableColumns.numeric("Quantità", value -> value.totals().quantity(), formats::number),
                TableColumns.numeric("Fatturato", value -> value.totals().revenue(), formats::money),
                TableColumns.numeric("Quota %", value -> share(value), formats::percent));
        open.getStyleClass().add("secondary-button"); open.setDisable(true);
        open.setTooltip(new Tooltip("Seleziona un cliente con ordini di vendita per aprire la sua analisi."));
        table.getSelectionModel().selectedItemProperty().addListener((observable, before, after) -> open.setDisable(after == null || after.totals().orders() == 0));
        open.setOnAction(event -> openCustomer());
        table.setRowFactory(ignored -> {
            TableRow<ProductCustomerValue> row = new TableRow<>();
            row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && !row.isEmpty() && row.getItem().totals().orders() > 0) onCustomer.accept(row.getItem().code()); });
            return row;
        });
        getChildren().addAll(new HBox(10, new Label("Quote e concentrazione per"), metric), summary, table, open);
    }
    void onCustomer(Consumer<String> action) { onCustomer = action; }
    void show(ProductDetailData data) { this.data = data; render(); }
    private void openCustomer() {
        var selected = table.getSelectionModel().getSelectedItem();
        if (selected != null && selected.totals().orders() > 0) onCustomer.accept(selected.code());
    }
    private BigDecimal share(ProductCustomerValue customer) {
        return data == null ? null : ProductTotalsCalculator.share(metric.getValue().value(customer.totals()), metric.getValue().value(data.group().current()));
    }
    private void render() {
        if (data == null) return;
        ProductMetric selected = metric.getValue();
        var ranked = data.customers().stream().sorted(Comparator.comparing((ProductCustomerValue customer) -> selected.value(customer.totals())).reversed()).toList();
        table.getSortOrder().clear();
        table.getItems().setAll(ranked);
        BigDecimal firstFive = ranked.stream().limit(5).map(customer -> selected.value(customer.totals())).reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setText("Clienti acquirenti: " + data.group().current().customers() + " · Quota dei 5 maggiori: "
                + formats.percent(ProductTotalsCalculator.share(firstFive, selected.value(data.group().current())))
                + " delle " + (selected == ProductMetric.QUANTITY ? "quantità nette." : "vendite nette in valore."));
        table.refresh();
    }
}

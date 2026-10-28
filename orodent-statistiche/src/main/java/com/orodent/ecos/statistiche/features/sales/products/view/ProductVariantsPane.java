package com.orodent.ecos.statistiche.features.sales.products.view;

import com.orodent.ecos.statistiche.core.components.TableColumns;
import com.orodent.ecos.statistiche.features.sales.products.model.*;
import com.orodent.ecos.statistiche.features.sales.products.service.ProductTotalsCalculator;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.function.Function;

final class ProductVariantsPane extends VBox {
    private final ProductFormats formats = new ProductFormats();
    private final ComboBox<ProductVariantDimension> dimension = new ComboBox<>();
    private final ComboBox<ProductMetric> metric = new ComboBox<>();
    private final TableView<ProductVariantValue> table = new TableView<>();
    private final Label paceScope = new Label();
    private ProductDetailData data;
    private boolean updating;

    ProductVariantsPane() {
        setSpacing(12); getStyleClass().add("product-tab-content");
        table.setId("stats-product-variants-table");
        dimension.setId("stats-product-variant-dimension");
        metric.setId("stats-product-variant-metric");
        metric.getItems().setAll(ProductMetric.values()); metric.setValue(ProductMetric.QUANTITY);
        dimension.valueProperty().addListener((observable, before, after) -> { if (!updating) render(); });
        metric.valueProperty().addListener((observable, before, after) -> render());
        // Keep every metric reachable through horizontal scrolling in a narrow split pane.
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(360); table.setPlaceholder(new Label("Nessuna variante nel periodo"));
        table.getColumns().addAll(TableColumns.text("Variante", ProductVariantValue::variant),
                paceColumn("Dischi/sett.", value -> value.pace().weeklyQuantity(),
                        "Vendite positive nel periodo / giorni di calendario × 7, inclusi i giorni senza vendite."),
                paceColumn("Tipico/doc.", value -> value.pace().medianDocumentQuantity(),
                        "Mediana delle quantità positive per documento che contiene questa variante; le righe dello stesso documento si sommano."),
                paceColumn("Media/doc.", value -> value.pace().meanDocumentQuantity(),
                        "Quantità positiva venduta / documenti che contengono questa variante. Resi e note di credito esclusi."),
                TableColumns.numeric("Quantità", value -> value.totals().quantity(), formats::number),
                TableColumns.numeric("Fatturato", value -> value.totals().revenue(), formats::money));
        table.getColumns().get(0).setPrefWidth(90);
        table.getColumns().get(4).setPrefWidth(90);
        table.getColumns().get(5).setPrefWidth(125);
        var share = TableColumns.<ProductVariantValue>numeric("Quota %", value -> share(value), formats::percent);
        share.setMinWidth(120);
        share.setPrefWidth(180);
        share.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty); setText(null); setGraphic(null);
                if (!empty) {
                    ProgressBar bar = new ProgressBar(value == null ? 0 : Math.max(0, Math.min(1, value.doubleValue() / 100)));
                    bar.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(bar, Priority.ALWAYS);
                    HBox line = new HBox(8, bar, new Label(formats.percent(value))); line.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    setGraphic(line);
                }
            }
        });
        table.getColumns().add(share);
        paceScope.setWrapText(true);
        paceScope.setMinHeight(Region.USE_PREF_SIZE);
        paceScope.getStyleClass().add("product-scope-note");
        FlowPane controls = new FlowPane(10, 8,
                new HBox(8, new Label("Ripartizione"), dimension), new HBox(8, new Label("Misura"), metric));
        getChildren().addAll(controls, paceScope, table);
    }
    void show(ProductDetailData data) {
        this.data = data; updating = true;
        var pace = data.pace();
        var date = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        paceScope.setText("Ritmo e acquisto tipico dal " + pace.from().format(date) + " al " + pace.through().format(date)
                + ". Vendite positive; quantità e fatturato restano netti. Passa sui valori per il numero di documenti.");
        ProductVariantDimension previous = dimension.getValue();
        dimension.getItems().setAll(data.variants().keySet().stream().sorted().toList());
        dimension.setValue(dimension.getItems().contains(previous) ? previous : dimension.getItems().getFirst());
        updating = false; render();
    }
    private TableColumn<ProductVariantValue, BigDecimal> paceColumn(String title,
            Function<ProductVariantValue, BigDecimal> value, String explanation) {
        var column = TableColumns.<ProductVariantValue>numeric(title, value, formats::pace);
        column.setMinWidth(125);
        column.setPrefWidth(130);
        Label info = new Label("ⓘ");
        info.setTooltip(new Tooltip(explanation));
        column.setGraphic(info);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty ? null : formats.pace(amount));
                setTooltip(null);
                var row = getTableRow() == null ? null : getTableRow().getItem();
                if (!empty && row != null) {
                    var pace = row.pace();
                    setTooltip(new Tooltip(explanation + "\n" + pace.documents() + " documenti di vendita · "
                            + formats.number(pace.soldQuantity()) + " dischi venduti · " + pace.days() + " giorni osservati."
                            + "\nUltimi " + pace.recentDays() + " giorni: " + formats.pace(pace.recentWeeklyQuantity()) + " dischi/settimana."
                            + (pace.documents() < 5 ? "\nCampione ridotto: meno di 5 documenti." : "")));
                }
            }
        });
        return column;
    }
    private BigDecimal share(ProductVariantValue value) {
        return data == null ? null : ProductTotalsCalculator.share(metric.getValue().value(value.totals()), metric.getValue().value(data.group().current()));
    }
    private void render() {
        if (data == null || dimension.getValue() == null) return;
        table.getSortOrder().clear();
        table.getItems().setAll(data.variants().get(dimension.getValue()).stream().sorted(
                Comparator.comparing((ProductVariantValue value) -> metric.getValue().value(value.totals())).reversed()
                        .thenComparing(ProductVariantValue::variant)).toList());
        table.refresh();
    }
}

package com.orodent.ecos.statistiche.features.sales.products.view;

import com.orodent.ecos.statistiche.core.components.TableColumns;
import com.orodent.ecos.statistiche.features.sales.analysis.model.AnalysisPageData;
import com.orodent.ecos.statistiche.features.sales.analysis.model.AnalysisSummary;
import com.orodent.ecos.statistiche.features.sales.analysis.view.AnalysisView;
import com.orodent.ecos.statistiche.features.sales.products.model.*;
import com.orodent.ecos.statistiche.features.sales.products.service.ProductTotalsCalculator;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class ProductsView extends AnalysisView<ProductGroupSummary> {
    private final ProductFormats formats = new ProductFormats();
    private final ComboBox<ProductGrouping> grouping = new ComboBox<>();
    private final ComboBox<ProductRanking> ranking = new ComboBox<>();
    private final TextField search = new TextField();
    private final Label scope = new Label();
    private final TableView<UnclassifiedProduct> otherTable = new TableView<>();
    private final TitledPane other = new TitledPane();
    private final ProductDetailPane detail = new ProductDetailPane();
    private ProductOverview overview;
    private boolean updating;
    private Consumer<Integer> yearChanged = ignored -> { };
    private Consumer<ProductGrouping> groupingChanged = ignored -> { };
    private Consumer<ProductGroupSummary> selected = ignored -> { };
    private TableColumn<ProductGroupSummary, BigDecimal> quantityColumn, revenueColumn, customersColumn, shareColumn;

    public ProductsView() {
        super("Analisi prodotti", "Famiglie, varianti e clienti acquirenti");
        getStyleClass().add("products-analysis-page");
        metrics.setVisible(false); metrics.setManaged(false);
        grouping.getItems().setAll(ProductGrouping.MATERIAL, ProductGrouping.FAMILY, ProductGrouping.FAMILY_COLOR);
        grouping.setValue(ProductGrouping.FAMILY);
        ranking.getItems().setAll(ProductRanking.values()); ranking.setValue(ProductRanking.QUANTITY);
        search.setPromptText("Famiglia o colore"); search.setPrefWidth(190);
        refreshButton().setText("Ricarica dati");
        filterBar.getChildren().addAll(1, List.of(field("Raggruppa per", grouping), field("Ordina per", ranking), field("Cerca", search)));
        FlowPane filters = new FlowPane(12, 10); filters.setAlignment(Pos.BOTTOM_LEFT);
        filters.getStyleClass().add("dashboard-filters");
        var fields = List.copyOf(filterBar.getChildren()); filterBar.getChildren().clear(); filters.getChildren().setAll(fields);
        content().getChildren().set(0, filters);
        scope.setWrapText(true); scope.setMinHeight(Region.USE_PREF_SIZE); scope.getStyleClass().add("product-scope-note");
        Label heading = new Label("Classifica prodotti"); heading.getStyleClass().add("product-section-title");
        configureOther();
        VBox rankingPane = new VBox(12, heading, table, other); rankingPane.getStyleClass().add("product-ranking");
        rankingPane.setMinWidth(340); table.setPrefHeight(450);
        ScrollPane detailScroll = new ScrollPane(detail); detailScroll.setFitToWidth(true);
        detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); detailScroll.getStyleClass().add("product-detail-scroll");
        SplitPane split = new SplitPane(rankingPane, detailScroll); split.setDividerPositions(0.46);
        split.setMinHeight(510); split.setPrefHeight(650); split.getStyleClass().add("product-analysis-split"); VBox.setVgrow(split, Priority.ALWAYS);
        content().getChildren().remove(table); content().getChildren().addAll(2, List.of(scope, split));
        ScrollPane pageScroll = new ScrollPane(content()); pageScroll.setFitToWidth(true); pageScroll.getStyleClass().add("product-page-scroll");
        setCenter(pageScroll);
        yearBox().valueProperty().addListener((observable, before, after) -> {
            if (!updating && after != null && !after.equals(before)) yearChanged.accept(after);
        });
        grouping.valueProperty().addListener((observable, before, after) -> {
            if (!updating && after != null && after != before) groupingChanged.accept(after);
        });
        ranking.valueProperty().addListener((observable, before, after) -> sort());
        search.textProperty().addListener((observable, before, after) -> filter());
        table.getSelectionModel().selectedItemProperty().addListener((observable, before, after) -> {
            if (!updating && after != null) selected.accept(after);
        });
    }

    @Override protected void configureTable(TableView<ProductGroupSummary> table) {
        var name = TableColumns.<ProductGroupSummary>text("Gruppo", value -> value.key().label()); name.setMinWidth(130);
        quantityColumn = TableColumns.numeric("Quantità", value -> value.current().quantity(), value -> formats.number(value)); quantityColumn.setMinWidth(85);
        revenueColumn = TableColumns.numeric("Fatturato", value -> value.current().revenue(), value -> formats.money(value)); revenueColumn.setMinWidth(105);
        customersColumn = TableColumns.numeric("Clienti", value -> BigDecimal.valueOf(value.current().customers()), value -> formats.number(value)); customersColumn.setMinWidth(55);
        shareColumn = TableColumns.numeric("Quota quantità %", value -> share(value), value -> formats.percent(value)); shareColumn.setMinWidth(65);
        table.getColumns().addAll(name, quantityColumn, revenueColumn, customersColumn, shareColumn);
    }

    public ProductGrouping grouping() { return grouping.getValue(); }
    public void onYearChanged(Consumer<Integer> action) { yearChanged = action; }
    public void onGroupingChanged(Consumer<ProductGrouping> action) { groupingChanged = action; }
    public void onSelected(Consumer<ProductGroupSummary> action) { selected = action; }
    public void onCustomer(Consumer<String> action) { detail.onCustomer(action); }

    @Override public void showLoading() {
        super.showLoading(); grouping.setDisable(true); ranking.setDisable(true); table.setDisable(true); search.setDisable(true);
        detail.clear("Caricamento dell'analisi prodotti…");
    }
    @Override public void showError(Throwable error) {
        super.showError(error);
        overview = null; metrics.getChildren().clear(); table.getItems().clear(); otherTable.getItems().clear();
        grouping.setDisable(true); ranking.setDisable(true); table.setDisable(true); search.setDisable(true);
        scope.setText("Dati non disponibili: riprova con Ricarica dati.");
        detail.clear("Impossibile caricare i dati. Puoi riprovare con Ricarica dati.");
    }
    public void showOverview(ProductOverview data, ProductGroupKey preferred) {
        overview = data; updating = true;
        try {
            grouping.setValue(data.grouping());
            super.showData(new AnalysisPageData<>(data.snapshot().years(), data.snapshot().year(),
                    new AnalysisSummary(data.classifiedTotals().revenue(), data.classifiedTotals().quantity(), BigDecimal.ZERO, data.groups().size()), data.groups()));
            otherTable.getItems().setAll(data.unclassified());
            other.setText("Altri / non classificati · " + data.unclassified().size() + " codici · " + formats.money(data.unclassifiedTotals().revenue()));
            updateScope();
        } finally { updating = false; }
        grouping.setDisable(false); ranking.setDisable(false); table.setDisable(false); search.setDisable(false);
        applyFilter(preferred, true);
    }
    public void showDetailLoading(ProductGroupKey key) { detail.loading(key); }
    public void showDetail(ProductDetailData data) { detail.show(data); }
    public void showDetailError(Throwable error) { detail.clear(error.getMessage() == null ? "Impossibile caricare il dettaglio." : error.getMessage()); }
    public void dispose() { detail.dispose(); }

    private void filter() {
        var current = table.getSelectionModel().getSelectedItem();
        applyFilter(current == null ? null : current.key(), false);
    }
    private void applyFilter(ProductGroupKey preferred, boolean forceNotify) {
        if (overview == null) return;
        var previous = table.getSelectionModel().getSelectedItem();
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        updating = true;
        try {
            table.getItems().setAll(overview.groups().stream().filter(value -> value.key().label().toLowerCase(Locale.ROOT).contains(query)).toList());
            sort();
            ProductGroupSummary choice = table.getItems().stream().filter(value -> value.key().equals(preferred)).findFirst().orElse(null);
            if (choice == null && preferred != null) choice = table.getItems().stream()
                    .filter(value -> value.key().relatedTo(preferred)).findFirst().orElse(null);
            if (choice == null && !table.getItems().isEmpty()) choice = table.getItems().getFirst();
            if (choice != null) table.getSelectionModel().select(choice);
        } finally { updating = false; }
        var current = table.getSelectionModel().getSelectedItem();
        if (current == null) {
            detail.clear("Nessun gruppo disponibile per l'anno o la ricerca selezionati.");
            selected.accept(null);
        }
        else if (forceNotify || !current.equals(previous)) selected.accept(current);
    }
    private void sort() {
        if (ranking.getValue() == null) return;
        TableColumn<ProductGroupSummary, BigDecimal> column = switch (ranking.getValue()) {
            case QUANTITY -> quantityColumn; case REVENUE -> revenueColumn; case CUSTOMERS -> customersColumn;
        };
        column.setSortType(TableColumn.SortType.DESCENDING); table.getSortOrder().setAll(column); table.sort();
        shareColumn.setText("Quota %");
        updateScope();
        table.refresh();
    }
    private void updateScope() {
        if (overview == null) return;
        scope.setText("Classificati: " + overview.groups().size() + " gruppi · Quantità netta: " + formats.number(overview.classifiedTotals().quantity())
                + " · Fatturato netto: " + formats.money(overview.classifiedTotals().revenue()) + " · Clienti: " + overview.classifiedTotals().customers()
                + "\nDiametri e altezze aggregati · Materie prime distinte · Totali netti fino al "
                + overview.snapshot().reference().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + ". Quote " + (ranking.getValue().metric() == ProductMetric.QUANTITY ? "quantità" : "fatturato")
                + " sul totale dei prodotti classificati, anche durante la ricerca.");
    }

    private BigDecimal share(ProductGroupSummary value) {
        return overview == null ? null : ProductTotalsCalculator.share(ranking.getValue().metric().value(value.current()), ranking.getValue().metric().value(overview.classifiedTotals()));
    }
    private VBox field(String title, javafx.scene.Node control) {
        Label label = new Label(title); label.getStyleClass().add("filter-label"); return new VBox(5, label, control);
    }
    private void configureOther() {
        other.setExpanded(false); other.setContent(otherTable); other.getStyleClass().add("product-unclassified");
        otherTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); otherTable.setPrefHeight(180);
        otherTable.setPlaceholder(new Label("Nessun prodotto fuori classificazione"));
        otherTable.getColumns().addAll(TableColumns.text("Codice", UnclassifiedProduct::code),
                TableColumns.text("Descrizione", UnclassifiedProduct::description),
                TableColumns.numeric("Quantità", value -> value.totals().quantity(), formats::number),
                TableColumns.numeric("Fatturato", value -> value.totals().revenue(), formats::money));
    }
}

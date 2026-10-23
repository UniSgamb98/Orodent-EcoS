package com.orodent.ecos.statistiche.features.sales.customers.view;

import com.orodent.ecos.statistiche.features.sales.projection.model.*;

import com.orodent.ecos.statistiche.features.sales.analysis.model.*;
import com.orodent.ecos.statistiche.core.components.ChartPointTooltip;
import com.orodent.ecos.statistiche.core.components.ContentSizedTabPane;
import com.orodent.ecos.statistiche.features.sales.analysis.view.AnalysisView;
import com.orodent.ecos.statistiche.features.sales.customers.model.*;
import static com.orodent.ecos.statistiche.core.components.TableColumns.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

public final class CustomersView extends AnalysisView<CustomerAnalysisItem> {
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final NumberFormat number = NumberFormat.getNumberInstance(Locale.ITALY);
    private final TextField search = new TextField();
    private final VBox detail = new VBox(14);
    private final ScrollPane detailScroll = new ScrollPane(detail);
    private final CustomerDetailScrollState detailScrollState = new CustomerDetailScrollState(detailScroll);
    private String detailCustomerCode;
    private boolean detailAvailable;
    private final List<ChartPointTooltip<String, Number>> yearlyTooltips = new ArrayList<>();
    private final Label detailTitle = new Label("Seleziona un cliente");
    private final Label detailSubtitle = new Label("Clicca una riga per aprire storico, prodotti e frequenza di acquisto.");
    private final CustomerKpiPane detailMetrics = new CustomerKpiPane();
    private final CustomerMonthlyPane monthlyPane = new CustomerMonthlyPane();
    private final StackedBarChart<String, Number> yearlyChart = new StackedBarChart<>(new CategoryAxis(), new NumberAxis());
    private final TableView<CustomerProductItem> products = new TableView<>();
    private final ProgressIndicator detailLoading = new ProgressIndicator();
    private final TabPane detailTabs = new ContentSizedTabPane();
    private final CustomerTrendPane trendPane = new CustomerTrendPane();
    private final Label projectionNote = new Label();
    private Consumer<CustomerAnalysisItem> selected = item -> { };
    private List<CustomerAnalysisItem> allCustomers = List.of();
    private boolean notifyCustomerSelection = true;
    private final CustomerFollowUpPane customerList;
    private boolean notifyYearSelection = true;
    private Consumer<Integer> yearSelected = year -> { };

    public CustomersView() {
        super("Analisi clienti", "Fatturato, comportamento d'acquisto e storico per cliente");
        configureFilters();
        configureDetail();

        detailScroll.setFitToWidth(true);
        detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detailScroll.getStyleClass().add("customer-detail-scroll");
        customerList = new CustomerFollowUpPane(table, item -> {
            if (notifyCustomerSelection) selected.accept(item);
        });
        SplitPane split = new SplitPane(customerList, detailScroll);
        split.setDividerPositions(0.55);
        split.setMinHeight(480);
        split.setPrefHeight(620);
        VBox.setVgrow(split, Priority.ALWAYS);
        content().getChildren().remove(table);
        content().getChildren().add(2, split);
        setCenter(null);
        ScrollPane pageScroll = new ScrollPane(content());
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.getStyleClass().add("dashboard-scroll");
        setCenter(pageScroll);
    }

    private void configureFilters() {
        filterBar.getChildren().remove(refreshButton());
        yearBox().valueProperty().addListener((observable, previous, current) -> {
            if (notifyYearSelection && current != null && !Objects.equals(previous, current)) {
                yearSelected.accept(current);
            }
        });
        search.setPromptText("Codice o ragione sociale");
        search.setPrefWidth(260);
        Label searchLabel = new Label("Cerca cliente");
        searchLabel.getStyleClass().add("filter-label");
        filterBar.getChildren().add(1, new VBox(5, searchLabel, search));
        search.textProperty().addListener((obs, old, value) -> filterCustomers(value));
    }

    private void configureDetail() {
        detail.getStyleClass().add("customer-detail");
        detail.setPadding(new Insets(18));
        detailTitle.getStyleClass().add("section-title");
        detailSubtitle.getStyleClass().add("muted-label");
        detailSubtitle.setWrapText(true);
        detailLoading.setMaxSize(34, 34);
        detailLoading.setVisible(false);
        configureProducts();

        yearlyChart.getStyleClass().add("customer-yearly-chart");
        projectionNote.getStyleClass().add("customer-projection-note");
        projectionNote.setWrapText(true);
        VBox yearlyContent = new VBox(8, yearlyChart, projectionNote);
        VBox.setVgrow(yearlyChart, Priority.ALWAYS);
        detailTabs.getTabs().setAll(new Tab("Trend recente", trendPane), new Tab("Storico annuale", yearlyContent),
                new Tab("Andamento mensile", monthlyPane), new Tab("Prodotti", products));
        detailTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        detailTabs.setPrefHeight(Region.USE_COMPUTED_SIZE);
        detailTabs.setMinHeight(Region.USE_PREF_SIZE);
        detailTabs.getStyleClass().add("customer-detail-tabs");
        detailTabs.setVisible(false);
        detailTabs.setManaged(false);
        VBox.setVgrow(detailTabs, Priority.ALWAYS);
        HBox detailHeading = new HBox(10, detailTitle, detailLoading);
        detailHeading.setAlignment(Pos.CENTER_LEFT);
        detailHeading.setMinHeight(34);
        HBox.setHgrow(detailTitle, Priority.ALWAYS);
        detailTitle.setMaxWidth(Double.MAX_VALUE);
        detail.getChildren().setAll(detailHeading, detailSubtitle, detailMetrics, detailTabs);
        detailMetrics.setVisible(false);
        detailMetrics.setManaged(false);
    }

    @Override protected void configureTable(TableView<CustomerAnalysisItem> table) {
        table.getColumns().addAll(text("Codice", CustomerAnalysisItem::code),
                text("Ragione sociale", CustomerAnalysisItem::name),
                value("Fatturato", item -> item.revenue(), amount -> currency.format(amount)),
                value("Quantità", item -> item.quantity(), amount -> number.format(amount)),
                text("PriceList", item -> item.priceList() == null || item.priceList().isBlank() ? "—" : item.priceList()));
    }

    public void showOverview(CustomerOverviewData overview) {
        notifyYearSelection = false;
        try {
            updateSelectionSilently(() -> {
                showData(overview.customers());
                allCustomers = overview.customers().items();
                customerList.show(allCustomers, overview.followUp());
                customerList.search(search.getText());
            });
        } finally {
            notifyYearSelection = true;
        }
    }

    public void onYearChanged(Consumer<Integer> handler) { yearSelected = handler; }

    private void filterCustomers(String text) {
        if (customerList != null) updateSelectionSilently(() -> customerList.search(text));
    }

    public void onCustomerSelected(Consumer<CustomerAnalysisItem> handler) { selected = handler; }
    public int comparisonYears() { return monthlyPane.comparisonYears(); }
    public void onComparisonYearsChanged(Runnable handler) { monthlyPane.onComparisonYearsChanged(handler); }

    public void selectCustomerSilently(CustomerAnalysisItem customer) {
        updateSelectionSilently(() -> {
            customerList.select(customer);
            if (table.getItems().contains(customer)) {
                table.getSelectionModel().select(customer);
                table.scrollTo(customer);
            } else {
                table.getSelectionModel().clearSelection();
            }
        });
    }

    public void clearCustomerSelection() {
        updateSelectionSilently(() -> {
            table.getSelectionModel().clearSelection();
            customerList.clearSelection();
        });
    }

    public void showCustomerUnavailable(String customerName, String customerCode, int year) {
        detailScrollState.reset();
        detailCustomerCode = customerCode;
        detailAvailable = false;
        detailLoading.setVisible(false);
        detailMetrics.setVisible(false);
        detailMetrics.setManaged(false);
        detailTabs.setVisible(false);
        detailTabs.setManaged(false);
        detailTitle.setText(customerName == null || customerName.isBlank() ? customerCode : customerName);
        detailSubtitle.setText("Il cliente " + customerCode + " non ha vendite disponibili per il " + year + ".");
    }

    private void updateSelectionSilently(Runnable update) {
        notifyCustomerSelection = false;
        try {
            update.run();
        } finally {
            notifyCustomerSelection = true;
        }
    }

    public void showDetailLoading(CustomerAnalysisItem customer) {
        boolean preserveDetail = detailAvailable && Objects.equals(detailCustomerCode, customer.code());
        if (preserveDetail) {
            detailScrollState.capture();
        } else {
            detailScrollState.reset();
            detailAvailable = false;
        }
        detailCustomerCode = customer.code();
        detailTitle.setText(customer.name());
        detailSubtitle.setText("Caricamento del profilo cliente…");
        detailLoading.setVisible(true);
        if (!preserveDetail) {
            detailMetrics.setVisible(false);
            detailMetrics.setManaged(false);
            detailTabs.setVisible(false);
            detailTabs.setManaged(false);
        }
    }

    public void showDetail(CustomerDetailData data) {
        CustomerDetail value = data.detail();
        detailTitle.setText(value.name());
        detailSubtitle.setText("Codice " + value.code() + anagraphic(value));
        detailLoading.setVisible(false);
        detailMetrics.show(value, data.projection());
        detailMetrics.setVisible(true);
        detailMetrics.setManaged(true);
        trendPane.show(data.trend());
        updateYearly(data.yearly(), data.projection());
        updateProjectionNote(data.projection());
        monthlyPane.showProjections(data.monthlyProjections());
        showMonthlyHistory(data.monthly());
        products.getItems().setAll(data.products());
        detailTabs.setVisible(true);
        detailTabs.setManaged(true);
        detailCustomerCode = value.code();
        detailAvailable = true;
        detailScrollState.restoreAfterLayout();
    }

    public void showMonthlyLoading() { monthlyPane.showLoading(); }

    public void showMonthlyHistory(List<CustomerMonthlyValue> values) { monthlyPane.showHistory(values); }

    public void showMonthlyError(Throwable error) { monthlyPane.showError(error); }

    public void dispose() {
        yearlyTooltips.forEach(ChartPointTooltip::close);
        yearlyTooltips.clear();
        yearlyChart.getData().clear();
        monthlyPane.dispose();
        trendPane.dispose();
        detailScrollState.dispose();
    }

    public void showDetailError(Throwable error) {
        detailScrollState.restoreAfterLayout();
        detailLoading.setVisible(false);
        detailSubtitle.setText(error == null || error.getMessage() == null
                ? "Impossibile caricare il cliente." : error.getMessage());
    }

    @Override protected void updateSummary(AnalysisPageData<CustomerAnalysisItem> data) {
        metrics.getChildren().setAll(metric("Clienti attivi", number.format(data.summary().count())),
                metric("Fatturato", currency.format(data.summary().primaryValue())),
                metric("Quantità", number.format(data.summary().secondaryValue())));
    }

    private void updateYearly(List<CustomerYearSummary> values, RevenueProjection projection) {
        yearlyTooltips.forEach(ChartPointTooltip::close);
        yearlyTooltips.clear();
        XYChart.Series<String, Number> actual = new XYChart.Series<>(); actual.setName("Fatturato registrato");
        XYChart.Series<String, Number> forecast = new XYChart.Series<>(); forecast.setName("Residuo previsto");
        values.forEach(value -> {
            String year = Integer.toString(value.year());
            XYChart.Data<String, Number> actualPoint = new XYChart.Data<>(year, value.revenue());
            yearlyTooltips.add(new ChartPointTooltip<>(actualPoint,
                    () -> year + " — Fatturato registrato\n" + currency.format(actualPoint.getYValue())));
            actual.getData().add(actualPoint);
            BigDecimal remaining = value.year() == projection.year()
                    ? projection.projectedRemainingRevenue() : BigDecimal.ZERO;
            XYChart.Data<String, Number> forecastPoint = new XYChart.Data<>(year, remaining);
            if (value.year() == projection.year() && projection.method() != ProjectionMethod.ACTUAL) {
                yearlyTooltips.add(new ChartPointTooltip<>(forecastPoint,
                        () -> year + " — Residuo previsto · Stima\n" + currency.format(forecastPoint.getYValue())
                                + "\nTotale annuo proiettato: " + currency.format(projection.projectedAnnualRevenue())));
            }
            forecast.getData().add(forecastPoint);
        });
        yearlyChart.getData().setAll(actual, forecast);
        yearlyChart.setLegendVisible(true);
        yearlyChart.setAnimated(false);
    }

    private void updateProjectionNote(RevenueProjection projection) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        if (projection.method() == ProjectionMethod.ACTUAL) {
            projectionNote.setText("Consuntivo annuale · Dati disponibili dal "
                    + projection.dataFrom().format(format) + " al " + projection.dataThrough().format(format));
            return;
        }
        String method = switch (projection.method()) {
            case ACTUAL -> throw new IllegalStateException("Metodo consuntivo già gestito");
            case SEASONAL -> "stagionalità di " + projection.historicalYears() + " anni storici";
            case LINEAR -> "andamento lineare (storico insufficiente)";
        };
        projectionNote.setText("Proiezione al 31/12 basata su " + method
                + " · Affidabilità " + projection.confidence().label().toLowerCase(Locale.ITALY)
                + " · Dati disponibili dal " + projection.dataFrom().format(format)
                + " al " + projection.dataThrough().format(format));
    }

    private void configureProducts() {
        products.getColumns().addAll(text("Codice", CustomerProductItem::code),
                text("Descrizione", CustomerProductItem::description),
                value("Fatturato", item -> item.revenue(), amount -> currency.format(amount)),
                value("Quantità", item -> item.quantity(), amount -> number.format(amount)));
        products.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private String anagraphic(CustomerDetail value) {
        List<String> parts = new ArrayList<>();
        if (value.country()!=null) parts.add(value.country()); if(value.customerType()!=null)parts.add(value.customerType());
        if(value.agent()!=null)parts.add("Agente " + value.agent()); return parts.isEmpty()?"":" · "+String.join(" · ",parts);
    }

}

package com.orodent.ecos.statistiche.features.sales.dashboard.view;

import com.orodent.ecos.statistiche.core.components.AppHeader;
import com.orodent.ecos.statistiche.core.components.ChartPointTooltip;
import com.orodent.ecos.statistiche.features.sales.projection.view.MonthlyForecastLayer;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.MonthlySales;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesDashboardData;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesRankingItem;
import static com.orodent.ecos.statistiche.core.components.TableColumns.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;

public final class SalesDashboardView extends BorderPane {

    private final AppHeader header = new AppHeader(
            "Dashboard vendite",
            "Andamento, clienti e prodotti in un'unica vista"
    );
    private final ComboBox<Integer> yearBox = new ComboBox<>();
    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();
    private final ComboBox<PeriodPreset> periodBox = new ComboBox<>();
    private final Button applyButton = new Button("Applica");
    private final Button retryButton = new Button("Riprova");
    private final Button importButton = new Button("Importa vendite");
    private final ProgressIndicator loading = new ProgressIndicator();
    private final Label errorLabel = new Label();
    private final VBox emptyState = new VBox(12);
    private final VBox dashboard = new VBox(22);
    private final DashboardKpiPane kpiPane = new DashboardKpiPane();
    private final TopCustomersHistoryPane topCustomersHistoryPane = new TopCustomersHistoryPane();
    private final AnnualSalesComparisonPane annualComparisonPane = new AnnualSalesComparisonPane();
    private final AnnualSalesTable annualTable = new AnnualSalesTable();
    private final ComboBox<ChartMetric> chartMetricBox = new ComboBox<>();
    private final LineChart<String, Number> monthlyChart = createMonthlyChart();
    private final MonthlyForecastLayer forecast = new MonthlyForecastLayer();
    private final TableView<SalesRankingItem> customersTable = createRankingTable(false);
    private final TableView<SalesRankingItem> productsTable = createRankingTable(true);
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final NumberFormat number = NumberFormat.getNumberInstance(Locale.ITALY);
    private final List<ChartPointTooltip<String, Number>> monthlyTooltips = new ArrayList<>();
    private SalesDashboardData currentData;

    public SalesDashboardView() {
        getStyleClass().add("page");
        setTop(buildFixedHeader());
        setCenter(buildContent());
        showLoading();
    }

    public AppHeader header() {
        return header;
    }

    public ComboBox<Integer> yearBox() {
        return yearBox;
    }

    public DatePicker fromPicker() {
        return fromPicker;
    }

    public DatePicker toPicker() {
        return toPicker;
    }

    public Button applyButton() {
        return applyButton;
    }

    public ComboBox<PeriodPreset> periodBox() {
        return periodBox;
    }

    public Button retryButton() {
        return retryButton;
    }

    public Button importButton() {
        return importButton;
    }

    public void dispose() {
        forecast.close();
        monthlyTooltips.forEach(ChartPointTooltip::close);
        monthlyTooltips.clear();
        monthlyChart.getData().clear();
        topCustomersHistoryPane.dispose();
        annualComparisonPane.dispose();
        currentData = null;
    }

    public void showLoading() {
        setFiltersDisabled(true);
        loading.setVisible(true);
        loading.setManaged(true);
        dashboard.setVisible(false);
        dashboard.setManaged(false);
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        retryButton.setVisible(false);
        retryButton.setManaged(false);
    }

    public void showData(SalesDashboardData data) {
        setFiltersDisabled(false);
        loading.setVisible(false);
        loading.setManaged(false);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        retryButton.setVisible(false);
        retryButton.setManaged(false);

        yearBox.setItems(FXCollections.observableArrayList(data.availableYears()));
        yearBox.setValue(data.selectedYear());
        fromPicker.setValue(data.filter().from());
        toPicker.setValue(data.filter().to());
        currentData = data;

        if (data.empty()) {
            dashboard.setVisible(false);
            dashboard.setManaged(false);
            emptyState.setVisible(true);
            emptyState.setManaged(true);
            return;
        }

        emptyState.setVisible(false);
        emptyState.setManaged(false);
        kpiPane.show(data);
        topCustomersHistoryPane.show(data.selectedYear(), data.topCustomerHistory());
        updateAnnualComparison(data);
        updateChart(data);
        customersTable.getItems().setAll(data.topCustomers());
        productsTable.getItems().setAll(data.topProducts());
        dashboard.setVisible(true);
        dashboard.setManaged(true);
    }

    public void showError(Throwable error) {
        setFiltersDisabled(false);
        loading.setVisible(false);
        loading.setManaged(false);
        dashboard.setVisible(false);
        dashboard.setManaged(false);
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        errorLabel.setText(error == null || error.getMessage() == null
                ? "Impossibile caricare le statistiche."
                : error.getMessage());
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        retryButton.setVisible(true);
        retryButton.setManaged(true);
    }

    private VBox buildFixedHeader() {
        VBox filterSection = new VBox(buildFilters());
        filterSection.getStyleClass().add("dashboard-fixed-filters");
        return new VBox(header, filterSection);
    }

    private StackPane buildContent() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 34, 36, 34));
        HBox errorState = new HBox(12, errorLabel, retryButton);
        errorState.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(buildEmptyState(), dashboard, errorState);
        buildDashboard();

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("dashboard-scroll");

        StackPane stack = new StackPane(scrollPane, loading);
        StackPane.setAlignment(loading, Pos.CENTER);
        return stack;
    }

    private HBox buildFilters() {
        yearBox.setPrefWidth(110);
        periodBox.setItems(FXCollections.observableArrayList(PeriodPreset.values()));
        periodBox.setValue(PeriodPreset.WHOLE_YEAR);
        periodBox.setPrefWidth(150);
        fromPicker.setPrefWidth(145);
        toPicker.setPrefWidth(145);
        applyButton.getStyleClass().add("primary-button");

        HBox filters = new HBox(12,
                filterField("Anno", yearBox),
                filterField("Periodo", periodBox),
                filterField("Dal", fromPicker),
                filterField("Al", toPicker),
                applyButton
        );
        filters.getStyleClass().add("dashboard-filters");
        filters.setAlignment(Pos.BOTTOM_LEFT);
        return filters;
    }

    private VBox filterField(String label, javafx.scene.Node control) {
        Label title = new Label(label);
        title.getStyleClass().add("filter-label");
        return new VBox(5, title, control);
    }

    private VBox buildEmptyState() {
        emptyState.getStyleClass().add("dashboard-empty");
        emptyState.setAlignment(Pos.CENTER);
        Label title = new Label("Nessuna vendita disponibile");
        title.getStyleClass().add("section-title");
        Label description = new Label(
                "Importa un file annuale per visualizzare fatturato, andamento, clienti e prodotti."
        );
        description.getStyleClass().add("muted-label");
        importButton.getStyleClass().add("primary-button");
        emptyState.getChildren().addAll(title, description, importButton);
        return emptyState;
    }

    private void buildDashboard() {
        chartMetricBox.setItems(FXCollections.observableArrayList(ChartMetric.values()));
        chartMetricBox.setValue(ChartMetric.REVENUE);
        chartMetricBox.setOnAction(event -> updateChart(currentData));
        FlowPane chartHeader = new FlowPane(12, 8, new Label("Andamento mensile"), chartMetricBox, forecast.toggle());
        chartHeader.setAlignment(Pos.CENTER_LEFT);
        chartHeader.getChildren().getFirst().getStyleClass().add("card-title");
        VBox historicalCard = card("Confronto storico automatico", annualComparisonPane);
        VBox annualTableCard = card("Riepilogo di tutti gli anni", annualTable);
        GridPane history = new GridPane();
        history.setHgap(18);
        history.add(historicalCard, 0, 0);
        history.add(annualTableCard, 1, 0);
        history.getColumnConstraints().addAll(
                new javafx.scene.layout.ColumnConstraints(0, 520, Double.MAX_VALUE, Priority.ALWAYS, null, true),
                new javafx.scene.layout.ColumnConstraints(0, 620, Double.MAX_VALUE, Priority.ALWAYS, null, true)
        );

        VBox chartCard = card(chartHeader, monthlyChart);
        chartCard.getChildren().add(forecast.note());
        GridPane rankings = new GridPane();
        rankings.setHgap(18);
        rankings.add(card("Top clienti", customersTable), 0, 0);
        rankings.add(card("Top prodotti", productsTable), 1, 0);
        GridPane.setHgrow(rankings.getChildren().get(0), Priority.ALWAYS);
        GridPane.setHgrow(rankings.getChildren().get(1), Priority.ALWAYS);
        rankings.getColumnConstraints().addAll(
                new javafx.scene.layout.ColumnConstraints(0, 520, Double.MAX_VALUE, Priority.ALWAYS, null, true),
                new javafx.scene.layout.ColumnConstraints(0, 520, Double.MAX_VALUE, Priority.ALWAYS, null, true)
        );

        dashboard.getChildren().addAll(kpiPane, history, chartCard,
                card("Top 5 clienti — evoluzione storica", topCustomersHistoryPane), rankings);
    }

    private VBox card(String title, javafx.scene.Node content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        return card(titleLabel, content);
    }

    private VBox card(javafx.scene.Node title, javafx.scene.Node content) {
        VBox card = new VBox(14);
        card.getStyleClass().add("dashboard-card");
        card.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(content, Priority.ALWAYS);
        card.getChildren().addAll(title, content);
        return card;
    }

    private LineChart<String, Number> createMonthlyChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(true);
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setLegendVisible(true);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setPrefHeight(300);
        return chart;
    }

    private TableView<SalesRankingItem> createRankingTable(boolean showDescription) {
        TableView<SalesRankingItem> table = new TableView<>();
        table.getColumns().addAll(
                text(showDescription ? "Prodotto" : "Codice", SalesRankingItem::code),
                text(showDescription ? "Descrizione" : "Cliente", SalesRankingItem::description),
                value("Fatturato", SalesRankingItem::netRevenue, amount -> currency.format(amount)),
                value("Quantità", SalesRankingItem::quantity, amount -> number.format(amount)),
                value("Documenti", SalesRankingItem::documents, amount -> number.format(amount)),
                value("Incidenza", SalesRankingItem::percentage, amount -> number.format(amount) + "%"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(310);
        table.setPlaceholder(new Label("Nessun dato"));
        return table;
    }

    private void updateChart(SalesDashboardData data) {
        if (data == null) {
            return;
        }
        forecast.close();
        monthlyTooltips.forEach(ChartPointTooltip::close);
        monthlyTooltips.clear();
        ChartMetric metric = chartMetricBox.getValue() == null ? ChartMetric.REVENUE : chartMetricBox.getValue();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(Integer.toString(data.selectedYear()));
        XYChart.Series<String, Number> previousSeries = new XYChart.Series<>();
        previousSeries.setName(Integer.toString(data.selectedYear() - 1));
        int firstMonth = data.filter().from().getMonthValue();
        int lastMonth = data.filter().to().getMonthValue();
        var monthlyProjection = metric == ChartMetric.REVENUE ? data.monthlyProjections().revenue() : data.monthlyProjections().quantity();
        var cutoffProjection = data.monthlyProjections().revenue() != null ? data.monthlyProjections().revenue() : data.monthlyProjections().quantity();
        int actualLastMonth = cutoffProjection == null ? lastMonth : cutoffProjection.annual().dataThrough().getMonthValue();
        ((CategoryAxis) monthlyChart.getXAxis()).setAutoRanging(false);
        ((CategoryAxis) monthlyChart.getXAxis()).getCategories().setAll(java.util.stream.IntStream.rangeClosed(firstMonth, lastMonth)
                .mapToObj(value -> Month.of(value).getDisplayName(TextStyle.SHORT, Locale.ITALY)).toList());
        for (int monthNumber = firstMonth; monthNumber <= lastMonth; monthNumber++) {
            MonthlySales month = findMonth(data, monthNumber);
            Number value = metric.value(month);
            String label = Month.of(monthNumber).getDisplayName(TextStyle.SHORT, Locale.ITALY);
            XYChart.Data<String, Number> point = new XYChart.Data<>(label, value);
            String monthName = Month.of(monthNumber).getDisplayName(TextStyle.FULL, Locale.ITALY);
            monthlyTooltips.add(new ChartPointTooltip<>(point,
                    () -> monthName
                            + " " + series.getName() + "\n" + metric.label() + ": "
                            + formatChartValue(metric, point.getYValue()), series.nameProperty()));
            if (monthNumber <= actualLastMonth) series.getData().add(point);
            MonthlySales previousMonth = findMonth(data.previousMonthlySales(), monthNumber);
            XYChart.Data<String, Number> previousPoint = new XYChart.Data<>(label, metric.value(previousMonth));
            monthlyTooltips.add(new ChartPointTooltip<>(previousPoint,
                    () -> monthName + " " + previousSeries.getName() + "\n" + metric.label()
                            + ": " + formatChartValue(metric, previousPoint.getYValue()), previousSeries.nameProperty()));
            previousSeries.getData().add(previousPoint);
        }
        if (data.previousMonthlySales().isEmpty()) {
            monthlyChart.getData().setAll(series);
        } else {
            monthlyChart.getData().setAll(series, previousSeries);
        }
        forecast.show(monthlyChart, monthlyProjection, value -> formatChartValue(metric, value));
    }

    private String formatChartValue(ChartMetric metric, Number value) {
        NumberFormat format = metric == ChartMetric.REVENUE
                ? NumberFormat.getCurrencyInstance(Locale.ITALY)
                : NumberFormat.getNumberInstance(Locale.ITALY);
        format.setMaximumFractionDigits(340);
        return format.format(value) + (metric == ChartMetric.QUANTITY ? " unità" : "");
    }

    private MonthlySales findMonth(SalesDashboardData data, int month) {
        return findMonth(data.monthlySales(), month);
    }

    private MonthlySales findMonth(java.util.List<MonthlySales> values, int month) {
        return values.stream()
                .filter(value -> value.month() == month)
                .findFirst()
                .orElse(new MonthlySales(month, BigDecimal.ZERO, BigDecimal.ZERO, 0));
    }

    private void updateAnnualComparison(SalesDashboardData data) {
        annualComparisonPane.show(data.annualComparisons());
        annualTable.show(data.annualComparisons());
    }

    private void setFiltersDisabled(boolean disabled) {
        yearBox.setDisable(disabled);
        periodBox.setDisable(disabled);
        fromPicker.setDisable(disabled);
        toPicker.setDisable(disabled);
        applyButton.setDisable(disabled);
    }

    public enum PeriodPreset {
        WHOLE_YEAR("Anno intero"),
        FIRST_QUARTER("Primo trimestre"),
        SECOND_QUARTER("Secondo trimestre"),
        THIRD_QUARTER("Terzo trimestre"),
        FOURTH_QUARTER("Quarto trimestre"),
        CUSTOM("Personalizzato");

        private final String label;

        PeriodPreset(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum ChartMetric {
        REVENUE("Fatturato") {
            @Override Number value(MonthlySales month) { return month.netRevenue(); }
            @Override String format(Number value, NumberFormat currency, NumberFormat number) {
                return currency.format(value);
            }
        },
        QUANTITY("Quantità") {
            @Override Number value(MonthlySales month) { return month.quantity(); }
        },
        DOCUMENTS("Documenti") {
            @Override Number value(MonthlySales month) { return month.documents(); }
        };

        private final String label;

        ChartMetric(String label) { this.label = label; }
        abstract Number value(MonthlySales month);
        String label() { return label; }
        String format(Number value, NumberFormat currency, NumberFormat number) { return number.format(value); }
        @Override public String toString() { return label; }
    }
}

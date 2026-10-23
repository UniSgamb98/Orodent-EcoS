package com.orodent.ecos.statistiche.features.sales.analysis.view;

import com.orodent.ecos.statistiche.core.components.AppHeader;
import com.orodent.ecos.statistiche.features.sales.analysis.model.AnalysisPageData;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public abstract class AnalysisView<T> extends BorderPane {
    private final AppHeader header;
    private final ComboBox<Integer> yearBox = new ComboBox<>();
    private final Button refreshButton = new Button("Aggiorna");
    private final ProgressIndicator loading = new ProgressIndicator();
    private final Label error = new Label();
    protected final HBox metrics = new HBox(14);
    protected final TableView<T> table = new TableView<>();
    protected final HBox filterBar = new HBox(12);
    private final VBox content = new VBox(20);

    protected AnalysisView(String title, String description) {
        header = new AppHeader(title, description);
        getStyleClass().add("page");
        setTop(header);
        content.setPadding(new Insets(28, 34, 36, 34));
        Label yearLabel = new Label("Anno");
        yearLabel.getStyleClass().add("filter-label");
        VBox yearField = new VBox(5, yearLabel, yearBox);
        refreshButton.getStyleClass().add("primary-button");
        filterBar.getChildren().addAll(yearField, refreshButton);
        filterBar.setAlignment(Pos.BOTTOM_LEFT);
        filterBar.getStyleClass().add("dashboard-filters");
        metrics.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nessun dato disponibile"));
        VBox.setVgrow(table, Priority.ALWAYS);
        error.getStyleClass().add("error-label");
        error.setVisible(false); error.setManaged(false);
        loading.setMaxSize(42, 42);
        content.getChildren().addAll(filterBar, metrics, table, error, loading);
        setCenter(content);
        configureTable(table);
    }

    protected abstract void configureTable(TableView<T> table);
    protected void updateSummary(AnalysisPageData<T> data) { }
    protected VBox content() { return content; }

    public AppHeader header() { return header; }
    public ComboBox<Integer> yearBox() { return yearBox; }
    public Button refreshButton() { return refreshButton; }

    public void showLoading() {
        loading.setVisible(true); loading.setManaged(true);
        refreshButton.setDisable(true); yearBox.setDisable(true);
        error.setVisible(false); error.setManaged(false);
    }

    public void showData(AnalysisPageData<T> data) {
        loading.setVisible(false); loading.setManaged(false);
        refreshButton.setDisable(false); yearBox.setDisable(false);
        yearBox.setItems(FXCollections.observableArrayList(data.years()));
        yearBox.setValue(data.selectedYear());
        table.getItems().setAll(data.items());
        updateSummary(data);
    }

    public void showError(Throwable throwable) {
        loading.setVisible(false); loading.setManaged(false);
        refreshButton.setDisable(false); yearBox.setDisable(false);
        error.setText(throwable == null || throwable.getMessage() == null
                ? "Impossibile caricare i dati." : throwable.getMessage());
        error.setVisible(true); error.setManaged(true);
    }

    protected VBox metric(String title, String value) {
        Label titleLabel = new Label(title); titleLabel.getStyleClass().add("muted-label");
        Label valueLabel = new Label(value); valueLabel.getStyleClass().add("dashboard-metric-value");
        VBox card = new VBox(7, titleLabel, valueLabel); card.getStyleClass().add("dashboard-metric");
        HBox.setHgrow(card, Priority.ALWAYS); card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }
}

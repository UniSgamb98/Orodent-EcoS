package com.orodent.ecos.crm.feature.clienti.clienti.view;

import com.orodent.ecos.crm.core.country.Countries;
import com.orodent.ecos.crm.core.database.model.VistaSalvata;
import com.orodent.ecos.crm.core.ui.AppSidebar;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.*;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.function.Consumer;

public class ClientiView extends BorderPane {
    private final AppSidebar sidebar = new AppSidebar();
    private final TextField searchField = new TextField();
    private final Button newClientButton = new Button("+ Nuova anagrafica");
    private final ChoiceBox<OperatoreFilter> operatorFilterChoiceBox = new ChoiceBox<>();
    private final ChoiceBox<TextFilter> typeFilterChoiceBox = textChoice("Tutti");
    private final ChoiceBox<TextFilter> statusFilterChoiceBox = textChoice("Tutti");
    private final ChoiceBox<TextFilter> countryFilterChoiceBox = textChoice("Tutti i paesi");
    private final Button clearFiltersButton = new Button("Pulisci filtri");
    private final Button saveSearchButton = new Button("Salva ricerca");
    private final MenuButton savedSearchMenu = new MenuButton("Ricerche salvate");
    private final ToggleGroup savedSearchGroup = new ToggleGroup();
    private final MenuItem updateSavedSearchItem = new MenuItem("Aggiorna con i filtri correnti");
    private final MenuItem renameSavedSearchItem = new MenuItem("Rinomina");
    private final MenuItem setDefaultSavedSearchItem = new MenuItem("Imposta come predefinita");
    private final MenuItem deleteSavedSearchItem = new MenuItem("Elimina ricerca");
    private final MenuButton manageSavedSearchButton =
            new MenuButton(
                    "Gestisci ricerca",
                    null,
                    updateSavedSearchItem,
                    renameSavedSearchItem,
                    setDefaultSavedSearchItem,
                    deleteSavedSearchItem);
    private final Label unsavedChangesLabel = new Label("Ricerca modificata");
    private final Label resultsCountLabel = new Label("Caricamento anagrafiche…");
    private final FlowPane activeFilters = new FlowPane(6, 6);
    private final ClientiResultsTable resultsTable = new ClientiResultsTable();
    private VistaSalvata selectedSavedView;
    private Consumer<VistaSalvata> applySavedSearchHandler = ignored -> {};

    public ClientiView() {
        searchField.setId("crm-clients-search");
        searchField.setPromptText("Cerca azienda, referente, telefono o email");
        searchField.getStyleClass().add("clients-search-field");
        newClientButton.getStyleClass().add("clients-primary-button");
        operatorFilterChoiceBox.getStyleClass().add("clients-operator-filter-choice");
        operatorFilterChoiceBox.getItems().add(OperatoreFilter.empty());
        operatorFilterChoiceBox.getSelectionModel().selectFirst();
        countryFilterChoiceBox.setId("crm-country-filter");
        clearFiltersButton.getStyleClass().add("clients-clear-filters-button");
        saveSearchButton.getStyleClass().add("clients-save-search-button");
        savedSearchMenu.getStyleClass().add("clients-manage-search-button");
        manageSavedSearchButton.getStyleClass().add("clients-manage-search-button");
        manageSavedSearchButton.setVisible(false);
        manageSavedSearchButton.setManaged(false);
        savedSearchMenu.setDisable(true);
        unsavedChangesLabel.getStyleClass().add("clients-unsaved-search-label");
        unsavedChangesLabel.setVisible(false);
        unsavedChangesLabel.setManaged(false);
        resultsCountLabel.getStyleClass().add("clients-results-count");
        activeFilters.getStyleClass().add("clients-active-filters");
        searchField.textProperty().addListener((o, a, b) -> updateActiveFilters());
        for (ChoiceBox<?> box :
                List.of(
                        operatorFilterChoiceBox,
                        typeFilterChoiceBox,
                        statusFilterChoiceBox,
                        countryFilterChoiceBox)) {
            box.setMinWidth(0);
            box.setMaxWidth(Double.MAX_VALUE);
            box.valueProperty().addListener((o, a, b) -> updateActiveFilters());
        }
        setLeft(sidebar);
        setCenter(createContent());
        updateActiveFilters();
    }

    private VBox createContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setMinWidth(0);
        content.getStyleClass().addAll("clients-content", "clients-list-page");
        Label title = new Label("Anagrafiche CRM");
        title.getStyleClass().add("clients-title");
        Label subtitle = new Label("Contatti, attività commerciali e prossimi richiami.");
        subtitle.getStyleClass().add("clients-subtitle");
        VBox titleBox = new VBox(3, title, subtitle);
        titleBox.setMinWidth(0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleBar = new HBox(12, titleBox, spacer, newClientButton);
        titleBar.getStyleClass().add("clients-title-bar");
        FlowPane filters = new FlowPane(10, 10);
        var search = filter("Ricerca", searchField);
        var status = filter("Stato trattativa", statusFilterChoiceBox);
        var operator = filter("Operatore", operatorFilterChoiceBox);
        var type = filter("Tipologia", typeFilterChoiceBox);
        var country = filter("Paese", countryFilterChoiceBox);
        filters.getChildren().addAll(search, status, operator, type, country);
        filters.widthProperty()
                .addListener(
                        (o, a, b) -> {
                            double width = b.doubleValue();
                            boolean single = width >= 1120;
                            double smallWidth =
                                    single
                                            ? 160
                                            : (width - (width >= 680 ? 30 : 10))
                                                    / (width >= 680 ? 4 : 2);
                            for (var item : List.of(status, operator, type, country))
                                item.setPrefWidth(Math.max(0, smallWidth));
                            search.setPrefWidth(single ? width - 680 : width);
                        });
        VBox filterCard = new VBox(10, filters, activeFilters);
        filterCard.getStyleClass().add("clients-filter-bar");
        FlowPane commands =
                new FlowPane(
                        10,
                        8,
                        resultsCountLabel,
                        savedSearchMenu,
                        manageSavedSearchButton,
                        clearFiltersButton,
                        saveSearchButton,
                        unsavedChangesLabel);
        commands.getStyleClass().add("clients-filter-actions");
        VBox.setVgrow(resultsTable, Priority.ALWAYS);
        content.getChildren().addAll(titleBar, filterCard, commands, resultsTable);
        return content;
    }

    private VBox filter(String title, Region field) {
        Label label = new Label(title);
        label.getStyleClass().add("clients-filter-label");
        field.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(5, label, field);
        box.setMinWidth(0);
        box.getStyleClass().add("clients-filter-control");
        return box;
    }

    private static ChoiceBox<TextFilter> textChoice(String title) {
        ChoiceBox<TextFilter> box = new ChoiceBox<>();
        box.getStyleClass().add("clients-operator-filter-choice");
        box.getItems().add(TextFilter.empty(title));
        box.getSelectionModel().selectFirst();
        return box;
    }

    private void updateActiveFilters() {
        activeFilters.getChildren().clear();
        if (!searchField.getText().isBlank())
            chip("Ricerca: " + searchField.getText(), searchField::clear);
        if (operatorFilterChoiceBox.getValue() != null
                && operatorFilterChoiceBox.getValue().id() != null)
            chip(
                    "Operatore: " + operatorFilterChoiceBox.getValue().label(),
                    () -> operatorFilterChoiceBox.getSelectionModel().selectFirst());
        for (var entry :
                List.of(
                        Map.entry("Stato", statusFilterChoiceBox),
                        Map.entry("Tipologia", typeFilterChoiceBox),
                        Map.entry("Paese", countryFilterChoiceBox))) {
            var box = entry.getValue();
            if (box.getValue() != null && !box.getValue().isEmptyOption())
                chip(
                        entry.getKey() + ": " + box.getValue().label(),
                        () -> box.getSelectionModel().selectFirst());
        }
        boolean present = !activeFilters.getChildren().isEmpty();
        activeFilters.setVisible(present);
        activeFilters.setManaged(present);
    }

    private void chip(String text, Runnable clear) {
        Button button =
                new Button((text.length() > 60 ? text.substring(0, 60) + "…" : text) + "  ×");
        button.setTooltip(new Tooltip("Rimuovi filtro: " + text));
        button.setAccessibleText("Rimuovi filtro: " + text);
        button.getStyleClass().add("clients-filter-chip");
        button.setOnAction(e -> clear.run());
        activeFilters.getChildren().add(button);
    }

    public void showLoading() {
        resultsTable.showLoading();
    }

    public void showEmpty() {
        resultsTable.showEmpty();
    }

    public void showError(String message) {
        resultsTable.showError(message);
    }

    public void showLoadingMore() {
        resultsTable.showLoadingMore();
    }

    public void showLoadMoreAvailable() {
        resultsTable.showLoadMoreAvailable();
    }

    public void showAllResultsLoaded() {
        resultsTable.showAllResultsLoaded();
    }

    public void clearClientRows() {
        resultsTable.clearRows();
    }

    public void setResultsCount(long total) {
        resultsCountLabel.setText(
                String.format(java.util.Locale.ITALIAN, "%,d anagrafiche", total));
    }

    public void clearFilters() {
        searchField.clear();
        for (ChoiceBox<?> box :
                List.of(
                        operatorFilterChoiceBox,
                        typeFilterChoiceBox,
                        statusFilterChoiceBox,
                        countryFilterChoiceBox)) box.getSelectionModel().selectFirst();
    }

    public ClientiViewState applySearchState(ClientiViewState state) {
        searchField.setText(state.searchText());
        operatorFilterChoiceBox.getItems().stream()
                .filter(f -> Objects.equals(f.id(), state.operatore().id()))
                .findFirst()
                .ifPresentOrElse(
                        operatorFilterChoiceBox::setValue,
                        () -> operatorFilterChoiceBox.getSelectionModel().selectFirst());
        select(typeFilterChoiceBox, state.tipologia(), false);
        select(statusFilterChoiceBox, state.stato(), false);
        select(countryFilterChoiceBox, state.paese(), true);
        setSort(state.sortColumn(), state.ascending());
        return new ClientiViewState(
                searchField.getText(),
                operatorFilterChoiceBox.getValue(),
                typeFilterChoiceBox.getValue(),
                statusFilterChoiceBox.getValue(),
                countryFilterChoiceBox.getValue(),
                state.sortColumn(),
                state.ascending());
    }

    private void select(ChoiceBox<TextFilter> box, TextFilter value, boolean retainMissing) {
        var match =
                box.getItems().stream()
                        .filter(f -> Objects.equals(f.value(), value.value()))
                        .findFirst();
        if (match.isPresent()) box.setValue(match.get());
        else if (retainMissing && !value.isEmptyOption()) {
            var missing =
                    new TextFilter(
                            value.value(),
                            Countries.filterLabel(value.value()) + " (non presente)");
            box.getItems().add(missing);
            box.setValue(missing);
        } else box.getSelectionModel().selectFirst();
    }

    public void addClientRow(ClientePreview preview, Runnable open) {
        resultsTable.addClientRow(preview, open);
    }

    public void closeClientDetails() {
        resultsTable.closeClientDetails();
    }

    public void setSort(SortColumn column, boolean ascending) {
        resultsTable.setSort(column, ascending);
    }

    private void setTextFilters(ChoiceBox<TextFilter> box, List<TextFilter> values) {
        var previous = box.getValue();
        var empty = box.getItems().getFirst();
        box.getItems().setAll(empty);
        box.getItems().addAll(values);
        if (previous != null) select(box, previous, box == countryFilterChoiceBox);
        else box.getSelectionModel().selectFirst();
    }

    public void setTypeFilters(List<TextFilter> types) {
        setTextFilters(typeFilterChoiceBox, types);
    }

    public void setStatusFilters(List<TextFilter> statuses) {
        setTextFilters(statusFilterChoiceBox, statuses);
    }

    public void setPaeseFilters(List<TextFilter> countries) {
        setTextFilters(countryFilterChoiceBox, countries);
    }

    public void setOperatorFilters(List<OperatoreFilter> values) {
        var previous = operatorFilterChoiceBox.getValue();
        operatorFilterChoiceBox.getItems().setAll(OperatoreFilter.empty());
        operatorFilterChoiceBox.getItems().addAll(values);
        operatorFilterChoiceBox.getItems().stream()
                .filter(f -> previous != null && Objects.equals(f.id(), previous.id()))
                .findFirst()
                .ifPresentOrElse(
                        operatorFilterChoiceBox::setValue,
                        () -> operatorFilterChoiceBox.getSelectionModel().selectFirst());
    }

    public void onNewClient(Runnable action) {
        newClientButton.setId("crm-create-lead");
        newClientButton.setOnAction(e -> action.run());
    }

    public AppSidebar getSidebar() {
        return sidebar;
    }

    public void onSortRequested(Consumer<SortColumn> action) {
        resultsTable.onSortRequested(action);
    }

    public void onSearchChanged(Consumer<String> action) {
        searchField.textProperty().addListener((o, a, b) -> action.accept(b));
    }

    public void onOperatoreFilterChanged(Consumer<OperatoreFilter> action) {
        operatorFilterChoiceBox.valueProperty().addListener((o, a, b) -> action.accept(b));
    }

    public void onTipologiaFilterChanged(Consumer<TextFilter> action) {
        typeFilterChoiceBox.valueProperty().addListener((o, a, b) -> action.accept(b));
    }

    public void onStatoFilterChanged(Consumer<TextFilter> action) {
        statusFilterChoiceBox.valueProperty().addListener((o, a, b) -> action.accept(b));
    }

    public void onPaeseFilterChanged(Consumer<TextFilter> action) {
        countryFilterChoiceBox.valueProperty().addListener((o, a, b) -> action.accept(b));
    }

    public void onClearFilters(Runnable action) {
        clearFiltersButton.setOnAction(e -> action.run());
    }

    public void onSaveSearch(Runnable action) {
        saveSearchButton.setOnAction(e -> action.run());
    }

    public void onScrollNearBottom(Runnable action) {
        resultsTable.onScrollNearBottom(action);
    }

    public void onApplySavedSearch(Consumer<VistaSalvata> action) {
        applySavedSearchHandler = action;
    }

    public void onUpdateSavedSearch(Consumer<VistaSalvata> action) {
        updateSavedSearchItem.setOnAction(e -> action.accept(selectedSavedView));
    }

    public void onRenameSavedSearch(Consumer<VistaSalvata> action) {
        renameSavedSearchItem.setOnAction(e -> action.accept(selectedSavedView));
    }

    public void onSetDefaultSavedSearch(Consumer<VistaSalvata> action) {
        setDefaultSavedSearchItem.setOnAction(e -> action.accept(selectedSavedView));
    }

    public void onDeleteSavedSearch(Consumer<VistaSalvata> action) {
        deleteSavedSearchItem.setOnAction(e -> action.accept(selectedSavedView));
    }

    public void setSavedSearches(List<VistaSalvata> values) {
        UUID selected = selectedSavedView == null ? null : selectedSavedView.id();
        savedSearchMenu.getItems().clear();
        savedSearchGroup.getToggles().clear();
        for (var saved : values) {
            RadioMenuItem item =
                    new RadioMenuItem(saved.nome() + (saved.predefinita() ? " ★" : ""));
            item.setUserData(saved);
            item.setToggleGroup(savedSearchGroup);
            item.setOnAction(
                    e -> {
                        selectSavedSearch(saved);
                        applySavedSearchHandler.accept(saved);
                    });
            savedSearchMenu.getItems().add(item);
        }
        savedSearchMenu.setDisable(values.isEmpty());
        selectSavedSearch(
                values.stream().filter(v -> v.id().equals(selected)).findFirst().orElse(null));
    }

    public void selectSavedSearch(VistaSalvata saved) {
        selectedSavedView = saved;
        savedSearchMenu.setText(saved == null ? "Ricerche salvate" : saved.nome());
        for (var item : savedSearchMenu.getItems())
            ((RadioMenuItem) item)
                    .setSelected(
                            saved != null
                                    && ((VistaSalvata) item.getUserData()).id().equals(saved.id()));
        manageSavedSearchButton.setVisible(saved != null);
        manageSavedSearchButton.setManaged(saved != null);
    }

    public void setUnsavedChangesVisible(boolean visible) {
        unsavedChangesLabel.setVisible(visible);
        unsavedChangesLabel.setManaged(visible);
    }

    public void setSaveSearchDisabled(boolean disabled) {
        saveSearchButton.setDisable(disabled);
    }
}

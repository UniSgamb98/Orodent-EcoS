package com.orodent.ecos.crm.feature.clienti.clienti.view;

import com.orodent.ecos.crm.core.database.model.VistaSalvata;
import com.orodent.ecos.crm.core.ui.AppSidebar;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientePreview;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientiViewState;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.OperatoreFilter;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.SortColumn;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.TextFilter;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ClientiView extends BorderPane {

    private static final double NAME_COLUMN_WIDTH = 190;
    private static final double TYPE_COLUMN_WIDTH = 105;
    private static final double CONTACT_COLUMN_WIDTH = 120;
    private static final double OPERATOR_COLUMN_WIDTH = 115;
    private static final double STATUS_COLUMN_WIDTH = 105;
    private static final double LAST_CONTACT_COLUMN_WIDTH = 95;
    private static final double ACTIONS_COLUMN_WIDTH = 52;
    private static final double MIN_SAVED_SEARCHES_WIDTH = 250;

    private final AppSidebar sidebar;
    private final TextField searchField;
    private final Button newClientButton;
    private final ChoiceBox<OperatoreFilter> operatorFilterChoiceBox;
    private final ChoiceBox<TextFilter> typeFilterChoiceBox;
    private final ChoiceBox<TextFilter> statusFilterChoiceBox;
    private final Button otherFiltersButton;
    private final Button clearFiltersButton;
    private final Button saveSearchButton;
    private final HBox savedSearchButtons;
    private final ScrollPane savedSearchesScrollPane;
    private final ToggleGroup savedSearchToggleGroup;
    private final MenuButton manageSavedSearchButton;
    private final MenuItem updateSavedSearchItem;
    private final MenuItem renameSavedSearchItem;
    private final MenuItem setDefaultSavedSearchItem;
    private final MenuItem deleteSavedSearchItem;
    private final Label unsavedChangesLabel;
    private final Label resultsCountLabel;
    private final Button nameHeaderButton;
    private final Button typeHeaderButton;
    private final Button contactHeaderButton;
    private final Button operatorHeaderButton;
    private final Button statusHeaderButton;
    private final Button lastContactHeaderButton;
    private final VBox table;
    private final HBox resultsArea;
    private final VBox tableRows;
    private final HBox emptyRow;
    private final ScrollPane tableScrollPane;
    private final ClientePreviewDetailPanel detailPanel;
    private final ClientiResultsTable resultsTable;
    private HBox filterActionsBar;
    private HBox filterInfo;
    private HBox filterCommands;
    private HBox selectedClientRow;
    private VistaSalvata selectedSavedView;
    private Consumer<VistaSalvata> applySavedSearchHandler = savedView -> {};

    public ClientiView() {
        sidebar = new AppSidebar();

        searchField = new TextField();
        searchField.setPromptText("Cerca clienti...");
        searchField.getStyleClass().add("clients-search-field");

        newClientButton = new Button("+ Nuova anagrafica");
        newClientButton.getStyleClass().add("clients-primary-button");

        operatorFilterChoiceBox = new ChoiceBox<>();
        operatorFilterChoiceBox.getStyleClass().add("clients-operator-filter-choice");
        operatorFilterChoiceBox.getItems().add(OperatoreFilter.empty());
        operatorFilterChoiceBox.getSelectionModel().selectFirst();
        typeFilterChoiceBox = createTextFilterChoiceBox("Tutti");
        statusFilterChoiceBox = createTextFilterChoiceBox("Tutti");
        otherFiltersButton = new Button("Altri filtri");
        otherFiltersButton.getStyleClass().add("clients-other-filters-button");
        clearFiltersButton = new Button("Pulisci filtri");
        clearFiltersButton.getStyleClass().add("clients-clear-filters-button");
        saveSearchButton = new Button("Salva nuova ricerca");
        saveSearchButton.getStyleClass().add("clients-save-search-button");
        savedSearchButtons = new HBox(8);
        savedSearchButtons.getStyleClass().add("clients-saved-search-buttons");
        savedSearchToggleGroup = new ToggleGroup();
        savedSearchesScrollPane = new ScrollPane(savedSearchButtons);
        savedSearchesScrollPane.getStyleClass().add("clients-saved-searches-scroll");
        savedSearchesScrollPane.setFitToHeight(true);
        savedSearchesScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        savedSearchesScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        savedSearchesScrollPane.setMinWidth(MIN_SAVED_SEARCHES_WIDTH);
        savedSearchesScrollPane.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(savedSearchesScrollPane, javafx.scene.layout.Priority.ALWAYS);
        updateSavedSearchItem = new MenuItem("Aggiorna con i filtri correnti");
        renameSavedSearchItem = new MenuItem("Rinomina");
        setDefaultSavedSearchItem = new MenuItem("Imposta come predefinita");
        deleteSavedSearchItem = new MenuItem("Elimina");
        manageSavedSearchButton =
                new MenuButton(
                        "Gestisci",
                        null,
                        updateSavedSearchItem,
                        renameSavedSearchItem,
                        setDefaultSavedSearchItem,
                        deleteSavedSearchItem);
        manageSavedSearchButton.getStyleClass().add("clients-manage-search-button");
        manageSavedSearchButton.setDisable(true);
        unsavedChangesLabel = new Label("Modifiche non salvate");
        unsavedChangesLabel.getStyleClass().add("clients-unsaved-search-label");
        unsavedChangesLabel.setManaged(false);
        unsavedChangesLabel.setVisible(false);
        resultsCountLabel = new Label("0 risultati trovati");
        resultsCountLabel.getStyleClass().add("clients-results-count");

        nameHeaderButton = createHeaderButton("Ragione sociale", NAME_COLUMN_WIDTH);
        typeHeaderButton = createHeaderButton("Tipo", TYPE_COLUMN_WIDTH);
        contactHeaderButton = createHeaderButton("Referente", CONTACT_COLUMN_WIDTH);
        operatorHeaderButton = createHeaderButton("Operatore", OPERATOR_COLUMN_WIDTH);
        statusHeaderButton = createHeaderButton("Stato", STATUS_COLUMN_WIDTH);
        lastContactHeaderButton = createHeaderButton("Ultimo contatto", LAST_CONTACT_COLUMN_WIDTH);

        table = new VBox();
        table.getStyleClass().add("clients-table");
        tableRows = new VBox();
        emptyRow = createEmptyRow();
        tableScrollPane = new ScrollPane(tableRows);
        tableScrollPane.setFitToWidth(true);
        tableScrollPane.getStyleClass().add("clients-table-scroll");
        detailPanel = new ClientePreviewDetailPanel(this::closeClientDetails);
        detailPanel.setManaged(false);
        detailPanel.setVisible(false);
        resultsArea = new HBox(16, table, detailPanel);
        resultsArea.getStyleClass().add("clients-results-area");
        HBox.setHgrow(table, javafx.scene.layout.Priority.ALWAYS);

        resultsTable = new ClientiResultsTable();

        setLeft(sidebar);
        setCenter(createContent());
    }

    private VBox createContent() {
        VBox content = new VBox(18);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("clients-content");

        HBox titleBar = new HBox(12);
        titleBar.getStyleClass().add("clients-title-bar");

        VBox titleBox = new VBox(4);
        Label title = new Label("Anagrafiche CRM");
        title.getStyleClass().add("clients-title");
        Label subtitle =
                new Label(
                        "Gestisci anagrafiche, riferimenti e informazioni commerciali dei"
                                + " clienti.");
        subtitle.getStyleClass().add("clients-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        HBox titleSpacer = new HBox();
        HBox.setHgrow(titleSpacer, javafx.scene.layout.Priority.ALWAYS);
        titleBar.getChildren().addAll(titleBox, titleSpacer, newClientButton);

        HBox filters = new HBox(12);
        filters.getStyleClass().add("clients-filter-bar");
        VBox searchFilter = createFilterControl("Ricerca", searchField);
        searchFilter.getStyleClass().add("clients-search-filter-control");
        searchField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(searchFilter, javafx.scene.layout.Priority.ALWAYS);
        filters.getChildren()
                .addAll(
                        searchFilter,
                        createFilterControl("Stato trattativa", statusFilterChoiceBox),
                        createFilterControl("Operatore", operatorFilterChoiceBox),
                        createFilterControl("Tipologia", typeFilterChoiceBox),
                        otherFiltersButton);

        initializeTable();
        VBox.setVgrow(resultsTable, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(resultsArea, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(tableScrollPane, javafx.scene.layout.Priority.ALWAYS);

        content.getChildren().addAll(titleBar, filters, createFilterActionsBar(), resultsTable);
        return content;
    }

    private VBox createFilterControl(String labelText, Region control) {
        VBox filterControl = new VBox(4);
        filterControl.getStyleClass().add("clients-filter-control");
        filterControl.getChildren().addAll(createFilterLabel(labelText), control);
        return filterControl;
    }

    private HBox createFilterActionsBar() {
        filterInfo = new HBox(12, resultsCountLabel, unsavedChangesLabel);
        filterInfo.getStyleClass().add("clients-filter-info");
        filterInfo.setMinWidth(Region.USE_PREF_SIZE);

        filterCommands =
                new HBox(12, manageSavedSearchButton, clearFiltersButton, saveSearchButton);
        filterCommands.getStyleClass().add("clients-filter-commands");
        filterCommands.setMinWidth(Region.USE_PREF_SIZE);

        filterActionsBar = new HBox(12, filterInfo, savedSearchesScrollPane, filterCommands);
        filterActionsBar.getStyleClass().add("clients-filter-actions");
        filterActionsBar
                .widthProperty()
                .addListener((observable, oldValue, newValue) -> updateFilterActionsSizing());
        return filterActionsBar;
    }

    private void updateFilterActionsSizing() {
        if (filterActionsBar == null || filterActionsBar.getWidth() <= 0) {
            return;
        }
        double requiredWidth =
                filterInfo.prefWidth(-1)
                        + MIN_SAVED_SEARCHES_WIDTH
                        + filterCommands.prefWidth(-1)
                        + filterActionsBar.getSpacing() * 2;
        boolean compactInfo = filterActionsBar.getWidth() < requiredWidth;
        double informationMinWidth = compactInfo ? 0 : Region.USE_PREF_SIZE;
        filterInfo.setMinWidth(informationMinWidth);
        resultsCountLabel.setMinWidth(informationMinWidth);
        unsavedChangesLabel.setMinWidth(informationMinWidth);
    }

    private void initializeTable() {
        HBox headerRow = createHeaderRow();
        tableRows.getChildren().add(emptyRow);
        table.getChildren().addAll(headerRow, tableScrollPane);
    }

    private HBox createHeaderRow() {
        HBox row = new HBox();
        row.getStyleClass().add("clients-table-row");
        row.getStyleClass().add("clients-table-header");
        row.getChildren()
                .addAll(
                        nameHeaderButton,
                        typeHeaderButton,
                        contactHeaderButton,
                        operatorHeaderButton,
                        statusHeaderButton,
                        lastContactHeaderButton,
                        createActionsHeader());
        return row;
    }

    private Label createActionsHeader() {
        Label header = new Label("Azioni");
        header.getStyleClass().addAll("clients-table-cell", "clients-actions-header");
        setColumnWidth(header, ACTIONS_COLUMN_WIDTH);
        return header;
    }

    private Label createFilterLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("clients-filter-label");
        return label;
    }

    private ChoiceBox<TextFilter> createTextFilterChoiceBox(String emptyLabel) {
        ChoiceBox<TextFilter> choiceBox = new ChoiceBox<>();
        choiceBox.getStyleClass().add("clients-operator-filter-choice");
        choiceBox.getItems().add(TextFilter.empty(emptyLabel));
        choiceBox.getSelectionModel().selectFirst();
        return choiceBox;
    }

    private HBox createEmptyRow() {
        return createMessageRow("Nessun cliente caricato. Usa \"+ Nuovo cliente\" per iniziare.");
    }

    private HBox createMessageRow(String message) {
        HBox row = new HBox();
        row.getStyleClass().add("clients-empty-row");
        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("clients-empty-label");
        messageLabel.setWrapText(true);
        row.getChildren().add(messageLabel);
        return row;
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

    private void showMessage(String message) {
        closeClientDetails();
        tableRows.getChildren().clear();
        tableRows.getChildren().add(createMessageRow(message));
        tableScrollPane.setVvalue(0);
    }

    public void clearClientRows() {
        resultsTable.clearRows();
    }

    public void setResultsCount(long totalResults) {
        resultsCountLabel.setText(
                totalResults + (totalResults == 1 ? " risultato trovato" : " risultati trovati"));
        Platform.runLater(this::updateFilterActionsSizing);
    }

    public void clearFilters() {
        searchField.clear();
        operatorFilterChoiceBox.getSelectionModel().selectFirst();
        typeFilterChoiceBox.getSelectionModel().selectFirst();
        statusFilterChoiceBox.getSelectionModel().selectFirst();
    }

    public ClientiViewState applySearchState(ClientiViewState state) {
        searchField.setText(state.searchText());
        selectOperator(state.operatore());
        selectTextFilter(typeFilterChoiceBox, state.tipologia());
        selectTextFilter(statusFilterChoiceBox, state.stato());
        return new ClientiViewState(
                searchField.getText(),
                operatorFilterChoiceBox.getValue(),
                typeFilterChoiceBox.getValue(),
                statusFilterChoiceBox.getValue(),
                state.sortColumn(),
                state.ascending());
    }

    private void selectOperator(OperatoreFilter filter) {
        operatorFilterChoiceBox.getItems().stream()
                .filter(candidate -> Objects.equals(candidate.id(), filter.id()))
                .findFirst()
                .ifPresentOrElse(
                        operatorFilterChoiceBox.getSelectionModel()::select,
                        () -> operatorFilterChoiceBox.getSelectionModel().selectFirst());
    }

    private void selectTextFilter(ChoiceBox<TextFilter> choiceBox, TextFilter filter) {
        choiceBox.getItems().stream()
                .filter(candidate -> Objects.equals(candidate.value(), filter.value()))
                .findFirst()
                .ifPresentOrElse(
                        choiceBox.getSelectionModel()::select,
                        () -> choiceBox.getSelectionModel().selectFirst());
    }

    public HBox addClientRow(
            String name,
            String type,
            String contact,
            String operator,
            String status,
            String lastContact,
            Runnable onActionsClick) {
        return resultsTable.addClientRow(
                name, type, contact, operator, status, lastContact, onActionsClick);
    }

    public void openClientDetails(ClientePreview preview, HBox row, Runnable onOpenProfile) {
        resultsTable.openClientDetails(preview, row, onOpenProfile);
    }

    public void closeClientDetails() {
        resultsTable.closeClientDetails();
    }

    private HBox createTableRow(
            String name,
            String type,
            String contact,
            String operator,
            String status,
            String lastContact,
            Runnable onActionsClick) {
        HBox row = new HBox();
        row.getStyleClass().add("clients-table-row");
        row.getChildren()
                .addAll(
                        createCell(name, NAME_COLUMN_WIDTH),
                        createCell(type, TYPE_COLUMN_WIDTH),
                        createCell(contact, CONTACT_COLUMN_WIDTH),
                        createCell(operator, OPERATOR_COLUMN_WIDTH),
                        createCell(status, STATUS_COLUMN_WIDTH),
                        createCell(lastContact, LAST_CONTACT_COLUMN_WIDTH),
                        createActionsButton(onActionsClick));
        return row;
    }

    private Label createCell(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("clients-table-cell");
        setColumnWidth(label, width);
        return label;
    }

    private Button createActionsButton(Runnable onActionsClick) {
        Button button = new Button("...");
        button.getStyleClass().add("clients-row-actions-button");
        setColumnWidth(button, ACTIONS_COLUMN_WIDTH);
        button.setOnAction(
                event -> {
                    event.consume();
                    onActionsClick.run();
                });
        return button;
    }

    private Button createFilterButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("clients-filter-button");
        return button;
    }

    private Button createHeaderButton(String text, double width) {
        Button button = new Button(text);
        button.getStyleClass().add("clients-table-header-button");
        setColumnWidth(button, width);
        return button;
    }

    private void setColumnWidth(Region region, double width) {
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
    }

    public void setTypeFilters(List<TextFilter> types) {
        setTextFilters(typeFilterChoiceBox, TextFilter.empty("Tutti"), types);
    }

    public void setStatusFilters(List<TextFilter> statuses) {
        setTextFilters(statusFilterChoiceBox, TextFilter.empty("Tutti"), statuses);
    }

    private void setTextFilters(
            ChoiceBox<TextFilter> choiceBox, TextFilter emptyFilter, List<TextFilter> filters) {
        TextFilter selected = choiceBox.getSelectionModel().getSelectedItem();
        choiceBox.getItems().setAll(emptyFilter);
        if (filters != null) {
            choiceBox.getItems().addAll(filters);
        }

        if (selected != null && choiceBox.getItems().contains(selected)) {
            choiceBox.getSelectionModel().select(selected);
        } else {
            choiceBox.getSelectionModel().selectFirst();
        }
    }

    public void setOperatorFilters(List<OperatoreFilter> operators) {
        OperatoreFilter selected = operatorFilterChoiceBox.getSelectionModel().getSelectedItem();
        operatorFilterChoiceBox.getItems().setAll(OperatoreFilter.empty());
        if (operators != null) {
            operatorFilterChoiceBox.getItems().addAll(operators);
        }

        if (selected != null && operatorFilterChoiceBox.getItems().contains(selected)) {
            operatorFilterChoiceBox.getSelectionModel().select(selected);
        } else {
            operatorFilterChoiceBox.getSelectionModel().selectFirst();
        }
    }

    public void onNewClient(Runnable action) {
        newClientButton.setId("crm-create-lead");
        newClientButton.setOnAction(event -> action.run());
    }

    public AppSidebar getSidebar() {
        return sidebar;
    }

    public void onSortRequested(Consumer<SortColumn> action) {
        resultsTable.onSortRequested(action);
    }

    public void onSearchChanged(Consumer<String> action) {
        searchField
                .textProperty()
                .addListener((observable, oldValue, newValue) -> action.accept(newValue));
    }

    public void onOperatoreFilterChanged(Consumer<OperatoreFilter> action) {
        operatorFilterChoiceBox
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> action.accept(newValue));
    }

    public void onTipologiaFilterChanged(Consumer<TextFilter> action) {
        typeFilterChoiceBox
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> action.accept(newValue));
    }

    public void onStatoFilterChanged(Consumer<TextFilter> action) {
        statusFilterChoiceBox
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> action.accept(newValue));
    }

    public void onClearFilters(Runnable action) {
        clearFiltersButton.setOnAction(event -> action.run());
    }

    public void onSaveSearch(Runnable action) {
        saveSearchButton.setOnAction(event -> action.run());
    }

    public void setSavedSearches(List<VistaSalvata> savedSearches) {
        java.util.UUID selectedId = selectedSavedView == null ? null : selectedSavedView.id();
        savedSearchButtons.getChildren().clear();
        savedSearchToggleGroup.getToggles().clear();
        selectedSavedView = null;
        for (VistaSalvata savedView :
                savedSearches == null ? List.<VistaSalvata>of() : savedSearches) {
            ToggleButton button = createSavedSearchButton(savedView);
            savedSearchButtons.getChildren().add(button);
            if (savedView.id().equals(selectedId)) {
                selectSavedSearchButton(button, savedView, false);
            }
        }
        manageSavedSearchButton.setDisable(selectedSavedView == null);
    }

    public void onApplySavedSearch(Consumer<VistaSalvata> action) {
        applySavedSearchHandler = action == null ? savedView -> {} : action;
    }

    public void onUpdateSavedSearch(Consumer<VistaSalvata> action) {
        updateSavedSearchItem.setOnAction(event -> action.accept(selectedSavedView));
    }

    public void onRenameSavedSearch(Consumer<VistaSalvata> action) {
        renameSavedSearchItem.setOnAction(event -> action.accept(selectedSavedView));
    }

    public void onSetDefaultSavedSearch(Consumer<VistaSalvata> action) {
        setDefaultSavedSearchItem.setOnAction(event -> action.accept(selectedSavedView));
    }

    public void onDeleteSavedSearch(Consumer<VistaSalvata> action) {
        deleteSavedSearchItem.setOnAction(event -> action.accept(selectedSavedView));
    }

    public void selectSavedSearch(VistaSalvata savedView) {
        if (savedView == null) {
            savedSearchToggleGroup.selectToggle(null);
            selectedSavedView = null;
            manageSavedSearchButton.setDisable(true);
            return;
        }
        savedSearchButtons.getChildren().stream()
                .filter(ToggleButton.class::isInstance)
                .map(ToggleButton.class::cast)
                .filter(button -> savedView.id().equals(((VistaSalvata) button.getUserData()).id()))
                .findFirst()
                .ifPresentOrElse(
                        button -> selectSavedSearchButton(button, savedView, false),
                        () -> {
                            ToggleButton button = createSavedSearchButton(savedView);
                            savedSearchButtons.getChildren().add(button);
                            selectSavedSearchButton(button, savedView, false);
                        });
    }

    public void setUnsavedChangesVisible(boolean visible) {
        unsavedChangesLabel.setManaged(visible);
        unsavedChangesLabel.setVisible(visible);
        Platform.runLater(this::updateFilterActionsSizing);
    }

    private ToggleButton createSavedSearchButton(VistaSalvata savedView) {
        String label = savedView.nome() + (savedView.predefinita() ? " ★" : "");
        ToggleButton button = new ToggleButton(label);
        button.setUserData(savedView);
        button.setToggleGroup(savedSearchToggleGroup);
        button.getStyleClass().add("clients-saved-search-button");
        button.setOnAction(event -> selectSavedSearchButton(button, savedView, true));
        return button;
    }

    private void selectSavedSearchButton(
            ToggleButton button, VistaSalvata savedView, boolean apply) {
        savedSearchToggleGroup.selectToggle(button);
        selectedSavedView = savedView;
        manageSavedSearchButton.setDisable(false);
        if (apply) {
            applySavedSearchHandler.accept(savedView);
        }
    }

    public void setSaveSearchDisabled(boolean disabled) {
        saveSearchButton.setDisable(disabled);
    }

    public void onScrollNearBottom(Runnable action) {
        resultsTable.onScrollNearBottom(action);
    }
}

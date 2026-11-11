package com.orodent.ecos.crm.feature.clienti.clienti.view;

import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientePreview;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.SortColumn;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class ClientiResultsTable extends HBox {

    private static final double NAME_COLUMN_WIDTH = 190;
    private static final double TYPE_COLUMN_WIDTH = 105;
    private static final double CONTACT_COLUMN_WIDTH = 120;
    private static final double OPERATOR_COLUMN_WIDTH = 115;
    private static final double STATUS_COLUMN_WIDTH = 105;
    private static final double LAST_CONTACT_COLUMN_WIDTH = 95;
    private static final double ACTIONS_COLUMN_WIDTH = 52;

    private final VBox tableRows = new VBox();
    private final HBox emptyRow =
            createMessageRow("Nessun cliente caricato. Usa \"+ Nuovo cliente\" per iniziare.");
    private final ScrollPane tableScrollPane = new ScrollPane(tableRows);
    private final Label loadStateLabel = new Label();
    private final ClientePreviewDetailPanel detailPanel =
            new ClientePreviewDetailPanel(this::closeClientDetails);
    private HBox selectedClientRow;
    private Consumer<SortColumn> sortHandler = column -> {};

    public ClientiResultsTable() {
        super(16);
        getStyleClass().add("clients-results-area");

        VBox table = new VBox();
        table.getStyleClass().add("clients-table");
        tableScrollPane.setFitToWidth(true);
        tableScrollPane.getStyleClass().add("clients-table-scroll");
        tableRows.getChildren().add(emptyRow);
        table.getChildren().addAll(createHeaderRow(), tableScrollPane);
        VBox.setVgrow(tableScrollPane, Priority.ALWAYS);
        HBox.setHgrow(table, Priority.ALWAYS);

        detailPanel.setPadding(new Insets(18));
        detailPanel.setManaged(false);
        detailPanel.setVisible(false);
        getChildren().addAll(table, detailPanel);
    }

    public void onSortRequested(Consumer<SortColumn> action) {
        sortHandler = action == null ? column -> {} : action;
    }

    public void showLoading() {
        showMessage("Caricamento clienti...");
    }

    public void showEmpty() {
        showMessage("Nessun cliente trovato.");
    }

    public void showError(String message) {
        showMessage(
                message == null || message.isBlank()
                        ? "Caricamento clienti non riuscito."
                        : message);
    }

    public void showLoadingMore() {
        setLoadState("Caricamento altri clienti...");
    }

    public void showLoadMoreAvailable() {
        clearLoadState();
    }

    public void showAllResultsLoaded() {
        setLoadState("Tutti i clienti sono stati caricati.");
    }

    public void onScrollNearBottom(Runnable action) {
        tableScrollPane
                .vvalueProperty()
                .addListener(
                        (observable, oldValue, newValue) -> {
                            if (newValue.doubleValue() >= 0.88) {
                                action.run();
                            }
                        });
    }

    public void clearRows() {
        closeClientDetails();
        clearLoadState();
        tableRows.getChildren().setAll(emptyRow);
        tableScrollPane.setVvalue(0);
    }

    public HBox addClientRow(
            String name,
            String type,
            String contact,
            String operator,
            String status,
            String lastContact,
            Runnable onActionsClick) {
        clearLoadState();
        tableRows.getChildren().remove(emptyRow);
        HBox row =
                new HBox(
                        createCell(name, NAME_COLUMN_WIDTH),
                        createCell(type, TYPE_COLUMN_WIDTH),
                        createCell(contact, CONTACT_COLUMN_WIDTH),
                        createCell(operator, OPERATOR_COLUMN_WIDTH),
                        createCell(status, STATUS_COLUMN_WIDTH),
                        createCell(lastContact, LAST_CONTACT_COLUMN_WIDTH),
                        createActionsButton(onActionsClick));
        row.getStyleClass().addAll("clients-table-row", "clients-data-row");
        tableRows.getChildren().add(row);
        return row;
    }

    public void openClientDetails(ClientePreview preview, HBox row, Runnable onOpenProfile) {
        if (selectedClientRow != null) {
            selectedClientRow.getStyleClass().remove("clients-data-row-selected");
        }
        selectedClientRow = row;
        selectedClientRow.getStyleClass().add("clients-data-row-selected");
        detailPanel.showCliente(preview, onOpenProfile);
        detailPanel.setManaged(true);
        detailPanel.setVisible(true);
    }

    public void closeClientDetails() {
        if (selectedClientRow != null) {
            selectedClientRow.getStyleClass().remove("clients-data-row-selected");
            selectedClientRow = null;
        }
        detailPanel.setManaged(false);
        detailPanel.setVisible(false);
    }

    private HBox createHeaderRow() {
        HBox row = new HBox();
        row.getStyleClass().addAll("clients-table-row", "clients-table-header");
        row.getChildren()
                .addAll(
                        createHeaderButton("Ragione sociale", NAME_COLUMN_WIDTH, SortColumn.NAME),
                        createHeaderButton("Tipo", TYPE_COLUMN_WIDTH, SortColumn.TYPE),
                        createHeaderButton("Referente", CONTACT_COLUMN_WIDTH, SortColumn.CONTACT),
                        createHeaderButton("Operatore", OPERATOR_COLUMN_WIDTH, SortColumn.OPERATOR),
                        createHeaderButton("Stato", STATUS_COLUMN_WIDTH, SortColumn.STATUS),
                        createHeaderButton(
                                "Ultimo contatto",
                                LAST_CONTACT_COLUMN_WIDTH,
                                SortColumn.LAST_CONTACT),
                        createActionsHeader());
        return row;
    }

    private Button createHeaderButton(String text, double width, SortColumn column) {
        Button button = new Button(text);
        button.getStyleClass().add("clients-table-header-button");
        setColumnWidth(button, width);
        button.setOnAction(event -> sortHandler.accept(column));
        return button;
    }

    private Label createActionsHeader() {
        Label header = new Label("Azioni");
        header.getStyleClass().addAll("clients-table-cell", "clients-actions-header");
        setColumnWidth(header, ACTIONS_COLUMN_WIDTH);
        return header;
    }

    private void showMessage(String message) {
        closeClientDetails();
        clearLoadState();
        tableRows.getChildren().setAll(createMessageRow(message));
        tableScrollPane.setVvalue(0);
    }

    private HBox createMessageRow(String message) {
        HBox row = new HBox();
        row.getStyleClass().add("clients-empty-row");
        Label label = new Label(message);
        label.getStyleClass().add("clients-empty-label");
        label.setWrapText(true);
        row.getChildren().add(label);
        return row;
    }

    private void setLoadState(String message) {
        loadStateLabel.setText(message);
        loadStateLabel.getStyleClass().setAll("clients-load-state");
        if (!tableRows.getChildren().contains(loadStateLabel)) {
            tableRows.getChildren().add(loadStateLabel);
        }
    }

    private void clearLoadState() {
        tableRows.getChildren().remove(loadStateLabel);
    }

    private Label createCell(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("clients-table-cell");
        setColumnWidth(label, width);
        return label;
    }

    private Button createActionsButton(Runnable action) {
        Button button = new Button("...");
        button.getStyleClass().add("clients-row-actions-button");
        setColumnWidth(button, ACTIONS_COLUMN_WIDTH);
        button.setOnAction(
                event -> {
                    event.consume();
                    action.run();
                });
        return button;
    }

    private void setColumnWidth(Region region, double width) {
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
    }
}

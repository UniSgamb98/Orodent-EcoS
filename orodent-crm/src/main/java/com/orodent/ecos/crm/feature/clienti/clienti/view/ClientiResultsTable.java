package com.orodent.ecos.crm.feature.clienti.clienti.view;

import com.orodent.ecos.crm.feature.clienti.clienti.dto.*;

import javafx.beans.binding.Bindings;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;

import java.util.*;
import java.util.function.Consumer;

/** A growing list with a fixed header and a responsive, optional preview. */
public class ClientiResultsTable extends StackPane {
    private final VBox tableRows = new VBox();
    private final ScrollPane tableScrollPane = new ScrollPane(tableRows);
    private final Label loadStateLabel = new Label();
    private final VBox table = new VBox();
    private final BorderPane base = new BorderPane(table);
    private final ClientePreviewDetailPanel detailPanel =
            new ClientePreviewDetailPanel(this::closeClientDetails);
    private final StackPane previewScroll = new StackPane(detailPanel);
    private final StackPane overlay = new StackPane();
    private final Map<SortColumn, Button> headers = new EnumMap<>(SortColumn.class);
    private final Map<SortColumn, String> headerTitles = new EnumMap<>(SortColumn.class);
    private HBox selectedClientRow;
    private Consumer<SortColumn> sortHandler = ignored -> {};

    public ClientiResultsTable() {
        setMinWidth(0);
        setMinHeight(0);
        getStyleClass().add("clients-results-area");
        table.setMinWidth(0);
        table.getStyleClass().add("clients-table");
        tableScrollPane.setId("crm-clients-table-scroll");
        tableScrollPane.setFitToWidth(true);
        tableScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tableScrollPane.getStyleClass().add("clients-table-scroll");
        tableRows.setMinWidth(0);
        table.getChildren().addAll(createHeaderRow(), tableScrollPane);
        VBox.setVgrow(tableScrollPane, Priority.ALWAYS);
        previewScroll.setId("crm-client-preview");
        previewScroll.setMinWidth(0);
        previewScroll.setPrefWidth(320);
        previewScroll.getStyleClass().add("clients-preview-scroll");
        previewScroll.setOnMouseClicked(e -> e.consume());
        overlay.getStyleClass().add("clients-preview-overlay");
        overlay.setOnMouseClicked(
                e -> {
                    if (e.getTarget() == overlay) closeClientDetails();
                });
        getChildren().add(base);
        widthProperty().addListener((o, a, b) -> layoutPreview());
        showMessage("Caricamento anagrafiche…");
        setSort(SortColumn.NAME, true);
    }

    public void onSortRequested(Consumer<SortColumn> action) {
        sortHandler = action;
    }

    public void setSort(SortColumn column, boolean ascending) {
        headers.forEach(
                (key, button) ->
                        button.setText(
                                headerTitles.get(key)
                                        + (key == column ? ascending ? " ↑" : " ↓" : "")));
    }

    public void showLoading() {
        showMessage("Caricamento anagrafiche…");
    }

    public void showEmpty() {
        showMessage("Nessuna anagrafica trovata. Prova a modificare i filtri.");
    }

    public void showError(String text) {
        showMessage(text == null ? "Caricamento non riuscito." : text);
    }

    public void showLoadingMore() {
        setLoadState("Caricamento altre anagrafiche…");
    }

    public void showLoadMoreAvailable() {
        tableRows.getChildren().remove(loadStateLabel);
    }

    public void showAllResultsLoaded() {
        setLoadState("Tutte le anagrafiche sono state caricate.");
    }

    public void onScrollNearBottom(Runnable action) {
        tableScrollPane
                .vvalueProperty()
                .addListener(
                        (o, a, b) -> {
                            if (b.doubleValue() >= 0.88) action.run();
                        });
    }

    public void clearRows() {
        closeClientDetails();
        tableRows.getChildren().clear();
        tableScrollPane.setVvalue(0);
    }

    public void addClientRow(ClientePreview preview, Runnable openProfile) {
        tableRows.getChildren().remove(loadStateLabel);
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.getStyleClass().addAll("clients-table-row", "clients-data-row");
        row.setFocusTraversable(true);
        row.setAccessibleText(preview.name());
        Label name = label(preview.name());
        name.getStyleClass().add("clients-company-name");
        String subtitle =
                String.join(
                        " · ",
                        java.util.stream.Stream.of(preview.type(), preview.country())
                                .filter(v -> v != null && !v.isBlank())
                                .toList());
        Label context = label(subtitle);
        context.getStyleClass().add("clients-company-context");
        VBox company = new VBox(3, name, context);
        company.setMinWidth(0);
        HBox.setHgrow(company, Priority.ALWAYS);
        company.setPrefWidth(200);
        company.setMaxWidth(Double.MAX_VALUE);
        Label contact = label(ClientiPresentation.value(preview.contact()));
        size(contact, 130);
        optionalContact(contact);
        Label operator = label(ClientiPresentation.value(preview.operator()));
        size(operator, 100);
        HBox status = new HBox(ClientiPresentation.badge(preview.status()));
        status.setAlignment(Pos.CENTER_LEFT);
        size(status, 105);
        Label last = label(ClientiPresentation.value(preview.lastContact()));
        size(last, 100);
        Label next = label(ClientiPresentation.value(preview.nextContact()));
        size(next, 110);
        if (ClientiPresentation.overdue(preview.nextContact())) {
            next.getStyleClass().add("clients-recall-overdue");
            next.setTooltip(new Tooltip("Richiamo scaduto · " + preview.nextContact()));
        }
        MenuButton actions = actionsMenu();
        var show = new MenuItem("Mostra anteprima");
        show.setOnAction(e -> openClientDetails(preview, row, openProfile));
        var open = new MenuItem("Apri scheda");
        open.setOnAction(e -> openProfile.run());
        actions.getItems().addAll(show, open);
        actions.setAccessibleText("Azioni per " + preview.name());
        HBox actionCell = new HBox(actions);
        actionCell.setAlignment(Pos.CENTER);
        size(actionCell, 40);
        row.getChildren().addAll(company, contact, operator, status, last, next, actionCell);
        row.setOnMouseClicked(
                e -> {
                    if (e.getButton() != MouseButton.PRIMARY) return;
                    if (e.getClickCount() == 2) openProfile.run();
                    else openClientDetails(preview, row, openProfile);
                });
        row.setOnKeyPressed(
                e -> {
                    if (e.getCode() == KeyCode.ENTER) {
                        e.consume();
                        openProfile.run();
                    }
                });
        tableRows.getChildren().add(row);
    }

    private void optionalContact(Region node) {
        node.visibleProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () -> tableRows.getWidth() >= 850, tableRows.widthProperty()));
        node.managedProperty().bind(node.visibleProperty());
    }

    public void openClientDetails(ClientePreview preview, HBox row, Runnable open) {
        if (selectedClientRow != null)
            selectedClientRow.getStyleClass().remove("clients-data-row-selected");
        selectedClientRow = row;
        row.getStyleClass().add("clients-data-row-selected");
        detailPanel.showCliente(preview, open);
        layoutPreview();
    }

    public void closeClientDetails() {
        if (selectedClientRow != null)
            selectedClientRow.getStyleClass().remove("clients-data-row-selected");
        selectedClientRow = null;
        layoutPreview();
    }

    private void layoutPreview() {
        base.setRight(null);
        overlay.getChildren().clear();
        getChildren().remove(overlay);
        if (selectedClientRow == null) return;
        if (getWidth() >= 1240) {
            previewScroll.setMaxWidth(320);
            BorderPane.setMargin(previewScroll, new Insets(0, 0, 0, 16));
            base.setRight(previewScroll);
        } else {
            previewScroll.setMaxWidth(Math.min(340, Math.max(0, getWidth() - 16)));
            StackPane.setAlignment(previewScroll, Pos.CENTER_RIGHT);
            overlay.getChildren().add(previewScroll);
            getChildren().add(overlay);
        }
    }

    private HBox createHeaderRow() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.getStyleClass().addAll("clients-table-row", "clients-table-header");
        Button name = header("Azienda / Paese", SortColumn.NAME);
        HBox.setHgrow(name, Priority.ALWAYS);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setMinWidth(0);
        name.setPrefWidth(200);
        Button contact = header("Referente", SortColumn.CONTACT);
        size(contact, 130);
        optionalContact(contact);
        Button operator = header("Operatore", SortColumn.OPERATOR);
        size(operator, 100);
        Button status = header("Stato", SortColumn.STATUS);
        size(status, 105);
        Button last = header("Ultimo contatto", SortColumn.LAST_CONTACT);
        size(last, 100);
        Button next = header("Richiamo", SortColumn.NEXT_CONTACT);
        size(next, 110);
        Label action = new Label();
        size(action, 40);
        row.getChildren().addAll(name, contact, operator, status, last, next, action);
        row.maxWidthProperty()
                .bind(
                        Bindings.createDoubleBinding(
                                () -> Math.max(0, tableRows.getWidth()),
                                tableRows.widthProperty()));
        return row;
    }

    private Button header(String title, SortColumn column) {
        Button b = new Button(title);
        b.getStyleClass().add("clients-table-header-button");
        b.setOnAction(e -> sortHandler.accept(column));
        headers.put(column, b);
        headerTitles.put(column, title);
        return b;
    }

    private Label label(String text) {
        Label label = new Label(text);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("clients-table-cell");
        label.setTooltip(new Tooltip(text));
        return label;
    }

    private static void size(Region node, double width) {
        node.setMinWidth(width);
        node.setPrefWidth(width);
        node.setMaxWidth(width);
    }

    private MenuButton actionsMenu() {
        SVGPath dots = new SVGPath();
        dots.setContent(
                "M1.5 0a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3ZM7.5 0a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0"
                        + " 0-3ZM13.5 0a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3Z");
        dots.getStyleClass().add("clients-actions-icon");
        MenuButton button = new MenuButton();
        button.setGraphic(dots);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.getStyleClass().add("clients-row-actions-button");
        button.setTooltip(new Tooltip("Azioni anagrafica"));
        button.setOnMouseClicked(e -> e.consume());
        return button;
    }

    private void showMessage(String text) {
        clearRows();
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("clients-empty-label");
        tableRows.getChildren().add(label);
    }

    private void setLoadState(String text) {
        loadStateLabel.setText(text);
        loadStateLabel.getStyleClass().setAll("clients-load-state");
        if (!tableRows.getChildren().contains(loadStateLabel))
            tableRows.getChildren().add(loadStateLabel);
    }
}

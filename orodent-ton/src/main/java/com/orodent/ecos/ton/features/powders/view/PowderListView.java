package com.orodent.ecos.ton.features.powders.view;

import com.orodent.ecos.ton.features.powders.model.PowderModel.*;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Function;

public final class PowderListView extends VBox {
    private final TextField search = new TextField();
    private final CheckBox inactive = new CheckBox("Mostra anche archiviate");
    private final TableView<Row> table = new TableView<>();
    private final Label feedback = new Label();
    private final Button create = button("Nuova polvere", "ton-powder-new", "primary-button");
    private final Button refresh = button("Aggiorna", "ton-powders-refresh", "secondary-button");

    public PowderListView() {
        super(14);
        setId("ton-powders-list");
        getStyleClass().addAll("ton-powder-page", "ton-catalogue-list");
        setMinWidth(0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, spacer, create);
        header.setAlignment(Pos.CENTER_LEFT);
        Label intro =
                new Label(
                        "Anagrafiche e schede tecniche versionate. Gli ossidi sono espressi in"
                                + " percentuale in peso (wt%).");
        intro.setWrapText(true);
        intro.getStyleClass().add("muted-label");
        intro.setMinWidth(0);
        header.getChildren().addFirst(intro);
        HBox.setHgrow(intro, Priority.ALWAYS);
        search.setId("ton-powders-search");
        search.setPromptText("Cerca codice, nome o fornitore");
        HBox.setHgrow(search, Priority.ALWAYS);
        inactive.setId("ton-powders-inactive");
        table.setId("ton-powders-table");
        table.setMinHeight(230);
        table.setPrefHeight(360);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getColumns()
                .setAll(
                        List.of(
                                column("Codice", r -> r.powder().code(), 110),
                                column("Nome", r -> r.powder().name(), 200),
                                column("Fornitore", r -> r.powder().supplier(), 160),
                                column("Colore", r -> r.powder().colorLabel(), 110),
                                column(
                                        "Classe Y",
                                        r -> r.yttriaClass() == null ? "—" : r.yttriaClass() + "Y",
                                        80),
                                column("Schede tecniche", PowderListView::versions, 200),
                                column(
                                        "Polvere",
                                        r -> r.powder().active() ? "Attiva" : "Archiviata",
                                        90)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns()
                .forEach(
                        column -> {
                            column.setPrefWidth(150);
                            column.setMinWidth(60);
                        });
        table.setPlaceholder(new Label("Nessuna polvere. Usa Nuova polvere per iniziare."));
        feedback.setId("ton-powders-feedback");
        feedback.setWrapText(true);
        feedback.getStyleClass().add("ton-error-feedback");
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        VBox card = new VBox(12, new HBox(10, search, refresh), inactive, table, feedback);
        card.getStyleClass().add("ton-list-card");
        card.setMinSize(0, 0);
        VBox.setVgrow(card, Priority.ALWAYS);
        getChildren().setAll(header, card);
    }

    public void showLoading() {
        table.setDisable(true);
        refresh.setDisable(true);
        feedback.setText("");
    }

    public void showPage(Page page) {
        table.getItems().setAll(page.rows());
        table.setDisable(false);
        refresh.setDisable(false);
        feedback.setText("");
    }

    public void showError(String message) {
        table.setDisable(false);
        refresh.setDisable(false);
        feedback.setText(message);
    }

    private static String versions(Row row) {
        String text =
                row.approvedVersion() == null
                        ? "Nessuna approvata"
                        : "v" + row.approvedVersion() + " approvata";
        return text + (row.draftVersion() == null ? "" : " · bozza v" + row.draftVersion());
    }

    private static TableColumn<Row, String> column(
            String title, Function<Row, String> value, int width) {
        TableColumn<Row, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    static Button button(String title, String id, String style) {
        Button button = new Button(title);
        button.setId(id);
        button.getStyleClass().add(style);
        return button;
    }

    public TextField search() {
        return search;
    }

    public CheckBox inactive() {
        return inactive;
    }

    public TableView<Row> table() {
        return table;
    }

    public Button create() {
        return create;
    }

    public Button refresh() {
        return refresh;
    }
}

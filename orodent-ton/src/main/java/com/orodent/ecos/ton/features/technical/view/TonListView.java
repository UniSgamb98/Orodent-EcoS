package com.orodent.ecos.ton.features.technical.view;

import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.util.Collection;
import java.util.Objects;

/** Common TON catalogue shell. Features supply columns, optional filters and data operations. */
public class TonListView<T> extends VBox {
    private final TextField search;
    private final Button create, refresh;
    private final TableView<T> table;
    private final Label feedback = TechnicalUi.label("", "ton-error-feedback");
    private final HBox actions = new HBox(12);
    private final VBox filters = new VBox(12), options = new VBox(12);

    public TonListView(String id, String description, String prompt, String createText) {
        this(id, description, prompt, createText, new TableView<>());
    }

    protected TonListView(
            String id, String description, String prompt, String createText, TableView<T> table) {
        super(12);
        setId(id + "-list");
        setMinSize(0, 0);
        getStyleClass().add("ton-list-page");
        getStylesheets()
                .add(
                        Objects.requireNonNull(TonListView.class.getResource("/ton/css/lists.css"))
                                .toExternalForm());
        search = TechnicalUi.input(id + "-search", prompt);
        search.setMinWidth(0);
        refresh = TechnicalUi.button("Aggiorna", id + "-refresh", false);
        create = TechnicalUi.button(createText, id + "-new", true);
        create.setMinWidth(Region.USE_PREF_SIZE);
        refresh.setMinWidth(Region.USE_PREF_SIZE);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().add(create);
        Label intro = TechnicalUi.label(description, "muted-label");
        intro.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(intro, Priority.ALWAYS);
        HBox header = new HBox(12, intro, actions);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox query = new HBox(10, search, refresh);
        query.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);
        this.table = Objects.requireNonNull(table);
        if (table.getId() == null) table.setId(id + "-table");
        table.setMinSize(0, 180);
        table.setPrefHeight(360);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("Nessun risultato."));
        table.getColumns().forEach(TonListView::sizeColumn);
        table.getColumns()
                .addListener(
                        (ListChangeListener<TableColumn<T, ?>>)
                                change -> {
                                    while (change.next())
                                        if (change.wasAdded())
                                            change.getAddedSubList()
                                                    .forEach(TonListView::sizeColumn);
                                });
        VBox.setVgrow(table, Priority.ALWAYS);
        feedback.setId(id + "-feedback");
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        hideWhenEmpty(filters);
        hideWhenEmpty(options);
        VBox card = new VBox(12, query, filters, options, table, feedback);
        card.getStyleClass().add("ton-list-card");
        card.setMinSize(0, 0);
        VBox.setVgrow(card, Priority.ALWAYS);
        getChildren().addAll(header, card);
    }

    private static void sizeColumn(TableColumn<?, ?> column) {
        column.setPrefWidth(150);
        column.setMinWidth(60);
    }

    private static void hideWhenEmpty(VBox box) {
        box.visibleProperty().bind(javafx.beans.binding.Bindings.isNotEmpty(box.getChildren()));
        box.managedProperty().bind(box.visibleProperty());
    }

    public final void addAction(Node action) {
        if (action instanceof Region region) region.setMinWidth(Region.USE_PREF_SIZE);
        actions.getChildren().add(actions.getChildren().size() - 1, action);
    }

    public final void filters(String id, Node content) {
        TitledPane pane = new TitledPane("Filtri", content);
        pane.setId(id);
        pane.setExpanded(false);
        pane.setAnimated(false);
        filters.getChildren().setAll(pane);
    }

    public final void options(Node... controls) {
        options.getChildren().setAll(controls);
    }

    /** The controller supplies navigation, including its unsaved-change guard. */
    public final void onOpen(Runnable action) {
        table.setOnMouseClicked(
                event -> {
                    if (event.getClickCount() == 2
                            && !table.isDisabled()
                            && table.getSelectionModel().getSelectedItem() != null) action.run();
                });
        table.setOnKeyPressed(
                event -> {
                    if (event.getCode() == KeyCode.ENTER
                            && !table.isDisabled()
                            && table.getSelectionModel().getSelectedItem() != null) {
                        action.run();
                        event.consume();
                    }
                });
    }

    public void loading() {
        table.setDisable(true);
        refresh.setDisable(true);
        feedback("", false);
    }

    public final void showRows(Collection<? extends T> rows) {
        table.getItems().setAll(rows);
        table.setDisable(false);
        refresh.setDisable(false);
        feedback("", false);
    }

    public final void error(String message) {
        table.setDisable(false);
        refresh.setDisable(false);
        feedback(message, true);
    }

    public final void feedback(String message, boolean error) {
        feedback.setText(message);
        if (error && !feedback.getStyleClass().contains("ton-error-feedback"))
            feedback.getStyleClass().add("ton-error-feedback");
        else if (!error) feedback.getStyleClass().remove("ton-error-feedback");
    }

    public TextField search() {
        return search;
    }

    public Button create() {
        return create;
    }

    public Button refresh() {
        return refresh;
    }

    public TableView<T> table() {
        return table;
    }

    public Label feedback() {
        return feedback;
    }

    public void dispose() {
        table.setOnMouseClicked(null);
        table.setOnKeyPressed(null);
        table.getItems().clear();
    }
}

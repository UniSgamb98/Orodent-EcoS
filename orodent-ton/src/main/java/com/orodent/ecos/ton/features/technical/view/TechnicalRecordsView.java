package com.orodent.ecos.ton.features.technical.view;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.util.List;

/** Shared full-height sheet for versions, catalogue revisions and incoming references. */
public class TechnicalRecordsView<T> extends VBox {
    private final TableView<T> table = new TableView<>();
    private final Button refresh;
    private final Label feedback = TechnicalUi.label("", "muted-label");
    private Runnable open;

    public TechnicalRecordsView(String id, String title, String description) {
        super(12);
        setMinSize(0, 0);
        getStyleClass().add("ton-technical-records");
        var heading = TechnicalUi.label(title, "ton-technical-panel-title");
        heading.setId(id + "-title");
        var gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        refresh = TechnicalUi.button("Aggiorna", id + "-refresh", false);
        var toolbar = new HBox(12, heading, gap, refresh);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        table.setId(id + "-table");
        table.setMinSize(0, 120);
        table.setPrefHeight(310);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setRowFactory(
                t -> {
                    var row = new TableRow<T>();
                    row.setOnMouseClicked(
                            e -> {
                                if (e.getClickCount() == 2 && !row.isEmpty() && open != null) {
                                    table.getSelectionModel().select(row.getIndex());
                                    open.run();
                                    e.consume();
                                }
                            });
                    return row;
                });
        table.setOnKeyPressed(
                e -> {
                    if (e.getCode() == KeyCode.ENTER
                            && table.getSelectionModel().getSelectedItem() != null
                            && open != null) {
                        open.run();
                        e.consume();
                    }
                });
        feedback.setId(id + "-feedback");
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        getChildren().add(toolbar);
        if (description != null && !description.isBlank())
            getChildren().add(TechnicalUi.label(description, "muted-label"));
        getChildren().addAll(table, feedback);
        reset(false);
    }

    public void onOpen(Runnable action) {
        open = action;
    }

    public void show(List<T> rows) {
        table.getItems().setAll(rows);
        table.setDisable(false);
        table.setPlaceholder(new Label("Nessun risultato."));
        feedback.getStyleClass().remove("ton-error-feedback");
        refresh.setDisable(false);
        feedback.setText("");
        if (!rows.isEmpty()) table.getSelectionModel().selectFirst();
    }

    public void reset(boolean saved) {
        table.getItems().clear();
        table.setDisable(false);
        table.setPlaceholder(
                new Label(
                        saved ? "Apri la scheda per caricare i dati." : "Salva prima la scheda."));
        refresh.setDisable(!saved);
        feedback.setText("");
        feedback.getStyleClass().remove("ton-error-feedback");
    }

    public void loading() {
        feedback.getStyleClass().remove("ton-error-feedback");
        table.setDisable(true);
        refresh.setDisable(true);
        feedback.setText("Caricamento…");
    }

    public void error(String message) {
        if (!feedback.getStyleClass().contains("ton-error-feedback"))
            feedback.getStyleClass().add("ton-error-feedback");
        table.setDisable(false);
        refresh.setDisable(false);
        feedback.setText(message);
    }

    public TableView<T> table() {
        return table;
    }

    public Button refresh() {
        return refresh;
    }

    public void dispose() {
        open = null;
        refresh.setOnAction(null);
        table.setRowFactory(null);
        table.setOnKeyPressed(null);
        table.getItems().clear();
    }
}

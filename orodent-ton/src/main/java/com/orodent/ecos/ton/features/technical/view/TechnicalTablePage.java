package com.orodent.ecos.ton.features.technical.view;

import com.orodent.ecos.ton.features.technical.model.PageData;

import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Bounded pages for versions and incoming article references. */
public final class TechnicalTablePage<T> extends VBox {
    private final TableView<T> table = new TableView<>();
    private final Button previous, next, refresh, open;
    private final Label result = new Label();

    public TechnicalTablePage(String id, String openText) {
        super(12);
        setMinWidth(0);
        table.setId(id + "-table");
        table.setPrefHeight(310);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);
        previous = TechnicalUi.button("←", id + "-previous", false);
        next = TechnicalUi.button("→", id + "-next", false);
        refresh = TechnicalUi.button("Aggiorna", id + "-refresh", false);
        open = TechnicalUi.button(openText, id + "-open", false);
        previous.setAccessibleText("Pagina precedente");
        next.setAccessibleText("Pagina successiva");
        getChildren().addAll(table, new FlowPane(10, 8, previous, next, result, refresh, open));
        reset(false);
    }

    public void show(PageData<T> page) {
        table.setDisable(false);
        table.getItems().setAll(page.rows());
        table.setPlaceholder(new Label("Nessun risultato."));
        result.setText(page.label());
        previous.setDisable(page.offset() == 0);
        next.setDisable(!page.hasNext());
        refresh.setDisable(false);
        open.setDisable(page.rows().isEmpty());
        if (!page.rows().isEmpty()) table.getSelectionModel().selectFirst();
    }

    public void reset(boolean saved) {
        table.getItems().clear();
        table.setPlaceholder(
                new Label(
                        saved ? "Apri la scheda per caricare i dati." : "Salva prima la scheda."));
        result.setText("");
        for (Button b : new Button[] {previous, next, open}) b.setDisable(true);
        refresh.setDisable(!saved);
    }

    public void loading() {
        table.setDisable(true);
        result.setText("Caricamento…");
        for (Button b : new Button[] {previous, next, open}) b.setDisable(true);
    }

    public void error(String message) {
        table.setDisable(false);
        result.setText(message);
        refresh.setDisable(false);
    }

    public TableView<T> table() {
        return table;
    }

    public Button previous() {
        return previous;
    }

    public Button next() {
        return next;
    }

    public Button refresh() {
        return refresh;
    }

    public Button open() {
        return open;
    }

    public void dispose() {
        for (Button b : new Button[] {previous, next, refresh, open}) b.setOnAction(null);
        table.setOnMouseClicked(null);
        table.setOnKeyPressed(null);
        table.getItems().clear();
    }
}

package com.orodent.ecos.ton.features.technical.view;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Objects;

/** Visual shell for Dati tecnici details only. Each feature owns its data and command rules. */
public abstract class TechnicalDetailView extends VBox {
    protected final Button back, reload;
    protected final TabPane tabs = new TabPane();
    protected final FlowPane actions = new FlowPane(10, 8);
    protected final Label feedback = TechnicalUi.label("", "muted-label");
    protected final ReadOnlyStringWrapper headerTitle = new ReadOnlyStringWrapper("");
    private final VBox statusCard = new VBox(6);
    private final HBox statusRow = new HBox(12, statusCard);

    protected TechnicalDetailView(String id, String backText) {
        super(12);
        setId(id + "-editor");
        setMinSize(0, 0);
        getStyleClass().add("ton-technical-detail");
        getStylesheets()
                .add(
                        Objects.requireNonNull(
                                        TechnicalDetailView.class.getResource(
                                                "/ton/css/technical-editor.css"))
                                .toExternalForm());
        back = TechnicalUi.button(backText, id + "-back", false);
        reload = TechnicalUi.button("Aggiorna dati", id + "-reload", false);
        back.getStyleClass().add("ton-editor-back-link");
        reload.getStyleClass().add("ton-editor-refresh");
        back.setMinWidth(Region.USE_PREF_SIZE);
        reload.setMinWidth(Region.USE_PREF_SIZE);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox toolbar = new HBox(12, back, gap, reload);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        statusCard.getStyleClass().add("ton-editor-status");
        statusCard.setMinWidth(0);
        statusCard.setMaxWidth(Double.MAX_VALUE);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        tabs.setId(id + "-tabs");
        tabs.getStyleClass().add("ton-editor-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setMinHeight(230);
        tabs.setPrefHeight(420);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        actions.getStyleClass().add("ton-editor-actions");
        feedback.setId(id + "-feedback");
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        getChildren().setAll(toolbar, statusRow, tabs, actions, feedback);
    }

    protected final void status(boolean expand, Node... content) {
        statusCard.getChildren().setAll(content);
        HBox.setHgrow(statusCard, expand ? Priority.ALWAYS : Priority.NEVER);
    }

    protected final void addStatusAction(Node action) {
        if (action instanceof Region region) region.setMinWidth(Region.USE_PREF_SIZE);
        statusRow.getChildren().add(action);
    }

    protected final void actions(Node... buttons) {
        actions.getChildren().setAll(buttons);
    }

    protected final Tab addSheet(String title, Node content) {
        return addSheet(title, content, false);
    }

    protected final Tab addSheet(String title, Node content, boolean fitToHeight) {
        content.getStyleClass().add("ton-editor-content");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(fitToHeight);
        Tab tab = new Tab(title, scroll);
        tabs.getTabs().add(tab);
        return tab;
    }

    public final void feedback(String message, boolean error) {
        feedback.setText(message);
        feedback.getStyleClass().remove("ton-error-feedback");
        if (error) feedback.getStyleClass().add("ton-error-feedback");
    }

    public final ReadOnlyStringProperty headerTitleProperty() {
        return headerTitle.getReadOnlyProperty();
    }

    public final Button back() {
        return back;
    }

    public final Button reload() {
        return reload;
    }

    public final TabPane tabs() {
        return tabs;
    }
}

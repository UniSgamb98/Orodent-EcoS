package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Reusable resource section with a title, add button and card list. */
final class ClienteResourceSection extends VBox {

    private final Button addButton;
    private final Button editButton = new Button("Modifica");
    private final Button saveButton = new Button("Salva");
    private final Button cancelButton = new Button("Annulla");
    private final VBox list = new VBox(10);
    private boolean inline;

    void setInline() {
        inline = true;
        list.getChildren().forEach(ClienteProfileFormControls::compactDeletes);
        setActionMode(ActionMode.EDIT);
    }

    ClienteResourceSection(String titleText, String addButtonText) {
        super(10);
        getStyleClass().add("client-profile-resource-group");

        HBox header = new HBox(10);
        header.getStyleClass().add("client-profile-resource-group-header");
        Label title = new Label(titleText);
        title.getStyleClass().add("client-profile-resource-card-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        addButton = new Button(addButtonText);
        addButton.getStyleClass().add("clients-primary-button");
        editButton.getStyleClass().add("clients-filter-button");
        saveButton.getStyleClass().add("clients-primary-button");
        cancelButton.getStyleClass().add("clients-filter-button");
        header.getChildren().addAll(title, spacer, editButton, addButton, saveButton, cancelButton);

        setActionMode(ActionMode.HIDDEN);

        getChildren().addAll(header, list);
    }

    Button addButton() {
        return addButton;
    }

    Button editButton() {
        return editButton;
    }

    Button saveButton() {
        return saveButton;
    }

    Button cancelButton() {
        return cancelButton;
    }

    void showViewActions() {
        setActionMode(ActionMode.VIEW);
    }

    void showEditActions() {
        setActionMode(ActionMode.EDIT);
    }

    void hideActions() {
        setActionMode(ActionMode.HIDDEN);
    }

    void clearCards() {
        list.getChildren().clear();
    }

    void addCard(Node card) {
        if (inline) ClienteProfileFormControls.compactDeletes(card);
        list.getChildren().add(card);
    }

    void addEditorCard(
            VBox card,
            boolean expanded,
            String kind,
            java.util.function.Supplier<String> title,
            java.util.function.Supplier<String> subtitle,
            Button remove,
            javafx.beans.Observable... values) {
        VBox body = new VBox(10);
        body.getStyleClass().add("crm-resource-body");
        body.getChildren().setAll(java.util.List.copyOf(card.getChildren()));
        body.getChildren().remove(remove);
        Label name = new Label();
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        name.getStyleClass().add("crm-resource-name");
        Label description = new Label();
        description.setMinWidth(0);
        description.setMaxWidth(Double.MAX_VALUE);
        description.getStyleClass().add("crm-resource-description");
        Runnable refreshSummary =
                () -> {
                    String text = title.get();
                    name.setText(text.isBlank() ? (expanded ? "Nuovo " : "") + kind : text);
                    description.setText(
                            subtitle.get().isBlank() ? "Dati da completare" : subtitle.get());
                };
        for (var value : values)
            value.addListener((javafx.beans.InvalidationListener) o -> refreshSummary.run());
        refreshSummary.run();
        VBox info = new VBox(3, name, description);
        info.setMinWidth(0);
        Label action = new Label();
        action.getStyleClass().add("crm-resource-expand-label");
        javafx.scene.layout.BorderPane graphic = new javafx.scene.layout.BorderPane();
        graphic.setCenter(info);
        graphic.setRight(action);
        javafx.scene.layout.BorderPane.setAlignment(action, javafx.geometry.Pos.CENTER_RIGHT);
        Button toggle = new Button();
        toggle.setGraphic(graphic);
        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.setMinWidth(0);
        toggle.getStyleClass().add("crm-resource-toggle");
        toggle.setAccessibleText("Apri o chiudi " + kind);
        graphic.prefWidthProperty().bind(toggle.widthProperty().subtract(20));
        var menu =
                ClienteProfileFormControls.rowMenu(
                        null, remove, "", "Elimina " + kind + " dalla scheda cliente");
        menu.setAccessibleText("Azioni " + kind);
        menu.setTooltip(new javafx.scene.control.Tooltip("Azioni " + kind));
        HBox header = new HBox(4, toggle, menu);
        header.getStyleClass().add("crm-resource-heading");
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(toggle, Priority.ALWAYS);
        card.getStyleClass().add("crm-resource-card");
        card.getChildren().setAll(header, body);
        java.util.function.Consumer<Boolean> show =
                open -> {
                    body.setVisible(open);
                    body.setManaged(open);
                    action.setText(open ? "Chiudi" : "Apri");
                    toggle.setAccessibleHelp((open ? "Chiudi " : "Espandi ") + kind);
                    card.pseudoClassStateChanged(
                            javafx.css.PseudoClass.getPseudoClass("expanded"), open);
                };
        toggle.setOnAction(e -> show.accept(!body.isManaged()));
        show.accept(expanded);
        list.getChildren().removeIf(node -> node instanceof Label);
        addCard(card);
    }

    void removeCard(Node card) {
        list.getChildren().remove(card);
    }

    private void setActionMode(ActionMode mode) {
        if (inline) {
            setVisible(editButton, false);
            setVisible(addButton, true);
            setVisible(saveButton, false);
            setVisible(cancelButton, false);
            return;
        }
        setVisible(editButton, mode == ActionMode.VIEW);
        setVisible(addButton, mode == ActionMode.EDIT);
        setVisible(saveButton, mode == ActionMode.EDIT);
        setVisible(cancelButton, mode == ActionMode.EDIT);
    }

    private void setVisible(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    private enum ActionMode {
        HIDDEN,
        VIEW,
        EDIT
    }
}

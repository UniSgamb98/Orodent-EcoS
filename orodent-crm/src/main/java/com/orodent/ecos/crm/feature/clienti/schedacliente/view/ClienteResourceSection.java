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

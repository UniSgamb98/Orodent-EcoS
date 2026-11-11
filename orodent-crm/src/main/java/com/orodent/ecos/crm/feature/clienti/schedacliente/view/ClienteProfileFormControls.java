package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

final class ClienteProfileFormControls {

    private ClienteProfileFormControls() {}

    static VBox createSection(String titleText, Node body) {
        VBox section = new VBox(12);
        section.getStyleClass().add("new-client-section");
        Label title = new Label(titleText);
        title.getStyleClass().add("new-client-section-title");
        section.getChildren().addAll(title, body);
        return section;
    }

    static TextField createTextField(String value, String prompt) {
        TextField field = new TextField(value == null ? "" : value);
        field.setPromptText(prompt);
        field.getStyleClass().add("client-profile-edit-field");
        return field;
    }

    static HBox createFieldRow(String labelText, Region field) {
        HBox row = new HBox(8);
        row.getStyleClass().add("client-profile-edit-row");
        row.getChildren().addAll(createEditLabel(labelText), field);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    static Label createInfoLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("client-profile-info-label");
        label.setWrapText(true);
        return label;
    }

    static Label createEditLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("client-profile-edit-label");
        label.setMinWidth(120);
        return label;
    }

    static Label createEditSectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("client-profile-edit-section-title");
        return label;
    }

    static Button createSmallButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("client-profile-small-filter-button");
        return button;
    }
}

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

    static HBox copyableValue(String value) {
        Label text = createInfoLabel(value);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        Button copy = new Button("Copia");
        copy.getStyleClass().add("client-profile-copy");
        copy.setAccessibleText("Copia " + value);
        copy.setOnAction(
                e -> {
                    var clipboard = new javafx.scene.input.ClipboardContent();
                    clipboard.putString(value);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboard);
                });
        HBox row = new HBox(8, text, copy);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    static javafx.scene.control.MenuButton rowMenu(
            Button add, Button remove, String addText, String removeText) {
        var menu = new javafx.scene.control.MenuButton("⋯");
        menu.getStyleClass().add("crm-row-menu");
        menu.setAccessibleText("Azioni sulla riga");
        menu.setTooltip(new javafx.scene.control.Tooltip("Azioni sulla riga"));
        if (add != null) {
            var item = new javafx.scene.control.MenuItem(addText);
            item.setOnAction(e -> add.fire());
            menu.getItems().add(item);
        }
        if (remove != null) {
            var item = new javafx.scene.control.MenuItem(removeText);
            item.setOnAction(e -> remove.fire());
            menu.getItems().add(item);
        }
        return menu;
    }

    static void compactDeletes(Node node) {
        if (node instanceof javafx.scene.layout.Pane pane) {
            for (Node child : java.util.List.copyOf(pane.getChildren())) {
                if (child instanceof Button button
                        && button.getStyleClass()
                                .contains("client-profile-delete-interaction-button")) {
                    int index = pane.getChildren().indexOf(child);
                    pane.getChildren().set(index, rowMenu(null, button, "", "Elimina riga"));
                } else compactDeletes(child);
            }
        }
    }

    static HBox resourceField(String text, Region field) {
        Label label = new Label(text);
        label.getStyleClass().add("client-profile-resource-field-label");
        label.setMinWidth(76);
        label.setPrefWidth(76);
        label.setWrapText(true);
        field.setMinWidth(80);
        field.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(8, label, field);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getStyleClass().add("crm-resource-field-row");
        row.setPrefWidth(208);
        row.setMinWidth(180);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    static String summary(String... values) {
        return java.util.Arrays.stream(values)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(" · "));
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
        VBox column = new VBox(4, createEditLabel(labelText), field);
        field.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().add(column);
        HBox.setHgrow(column, Priority.ALWAYS);
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

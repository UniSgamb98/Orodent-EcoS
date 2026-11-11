package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyCode;

import java.util.List;
import java.util.function.Consumer;

/** Editable combo box that shows suggestions and keeps the suggested suffix selected. */
final class ResourceAutocompleteComboBox extends ComboBox<String> {

    private boolean refreshingSuggestions;
    private boolean lastEditWasDeletion;

    ResourceAutocompleteComboBox(
            String value, String prompt, Consumer<ResourceAutocompleteComboBox> onUserTextChanged) {
        setEditable(true);
        String initialValue = display(value);
        setValue(initialValue);
        getEditor().setText(initialValue);
        setPromptText(prompt);
        getStyleClass().add("client-profile-resource-combo");
        getEditor()
                .setOnKeyPressed(
                        event ->
                                lastEditWasDeletion =
                                        event.getCode() == KeyCode.BACK_SPACE
                                                || event.getCode() == KeyCode.DELETE);
        getEditor()
                .textProperty()
                .addListener(
                        (observable, oldValue, newValue) -> {
                            if (!refreshingSuggestions) {
                                onUserTextChanged.accept(this);
                            }
                        });
    }

    void setSuggestions(List<String> suggestions, boolean completeFirstSuggestion) {
        String typedText = textValue();
        refreshingSuggestions = true;
        try {
            if (!getItems().equals(suggestions)) {
                getItems().setAll(suggestions);
            }
            if (completeFirstSuggestion
                    && !lastEditWasDeletion
                    && applyFirstSuggestionCompletion(typedText, suggestions)) {
                return;
            }
            if (!textValue().equals(typedText)) {
                getEditor().setText(typedText);
                getEditor().positionCaret(typedText.length());
            }
        } finally {
            refreshingSuggestions = false;
            lastEditWasDeletion = false;
        }
    }

    String textValue() {
        String value = getEditor().getText();
        return value == null ? "" : value;
    }

    String trimmedTextValue() {
        return textValue().trim();
    }

    private boolean applyFirstSuggestionCompletion(String typedText, List<String> suggestions) {
        if (typedText.isBlank()) {
            return false;
        }
        return suggestions.stream()
                .filter(value -> value.length() > typedText.length())
                .filter(value -> value.regionMatches(true, 0, typedText, 0, typedText.length()))
                .findFirst()
                .map(
                        value -> {
                            getEditor().setText(value);
                            Platform.runLater(
                                    () ->
                                            getEditor()
                                                    .selectRange(
                                                            typedText.length(), value.length()));
                            return true;
                        })
                .orElse(false);
    }

    private String display(String value) {
        return value == null ? "" : value;
    }
}

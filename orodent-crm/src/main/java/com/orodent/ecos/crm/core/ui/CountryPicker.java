package com.orodent.ecos.crm.core.ui;

import com.orodent.ecos.crm.core.country.Countries;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.util.StringConverter;

import java.util.*;

/** Searchable offline selector; unknown imported values can be retained, never invented. */
public final class CountryPicker extends ComboBox<String> {
    private final String original;
    private final String initialDisplay;
    private boolean updating;

    public CountryPicker(String raw) {
        original = Objects.toString(raw, "");
        initialDisplay = Countries.code(raw).map(Countries::name).orElse(original);
        setEditable(true);
        setPromptText("Cerca o scegli paese");
        setAccessibleText("Paese");
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("crm-country-picker");
        setVisibleRowCount(10);
        setCellFactory(
                list ->
                        new javafx.scene.control.ListCell<>() {
                            @Override
                            protected void updateItem(String text, boolean empty) {
                                super.updateItem(text, empty);
                                setText(
                                        empty
                                                ? null
                                                : text == null || text.isBlank()
                                                        ? "Paese non indicato"
                                                        : text);
                            }
                        });
        getItems().add("");
        getItems().addAll(Countries.names());
        setConverter(
                new StringConverter<>() {
                    public String toString(String value) {
                        return Objects.toString(value, "");
                    }

                    public String fromString(String value) {
                        return Objects.toString(value, "");
                    }
                });
        if (!initialDisplay.isBlank() && !getItems().contains(initialDisplay)) {
            getItems().add(0, initialDisplay);
            setTooltip(new Tooltip("Paese importato da verificare: " + original));
        }
        setValue(initialDisplay);
        getEditor().setText(initialDisplay);
        getEditor()
                .textProperty()
                .addListener(
                        (o, a, b) ->
                                pseudoClassStateChanged(
                                        javafx.css.PseudoClass.getPseudoClass("unverified"),
                                        b != null && !b.isBlank() && Countries.code(b).isEmpty()));
        pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("unverified"),
                !original.isBlank() && Countries.code(original).isEmpty());
        getEditor()
                .setOnKeyReleased(
                        e -> {
                            if (e.getCode() == KeyCode.ESCAPE
                                    || e.getCode() == KeyCode.ENTER
                                    || e.getCode().isArrowKey()
                                    || e.getCode() == KeyCode.TAB) return;
                            String typed = getEditor().getText();
                            if (updating) return;
                            updating = true;
                            int caret = getEditor().getCaretPosition();
                            String key = Countries.key(typed);
                            getItems().setAll("");
                            getItems()
                                    .addAll(
                                            Countries.names().stream()
                                                    .filter(n -> Countries.key(n).contains(key))
                                                    .toList());
                            getEditor().setText(typed);
                            getEditor().positionCaret(caret);
                            updating = false;
                            if (!isShowing() && getEditor().isFocused()) show();
                        });
        setOnShowing(
                e -> {
                    if (getItems().size() <= 1) {
                        String typed = getEditor().getText();
                        getItems().setAll("");
                        getItems().addAll(Countries.names());
                        getEditor().setText(typed);
                    }
                });
    }

    public String draftValue() {
        String typed = Objects.toString(getEditor().getText(), "");
        return typed.equals(initialDisplay) ? original : typed.strip();
    }

    public String validatedValue() {
        return Objects.toString(Countries.forWrite(draftValue(), original), "");
    }
}

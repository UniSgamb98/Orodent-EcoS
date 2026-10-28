package com.orodent.ecos.ton.features.compositions.view;

import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import java.util.Objects;

public final class CompositionsView extends BorderPane {
    private final CompositionListView list = new CompositionListView();
    private final CompositionEditorView editor = new CompositionEditorView();

    public CompositionsView() {
        setId("ton-compositions-view");
        setMinWidth(0);
        getStyleClass().add("ton-compositions");
        getStylesheets()
                .add(
                        Objects.requireNonNull(getClass().getResource("/ton/css/compositions.css"))
                                .toExternalForm());
        showList();
    }

    public CompositionListView list() {
        return list;
    }

    public CompositionEditorView editor() {
        return editor;
    }

    public void showList() {
        setCenter(list);
    }

    public void showEditor() {
        setCenter(editor);
    }

    public void loading() {
        var label = new Label("Caricamento composizione…");
        label.setId("ton-composition-loading");
        setCenter(label);
    }

    public void dispose() {
        list.results().dispose();
        editor.dispose();
        setCenter(null);
    }
}

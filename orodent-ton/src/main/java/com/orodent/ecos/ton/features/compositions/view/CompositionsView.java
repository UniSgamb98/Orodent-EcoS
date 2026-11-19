package com.orodent.ecos.ton.features.compositions.view;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import java.util.Objects;

public final class CompositionsView extends BorderPane {
    private final CompositionListView list = new CompositionListView();
    private final CompositionEditorView editor = new CompositionEditorView();

    private final ReadOnlyStringWrapper subpage = new ReadOnlyStringWrapper("");

    public CompositionsView() {
        subpage.bind(
                Bindings.createStringBinding(
                        () -> getCenter() == editor ? editor.headerTitleProperty().get() : "",
                        centerProperty(),
                        editor.headerTitleProperty()));
        setId("ton-compositions-view");
        setMinWidth(0);
        getStyleClass().add("ton-compositions");
        getStylesheets()
                .add(
                        Objects.requireNonNull(getClass().getResource("/ton/css/compositions.css"))
                                .toExternalForm());
        showList();
    }

    public ReadOnlyStringProperty subpageProperty() {
        return subpage.getReadOnlyProperty();
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
        list.dispose();
        editor.dispose();
        setCenter(null);
    }
}

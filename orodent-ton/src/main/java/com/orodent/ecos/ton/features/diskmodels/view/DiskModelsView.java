package com.orodent.ecos.ton.features.diskmodels.view;

import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import java.util.Objects;

public final class DiskModelsView extends BorderPane {
    private final DiskModelListView list = new DiskModelListView();
    private final DiskModelEditorView editor = new DiskModelEditorView();

    public DiskModelsView() {
        setId("ton-models-view");
        setMinWidth(0);
        getStyleClass().add("ton-models");
        getStylesheets()
                .add(
                        Objects.requireNonNull(getClass().getResource("/ton/css/disk-models.css"))
                                .toExternalForm());
        showList();
    }

    public DiskModelListView list() {
        return list;
    }

    public DiskModelEditorView editor() {
        return editor;
    }

    public void showList() {
        setCenter(list);
    }

    public void showEditor() {
        setCenter(editor);
    }

    public void loading() {
        var l = new Label("Caricamento modello…");
        l.setId("ton-model-loading");
        setCenter(l);
    }

    public void dispose() {
        list.results().dispose();
        editor.dispose();
        setCenter(null);
    }
}

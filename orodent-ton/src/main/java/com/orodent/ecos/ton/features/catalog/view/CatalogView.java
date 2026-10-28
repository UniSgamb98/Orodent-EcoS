package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;

import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import java.util.Objects;

public final class CatalogView extends BorderPane {
    private final CatalogListView list;
    private final CatalogEditorView editor;

    public CatalogView(CatalogKind kind) {
        setId("ton-" + kind.pluralId() + "-view");
        setMinWidth(0);
        getStyleClass().add("ton-catalogue");
        getStylesheets()
                .add(
                        Objects.requireNonNull(getClass().getResource("/ton/css/catalogues.css"))
                                .toExternalForm());
        list = new CatalogListView(kind);
        editor = new CatalogEditorView(kind);
        showList();
    }

    public CatalogListView list() {
        return list;
    }

    public CatalogEditorView editor() {
        return editor;
    }

    public void showList() {
        setCenter(list);
    }

    public void showEditor() {
        setCenter(editor);
    }

    public void loading() {
        Label label = new Label("Caricamento scheda…");
        label.setId("ton-catalogue-loading");
        setCenter(label);
    }

    public void dispose() {
        setCenter(null);
    }
}

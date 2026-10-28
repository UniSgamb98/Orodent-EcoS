package com.orodent.ecos.ton.features.powders.view;

import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

public final class PowdersView extends BorderPane {
    private final PowderListView list = new PowderListView();
    private final PowderEditorView editor = new PowderEditorView();

    public PowdersView() {
        setId("ton-powders-view");
        setMinWidth(0);
        getStyleClass().add("ton-powders");
        getStylesheets()
                .add(
                        java.util.Objects.requireNonNull(
                                        getClass().getResource("/ton/css/powders.css"))
                                .toExternalForm());
        showList();
    }

    public PowderListView list() {
        return list;
    }

    public PowderEditorView editor() {
        return editor;
    }

    public void showList() {
        setCenter(list);
    }

    public void showEditor() {
        setCenter(editor);
    }

    public void loading() {
        Label label = new Label("Caricamento scheda tecnica…");
        label.setId("ton-powder-loading");
        setCenter(label);
    }

    public void dispose() {
        setCenter(null);
    }
}

package com.orodent.ecos.ton.features.documents.view;

import static com.orodent.ecos.ton.features.documents.model.DocumentTemplates.*;
import static com.orodent.ecos.ton.features.technical.view.TechnicalUi.*;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;

/**
 * Native navigation/version controls around the local HTML editor. WebView is created on demand.
 */
public final class DocumentTemplatesView extends BorderPane {
    public final TextField search = input("ton-document-search", "Cerca per codice o nome");
    public final TableView<Template> table = new TableView<>();
    public final Button create = button("Nuovo modello", "ton-document-new", true);
    public final Button open = button("Apri modello", "ton-document-open", true);
    public final Button refresh = button("Ricarica", "ton-document-refresh", false);
    public final Button previous = button("←", "ton-document-previous", false);
    public final Button next = button("→", "ton-document-next", false);
    public final Button back = button("← Elenco modelli", "ton-document-back", false);
    public final Button save = button("Salva bozza", "ton-document-save", true);
    public final Button approve = button("Salva e approva", "ton-document-approve", true);
    public final Button newVersion = button("Nuova versione", "ton-document-new-version", false);
    public final Button delete = button("Elimina bozza", "ton-document-delete", false);
    public final Button importFile = button("Importa JSON", "ton-document-import", false);
    public final Button exportFile = button("Esporta JSON", "ton-document-export", false);
    public final Button reload = button("Ricarica versione", "ton-document-reload", false);
    public final TextField code = input("ton-document-code", "Es. PRESSATURA");
    public final ComboBox<Template> versions = new ComboBox<>();
    private final Label feedback = label("", "muted-label");
    private final Label state = label("", "ton-description");
    private final Label paging = label("", "muted-label");
    private final BorderPane listing = new BorderPane();
    private final BorderPane editing = new BorderPane();
    private final FlowPane actions = new FlowPane(8, 6);
    private DocumentEditorPane editor;

    public DocumentTemplatesView() {
        setId("ton-document-templates-view");
        setMinSize(0, 0);
        table.setId("ton-document-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nessun modello. Crea il primo foglio operativo."));
        column(table, "Codice", 180, Template::code);
        column(table, "Nome", 280, Template::name);
        column(table, "Versione", 80, t -> "v" + t.number());
        column(table, "Stato", 110, t -> stateLabel(t.state()));
        column(
                table,
                "Aggiornato",
                160,
                t -> t.updated().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        var tools = new HBox(10, search, refresh, create);
        HBox.setHgrow(search, Priority.ALWAYS);
        var top =
                new VBox(
                        12,
                        label("Modelli documento", "ton-page-heading"),
                        label(
                                "Progetta il foglio operativo, salva le bozze e conserva le"
                                    + " versioni approvate. L’anteprima usa dati di esempio.",
                                "ton-description"),
                        tools);
        top.setPadding(new Insets(18, 24, 16, 24));
        listing.setTop(top);
        listing.setCenter(table);
        var bottom = new HBox(10, previous, paging, next, new Region(), open);
        bottom.setPadding(new Insets(12, 24, 16, 24));
        HBox.setHgrow(bottom.getChildren().get(3), Priority.ALWAYS);
        listing.setBottom(bottom);
        code.setPrefColumnCount(15);
        code.setMaxWidth(220);
        versions.setId("ton-document-versions");
        versions.setPrefWidth(190);
        var meta =
                new FlowPane(
                        10,
                        6,
                        back,
                        new Label("Codice *"),
                        code,
                        new Label("Versione"),
                        versions,
                        state);
        meta.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        actions.getChildren()
                .addAll(save, approve, newVersion, reload, importFile, exportFile, delete);
        var controls = new VBox(8, meta, actions);
        controls.setPadding(new Insets(8, 12, 8, 12));
        editing.setTop(controls);
        feedback.setId("ton-document-feedback");
        feedback.setPadding(new Insets(7, 12, 7, 12));
        setBottom(feedback);
        showList();
    }

    public void showList() {
        closeEditor();
        setCenter(listing);
        feedback("", false);
    }

    public DocumentEditorPane showEditor() {
        if (editor == null) {
            editor = new DocumentEditorPane();
            editing.setCenter(editor);
        }
        setCenter(editing);
        return editor;
    }

    public DocumentEditorPane editor() {
        return editor;
    }

    public void closeEditor() {
        if (editor != null) editor.dispose();
        editor = null;
        editing.setCenter(null);
    }

    public void page(Page page) {
        table.getItems().setAll(page.rows());
        previous.setDisable(page.offset() == 0);
        next.setDisable(!page.more());
        paging.setText(
                page.rows().isEmpty()
                        ? "Nessun modello"
                        : (page.offset() + 1) + "–" + (page.offset() + page.rows().size()));
    }

    public void listingBusy(boolean value) {
        listing.setDisable(value);
    }

    public void controls(Template selected, boolean busy, boolean ready) {
        boolean draft = selected == null || selected.state() == State.BOZZA;
        back.setDisable(busy);
        code.setDisable(busy || selected != null);
        versions.setDisable(busy || selected == null || !ready);
        save.setDisable(busy || !ready || !draft);
        approve.setDisable(busy || !ready || !draft);
        newVersion.setDisable(busy || !ready || draft);
        delete.setDisable(busy || !ready || selected == null || !draft);
        importFile.setDisable(busy || !ready || !draft);
        exportFile.setDisable(busy || !ready);
        reload.setDisable(busy || selected == null || !ready);
        state.setText(
                selected == null
                        ? "Nuovo · non salvato"
                        : stateLabel(selected.state()) + (draft ? "" : " · Sola lettura"));
        if (editor != null) editor.editable(ready && draft && !busy);
    }

    public void feedback(String message, boolean error) {
        feedback.setText(message);
        feedback.getStyleClass().remove("ton-error-feedback");
        if (error) feedback.getStyleClass().add("ton-error-feedback");
    }

    public void dispose() {
        closeEditor();
        table.getItems().clear();
        setCenter(null);
    }
}

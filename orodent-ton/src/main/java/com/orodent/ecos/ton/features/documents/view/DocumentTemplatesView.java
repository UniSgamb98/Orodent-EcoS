package com.orodent.ecos.ton.features.documents.view;

import static com.orodent.ecos.ton.features.documents.model.DocumentTemplates.*;
import static com.orodent.ecos.ton.features.technical.view.TechnicalUi.*;

import com.orodent.ecos.documents.DocumentEditorPane;
import com.orodent.ecos.ton.features.technical.view.TonListView;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;

/**
 * Native navigation/version controls around the local HTML editor. WebView is created on demand.
 */
public final class DocumentTemplatesView extends BorderPane {
    private final TonListView<Template> listing =
            new TonListView<>(
                    "ton-document",
                    "Progetta documenti ed etichette, salva le bozze e conserva le versioni"
                            + " approvate. L’anteprima usa dati di esempio.",
                    "Cerca per codice o nome",
                    "Nuovo modello");
    public final TextField search = listing.search();
    public final TableView<Template> table = listing.table();
    public final Button create = listing.create();
    public final Button refresh = listing.refresh();
    public final Button back = button("← Elenco modelli", "ton-document-back", false);
    public final Button save = button("Salva bozza", "ton-document-save", true);
    public final Button approve = button("Salva e approva", "ton-document-approve", true);
    public final Button newVersion = button("Nuova versione", "ton-document-new-version", false);
    public final Button delete = button("Elimina bozza", "ton-document-delete", false);
    public final Button reload = button("Ricarica versione", "ton-document-reload", false);
    public final TextField code = input("ton-document-code", "Es. PRESSATURA");
    public final ComboBox<Template> versions = new ComboBox<>();
    private final Label feedback = label("", "muted-label");
    private final Label state = label("", "ton-description");
    private final BorderPane editing = new BorderPane();
    private final FlowPane actions = new FlowPane(8, 6);
    private DocumentEditorPane editor;

    public DocumentTemplatesView() {
        setId("ton-document-templates-view");
        setMinSize(0, 0);
        table.setId("ton-document-table");
        table.setPlaceholder(new Label("Nessun modello. Crea il primo documento."));
        column(table, "Codice", 180, Template::code);
        column(table, "Nome", 280, Template::name);
        column(
                table,
                "Tipo",
                140,
                t -> t.type().equals("ETICHETTA") ? "Etichetta dischi" : "Foglio operativo");
        column(table, "Versione", 80, t -> "v" + t.number());
        column(table, "Stato", 110, t -> stateLabel(t.state()));
        column(
                table,
                "Aggiornato",
                160,
                t -> t.updated().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
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
        actions.getChildren().addAll(save, approve, newVersion, reload, delete);
        var controls = new VBox(8, meta, actions);
        controls.setPadding(new Insets(8, 12, 8, 12));
        editing.setTop(controls);
        feedback.setId("ton-document-feedback");
        feedback.setPadding(new Insets(7, 12, 7, 12));
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        showList();
    }

    public void showList() {
        closeEditor();
        setCenter(listing);
        setBottom(null);
        feedback("", false);
    }

    public DocumentEditorPane showEditor() {
        if (editor == null) {
            editor = new DocumentEditorPane();
            editing.setCenter(editor);
        }
        setCenter(editing);
        setBottom(feedback);
        return editor;
    }

    public void onOpen(Runnable action) {
        listing.onOpen(action);
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
        listing.showRows(page.rows());
    }

    public void listingBusy(boolean value) {
        if (value) listing.loading();
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
        reload.setDisable(busy || selected == null || !ready);
        state.setText(
                selected == null
                        ? "Nuovo · non salvato"
                        : stateLabel(selected.state()) + (draft ? "" : " · Sola lettura"));
        if (editor != null) {
            editor.purposeEditable(ready && selected == null && !busy);
            editor.editable(ready && draft && !busy);
        }
    }

    public void feedback(String message, boolean error) {
        if (getCenter() == listing) {
            if (error) listing.error(message);
            else listing.feedback(message, false);
            return;
        }
        feedback.setText(message);
        feedback.getStyleClass().remove("ton-error-feedback");
        if (error) feedback.getStyleClass().add("ton-error-feedback");
    }

    public void dispose() {
        closeEditor();
        listing.dispose();
        setCenter(null);
    }
}

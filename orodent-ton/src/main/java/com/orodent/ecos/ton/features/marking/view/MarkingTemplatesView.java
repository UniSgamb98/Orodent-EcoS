package com.orodent.ecos.ton.features.marking.view;

import com.orodent.ecos.database.CubatureRuleVersions.Version;
import com.orodent.ecos.ton.features.marking.model.MarkingTemplates.*;
import com.orodent.ecos.ton.features.technical.view.*;

import javafx.beans.property.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

public final class MarkingTemplatesView extends StackPane {
    private static final String ID = "ton-marking";
    public final TonListView<Entry> listing =
            new TonListView<>(
                    ID,
                    "Modelli versionati della scritta sul dorso dei dischi.",
                    "Cerca codice o nome",
                    "Nuovo modello");
    public final CheckBox archived = new CheckBox("Mostra anche archiviati");
    private final MarkingTemplateEditor editor = new MarkingTemplateEditor();
    public final Button back = editor.back(),
            reload = editor.reload(),
            cancel = editor.cancel,
            save = editor.save,
            approve = editor.approve,
            copy = editor.copy;
    public final TechnicalRecordsView<Snapshot> versions = editor.versions;
    public final MarkingContentPane content = editor.content;
    private final StringProperty subpage = new SimpleStringProperty("");
    private boolean editing;

    public MarkingTemplatesView() {
        setId(ID + "-view");
        setMinSize(0, 0);
        archived.setId(ID + "-archived");
        listing.options(archived);
        TechnicalUi.column(listing.table(), "Codice", 160, Entry::code);
        TechnicalUi.column(listing.table(), "Nome", 300, e -> e.latest().name());
        TechnicalUi.column(listing.table(), "Versione", 140, e -> e.latest().label());
        TechnicalUi.column(listing.table(), "Stato", 120, e -> e.latest().stateLabel());
        TechnicalUi.column(
                listing.table(),
                "Disponibilità",
                120,
                e -> e.latest().active() ? "Attivo" : "Archiviato");
        listing.table()
                .setPlaceholder(new Label("Nessun modello. Usa Nuovo modello per iniziare."));
        editor.headerTitleProperty()
                .addListener(
                        (o, a, b) -> {
                            if (editing) subpage.set(b);
                        });
        getChildren().addAll(listing, editor);
        showList();
    }

    public ReadOnlyStringProperty subpageProperty() {
        return subpage;
    }

    public void showList() {
        editing = false;
        visible(listing, true);
        visible(editor, false);
        subpage.set("");
    }

    public void showDetail(Detail detail) {
        editing = true;
        editor.show(detail, detail == null ? List.of() : detail.rules());
        visible(listing, false);
        visible(editor, true);
        subpage.set(editor.headerTitleProperty().get());
    }

    public void showNew(List<Version> rules) {
        showDetail(null);
        editor.show(null, rules);
    }

    public Draft draft() {
        return editor.draft();
    }

    public List<String> fingerprint() {
        return editor.fingerprint();
    }

    public boolean editing() {
        return editing;
    }

    public void busy(boolean busy) {
        setDisable(busy);
    }

    public void feedback(String message, boolean error) {
        editor.feedback(message, error);
    }

    private static void visible(javafx.scene.Node node, boolean show) {
        node.setVisible(show);
        node.setManaged(show);
    }

    public void dispose() {
        listing.dispose();
        editor.dispose();
    }
}

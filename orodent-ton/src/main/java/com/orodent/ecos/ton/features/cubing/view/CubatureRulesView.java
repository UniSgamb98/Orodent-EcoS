package com.orodent.ecos.ton.features.cubing.view;

import com.orodent.ecos.database.CubatureRuleCatalogue.*;
import com.orodent.ecos.database.CubatureRuleVersions.*;
import com.orodent.ecos.ton.features.technical.view.*;

import javafx.beans.property.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

public final class CubatureRulesView extends StackPane {
    private static final String ID = "ton-cubature-rules";
    public final TonListView<Entry> listing =
            new TonListView<>(
                    ID,
                    "Regole di cubatura per le specifiche di produzione dei prodotti.",
                    "Cerca codice o nome",
                    "Nuova regola");
    public final CheckBox archived = new CheckBox("Mostra anche archiviati");
    private final CubatureRuleEditor editor = new CubatureRuleEditor();
    public final Button back = editor.back(),
            reload = editor.reload(),
            cancel = editor.cancel,
            save = editor.save,
            approve = editor.approve,
            copy = editor.copy;
    public final TechnicalRecordsView<Version> versions = editor.versions;
    public final CubatureFormulaPane formulas = editor.formulas;
    private final StringProperty subpage = new SimpleStringProperty("");
    private boolean editing;

    public CubatureRulesView() {
        setId(ID + "-view");
        setMinSize(0, 0);
        archived.setId(ID + "-archived");
        listing.options(archived);
        TechnicalUi.column(listing.table(), "Codice", 160, Entry::code);
        TechnicalUi.column(listing.table(), "Nome", 300, Entry::name);
        TechnicalUi.column(
                listing.table(), "Stato", 120, e -> e.active() ? "Attiva" : "Archiviata");
        listing.table().setPlaceholder(new Label("Nessuna regola. Usa Nuova regola per iniziare."));
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
        editor.show(detail);
        visible(listing, false);
        visible(editor, true);
        subpage.set(editor.headerTitleProperty().get());
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

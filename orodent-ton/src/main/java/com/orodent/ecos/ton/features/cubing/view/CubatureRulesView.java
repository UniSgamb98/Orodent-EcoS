package com.orodent.ecos.ton.features.cubing.view;

import com.orodent.ecos.database.CubatureRuleCatalogue.*;
import com.orodent.ecos.ton.features.technical.view.*;

import javafx.beans.property.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/** Cubature rule catalogue using the common TON list and editor. */
public final class CubatureRulesView extends StackPane {
    private static final String ID = "ton-cubature-rules";
    public final TonListView<Entry> listing;
    public final CheckBox archived = new CheckBox("Mostra anche archiviati");
    public final Button back, reload, cancel, save;
    public final TextField code, name;
    public final TextArea description = new TextArea();
    public final CheckBox active = new CheckBox("Regola attiva");
    private final VBox editor;
    private final Label feedback = TechnicalUi.label("", "muted-label"),
            status = TechnicalUi.label("", "muted-label");
    private final StringProperty subpage = new SimpleStringProperty("");
    private boolean editing;

    public CubatureRulesView() {
        setId(ID + "-view");
        setMinSize(0, 0);
        listing =
                new TonListView<>(
                        ID,
                        "Regole di cubatura per le specifiche di produzione dei prodotti.",
                        "Cerca codice o nome",
                        "Nuova regola");
        archived.setId(ID + "-archived");
        listing.options(archived);
        TechnicalUi.column(listing.table(), "Codice", 160, Entry::code);
        TechnicalUi.column(listing.table(), "Nome", 300, Entry::name);
        TechnicalUi.column(
                listing.table(), "Stato", 120, e -> e.active() ? "Attiva" : "Archiviata");
        listing.table().setPlaceholder(new Label("Nessuna regola. Usa Nuova regola per iniziare."));
        back = TechnicalUi.button("← Torna alle regole di cubatura", ID + "-back", false);
        reload = TechnicalUi.button("Aggiorna dati", ID + "-reload", false);
        cancel = TechnicalUi.button("Annulla modifiche", ID + "-cancel", false);
        save = TechnicalUi.button("Salva regola", ID + "-save", true);
        back.getStyleClass().add("ton-editor-back-link");
        reload.getStyleClass().add("ton-editor-refresh");
        Region toolbarSpace = new Region();
        HBox.setHgrow(toolbarSpace, Priority.ALWAYS);
        HBox toolbar = new HBox(12, back, toolbarSpace, reload);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        code = TechnicalUi.input(ID + "-code", "Codice identificativo");
        name = TechnicalUi.input(ID + "-name", "Nome della regola");
        active.setId(ID + "-active");
        description.setId(ID + "-description");
        description.setWrapText(true);
        description.setPrefRowCount(4);
        feedback.setId(ID + "-feedback-editor");
        GridPane fields = new GridPane();
        fields.setHgap(18);
        fields.setVgap(16);
        fields.setMinWidth(0);
        for (int n = 0; n < 2; n++) {
            var col = new ColumnConstraints();
            col.setPercentWidth(50);
            col.setHgrow(Priority.ALWAYS);
            fields.getColumnConstraints().add(col);
        }
        fields.add(TechnicalUi.field("Codice *", code), 0, 0);
        fields.add(TechnicalUi.field("Nome *", name), 1, 0);
        fields.add(TechnicalUi.field("Descrizione della regola", description), 0, 1, 2, 1);
        fields.add(active, 0, 2, 2, 1);
        VBox card =
                new VBox(
                        18,
                        TechnicalUi.label("Regola di cubatura", "ton-technical-panel-title"),
                        fields);
        card.getStyleClass().add("ton-editor-status");
        VBox.setVgrow(card, Priority.ALWAYS);
        status.getStyleClass().add("ton-editor-status");
        FlowPane actions = new FlowPane(10, 8, cancel, save);
        actions.getStyleClass().add("ton-editor-actions");
        editor = new VBox(12, toolbar, status, card, actions, feedback);
        editor.setId(ID + "-editor");
        editor.setMinSize(0, 0);
        editor.getStyleClass().add("ton-technical-detail");
        editor.getStylesheets()
                .add(
                        Objects.requireNonNull(
                                        getClass().getResource("/ton/css/technical-editor.css"))
                                .toExternalForm());
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
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

    public void showEntry(Entry entry) {
        editing = true;
        code.setText(entry == null ? "" : entry.code());
        name.setText(entry == null ? "" : entry.name());
        description.setText(entry == null ? "" : entry.description());
        code.setDisable(entry != null && entry.used());
        code.setTooltip(
                new Tooltip(
                        "Il codice identifica la regola nelle specifiche e resta fisso quando è"
                                + " utilizzato."));
        active.setSelected(entry == null || entry.active());
        status.setText(
                entry == null
                        ? "Nuova regola · non ancora salvata."
                        : "Revisione "
                                + entry.revision()
                                + " · "
                                + (entry.active() ? "Attiva" : "Archiviata"));
        reload.setDisable(entry == null);
        feedback("", false);
        visible(listing, false);
        visible(editor, true);
        subpage.set(entry == null ? "Nuova regola" : entry.code() + " · " + entry.name());
    }

    public Draft draft() {
        return new Draft(
                code.getText(), name.getText(), description.getText(), active.isSelected());
    }

    public List<String> fingerprint() {
        return List.of(
                code.getText(), name.getText(), description.getText(), "" + active.isSelected());
    }

    public boolean editing() {
        return editing;
    }

    public void busy(boolean busy) {
        setDisable(busy);
    }

    public void feedback(String message, boolean error) {
        feedback.setText(message);
        feedback.getStyleClass().remove("ton-error-feedback");
        if (error) feedback.getStyleClass().add("ton-error-feedback");
    }

    private static void visible(javafx.scene.Node node, boolean show) {
        node.setVisible(show);
        node.setManaged(show);
    }

    public void dispose() {
        listing.dispose();
    }
}

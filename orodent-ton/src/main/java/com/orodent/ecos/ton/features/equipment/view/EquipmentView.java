package com.orodent.ecos.ton.features.equipment.view;

import static com.orodent.ecos.ton.features.equipment.model.Equipment.*;

import com.orodent.ecos.ton.features.technical.view.*;

import javafx.beans.property.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/** Equipment uses the common TON list; the editable identity form can evolve independently. */
public final class EquipmentView extends StackPane {
    public final Kind kind;
    public final TonListView<Entry> listing;
    public final CheckBox archived = new CheckBox("Mostra anche archiviati");
    public final Button back, reload, cancel, save;
    public final TextField code, name, dimension;
    public final TextArea notes = new TextArea();
    public final CheckBox active = new CheckBox("Anagrafica attiva");
    private final VBox editor;
    private final Label feedback = TechnicalUi.label("", "muted-label"),
            status = TechnicalUi.label("", "muted-label");
    private final StringProperty subpage = new SimpleStringProperty("");
    private boolean editing;

    public EquipmentView(Kind kind) {
        this.kind = kind;
        setId(kind.id() + "-view");
        setMinSize(0, 0);
        listing =
                new TonListView<>(
                        kind.id(), kind.description(), "Cerca codice o nome", kind.create());
        archived.setId(kind.id() + "-archived");
        listing.options(archived);
        TechnicalUi.column(listing.table(), "Codice", 160, Entry::code);
        TechnicalUi.column(listing.table(), "Nome", 300, Entry::name);
        if (kind.measure() != null)
            TechnicalUi.column(
                    listing.table(), kind.dimension(), 160, e -> TechnicalUi.number(e.dimension()));
        TechnicalUi.column(
                listing.table(), "Stato", 120, e -> e.active() ? "Attivo" : "Archiviato");
        listing.table()
                .setPlaceholder(
                        new Label("Nessun elemento. Usa " + kind.create() + " per iniziare."));
        back = TechnicalUi.button(kind.back(), kind.id() + "-back", false);
        reload = TechnicalUi.button("Aggiorna dati", kind.id() + "-reload", false);
        cancel = TechnicalUi.button("Annulla modifiche", kind.id() + "-cancel", false);
        save = TechnicalUi.button("Salva anagrafica", kind.id() + "-save", true);
        back.getStyleClass().add("ton-editor-back-link");
        reload.getStyleClass().add("ton-editor-refresh");
        Region toolbarSpace = new Region();
        HBox.setHgrow(toolbarSpace, Priority.ALWAYS);
        HBox toolbar = new HBox(12, back, toolbarSpace, reload);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        code = TechnicalUi.input(kind.id() + "-code", "Codice identificativo");
        name = TechnicalUi.input(kind.id() + "-name", "Nome del macchinario o dell’attrezzatura");
        dimension = TechnicalUi.input(kind.id() + "-dimension", "Misura in millimetri");
        notes.setId(kind.id() + "-notes");
        notes.setPrefRowCount(4);
        notes.setWrapText(true);
        active.setId(kind.id() + "-active");
        feedback.setId(kind.id() + "-feedback-editor");
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
        int row = 1;
        if (kind.measure() != null)
            fields.add(TechnicalUi.field(kind.dimension() + " *", dimension), 0, row++);
        fields.add(TechnicalUi.field("Note", notes), 0, row++, 2, 1);
        fields.add(active, 0, row, 2, 1);
        VBox card =
                new VBox(18, TechnicalUi.label("Anagrafica", "ton-technical-panel-title"), fields);
        card.getStyleClass().add("ton-editor-status");
        VBox.setVgrow(card, Priority.ALWAYS);
        status.getStyleClass().add("ton-editor-status");
        FlowPane actions = new FlowPane(10, 8, cancel, save);
        actions.getStyleClass().add("ton-editor-actions");
        editor = new VBox(12, toolbar, status, card, actions, feedback);
        editor.setId(kind.id() + "-editor");
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
        dimension.setText(entry == null ? "" : TechnicalUi.number(entry.dimension()));
        notes.setText(entry == null ? "" : entry.notes());
        active.setSelected(entry == null || entry.active());
        status.setText(
                entry == null
                        ? "Nuova anagrafica · non ancora salvata."
                        : "Revisione "
                                + entry.revision()
                                + " · "
                                + (entry.active() ? "Attivo" : "Archiviato"));
        reload.setDisable(entry == null);
        feedback("", false);
        visible(listing, false);
        visible(editor, true);
        subpage.set(entry == null ? kind.create() : entry.code() + " · " + entry.name());
    }

    public Draft draft() {
        return new Draft(
                code.getText(),
                name.getText(),
                kind.measure() == null
                        ? null
                        : TechnicalUi.decimal(dimension.getText(), kind.dimension()),
                notes.getText(),
                active.isSelected());
    }

    public List<String> fingerprint() {
        return List.of(
                code.getText(),
                name.getText(),
                dimension.getText(),
                notes.getText(),
                "" + active.isSelected());
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

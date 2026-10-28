package com.orodent.ecos.ton.features.powders.view;

import com.orodent.ecos.ton.features.powders.model.PowderModel.*;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class PowderEditorView extends VBox {
    private final Button back = button("← Elenco polveri", "ton-powders-back", "secondary-button");
    private final Button reload =
            button("Ricarica scheda", "ton-powder-reload", "secondary-button");
    private final Button newVersion =
            button("Crea nuova versione", "ton-powder-new-version", "secondary-button");
    private final Button retire =
            button("Ritira versione", "ton-powder-retire", "secondary-button");
    private final Button save = button("Salva bozza", "ton-powder-save", "primary-button");
    private final Button approve =
            button("Salva e approva", "ton-powder-approve", "primary-button");
    private final Button identitySave =
            button("Salva anagrafica", "ton-powder-save-identity", "primary-button");
    private final Button cancel =
            button("Annulla modifiche", "ton-powder-cancel", "secondary-button");
    private final Label heading = new Label();
    private final Label status = new Label();
    private final Label feedback = new Label();
    private final ComboBox<Revision> versions = new ComboBox<>();
    private final TextField code = field("ton-powder-code", "Es. P001");
    private final TextField name = field("ton-powder-name", "Nome della polvere");
    private final ComboBox<PowderColor> color = new ComboBox<>();
    private final CheckBox active = new CheckBox("Polvere attiva");
    private final TextField strength = field("ton-powder-strength", "Facoltativa");
    private final TextField translucency = field("ton-powder-translucency", "Facoltativa");
    private final ComboBox<String> yttria = new ComboBox<>();
    private final TextArea notes = new TextArea();
    private final OxidesEditor oxides = new OxidesEditor();
    private final TabPane tabs = new TabPane();
    private final TableView<Revision> history = new TableView<>();
    private final VBox technical;
    private boolean updating;

    public PowderEditorView() {
        super(10);
        setId("ton-powder-editor");
        getStyleClass().add("ton-powder-page");
        setMinWidth(0);
        heading.setId("ton-powder-heading");
        heading.getStyleClass().add("ton-page-heading");
        heading.setWrapText(true);
        status.setId("ton-powder-state");
        status.getStyleClass().add("ton-area-status");
        status.setWrapText(true);
        active.setId("ton-powder-active");
        color.setId("ton-powder-color");
        color.getItems().setAll(PowderColor.values());
        color.setPromptText("Seleziona il colore");
        color.setAccessibleText("Colore della polvere");
        color.setMaxWidth(Double.MAX_VALUE);
        versions.setId("ton-powder-version");
        versions.setPrefWidth(190);
        yttria.setId("ton-powder-class");
        yttria.setEditable(true);
        yttria.getItems().setAll("3Y", "4Y", "5Y", "6Y");
        yttria.setMaxWidth(Double.MAX_VALUE);
        yttria.getEditor().setPromptText("Facoltativa, es. 6Y");
        yttria.getEditor().setAccessibleText("Classe Y, indipendente dalla percentuale di Y2O3");
        notes.setId("ton-powder-notes");
        notes.setWrapText(true);
        notes.setPrefRowCount(4);
        Label generalHint =
                hint(
                        "Codice, nome e stato della polvere sono comuni a tutte le versioni della"
                                + " scheda tecnica.");
        VBox identity =
                new VBox(
                        12,
                        generalHint,
                        form("Codice *", code),
                        form("Nome *", name),
                        form("Colore *", color),
                        hint(
                                "Ordine dell'elenco: bianche, gialle, rosa, grigie. A parità di"
                                    + " colore, per codice."),
                        active);
        identity.getStyleClass().add("ton-powder-tab");
        technical =
                new VBox(
                        12,
                        hint(
                                "La classe 3Y / 4Y / 5Y / 6Y è distinta dalla percentuale in peso"
                                        + " di Y₂O₃. Puoi specificare anche altre classi Y."),
                        form("Classe Y", yttria),
                        form("Resistenza · MPa", strength),
                        form("Traslucenza · valore dichiarato", translucency),
                        hint(
                                "Se indichi la traslucenza, riporta nelle note il metodo e la scala"
                                        + " del produttore prima di approvare."),
                        form("Note della versione", notes));
        technical.getStyleClass().add("ton-powder-tab");
        oxides.getStyleClass().add("ton-powder-tab");
        history.setId("ton-powder-history");
        history.setMinHeight(200);
        history.setPrefHeight(280);
        history.getColumns()
                .setAll(
                        List.of(
                                historyColumn("Versione", r -> "v" + r.number()),
                                historyColumn("Stato", r -> r.state().label()),
                                historyColumn(
                                        "Creata",
                                        r ->
                                                r.createdAt()
                                                        .format(
                                                                DateTimeFormatter.ofPattern(
                                                                        "dd/MM/yyyy HH:mm"))),
                                historyColumn(
                                        "Approvata",
                                        r ->
                                                r.approvedAt() == null
                                                        ? "—"
                                                        : r.approvedAt()
                                                                .format(
                                                                        DateTimeFormatter.ofPattern(
                                                                                "dd/MM/yyyy"
                                                                                    + " HH:mm")))));
        history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Button openHistory =
                button("Apri versione selezionata", "ton-powder-open-version", "secondary-button");
        openHistory.setOnAction(
                event -> {
                    if (history.getSelectionModel().getSelectedItem() != null) {
                        versions.setValue(history.getSelectionModel().getSelectedItem());
                        tabs.getSelectionModel().select(1);
                    }
                });
        VBox historyPane =
                new VBox(
                        12,
                        hint(
                                "Le schede approvate non vengono riscritte. Le nuove versioni"
                                        + " conservano lo storico e i riferimenti delle ricette"
                                        + " precedenti."),
                        history,
                        openHistory);
        historyPane.getStyleClass().add("ton-powder-tab");
        tabs.setId("ton-powder-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs()
                .setAll(
                        tab("Anagrafica", identity),
                        tab("Dati tecnici", technical),
                        tab("Ossidi", oxides),
                        tab("Versioni", historyPane));
        tabs.setMinHeight(240);
        tabs.setPrefHeight(380);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        feedback.setId("ton-powder-feedback");
        feedback.setWrapText(true);
        feedback.setMinHeight(Region.USE_PREF_SIZE);
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        FlowPane actions = new FlowPane(10, 8, cancel, save, identitySave, approve, retire);
        HBox top = new HBox(12, back, reload);
        top.setAlignment(Pos.CENTER_LEFT);
        FlowPane versionRow =
                new FlowPane(12, 8, new HBox(8, new Label("Scheda tecnica"), versions), newVersion);
        getChildren().setAll(top, heading, versionRow, status, tabs, actions, feedback);
    }

    public void show(Detail detail, Revision revision) {
        updating = true;
        try {
            var powder = detail == null ? null : detail.powder();
            heading.setText(
                    powder == null ? "Nuova polvere" : powder.code() + " · " + powder.name());
            versions.getItems().setAll(detail == null ? List.of() : detail.revisions());
            versions.setValue(revision);
            versions.setPromptText("v1 · Nuova bozza");
            versions.setDisable(detail == null);
            code.setText(powder == null ? "" : powder.code());
            name.setText(powder == null ? "" : powder.name());
            color.setValue(powder == null ? null : powder.color());
            active.setSelected(powder == null || powder.active());
            var data =
                    revision == null
                            ? new TechnicalDraft(null, null, null, "", List.of())
                            : revision.technical();
            yttria.setValue(data.yttriaClass() == null ? "" : data.yttriaClass() + "Y");
            yttria.getEditor().setText(yttria.getValue());
            strength.setText(PowderFormats.number(data.strength()));
            translucency.setText(PowderFormats.number(data.translucency()));
            notes.setText(data.notes());
            boolean locked = revision != null && revision.state() != State.BOZZA;
            technical.setDisable(locked);
            oxides.show(data.oxides(), locked);
            history.getItems().setAll(detail == null ? List.of() : detail.revisions());
            state(locked, detail, revision);
            feedback.setText("");
        } finally {
            updating = false;
        }
    }

    private void state(boolean locked, Detail detail, Revision revision) {
        status.setText(
                revision == null
                        ? "Nuova bozza · i dati non sono ancora salvati."
                        : revision.state() == State.BOZZA
                                ? "Bozza v"
                                        + revision.number()
                                        + " · modificabile. La scheda approvata precedente resta"
                                        + " disponibile."
                                : revision
                                        + " · caratteristiche e ossidi in sola lettura."
                                        + " L'anagrafica resta modificabile.");
        visible(save, !locked);
        visible(approve, !locked);
        visible(identitySave, locked);
        visible(retire, revision != null && revision.state() == State.APPROVATA);
        visible(newVersion, locked);
        newVersion.setDisable(detail != null && detail.hasDraft());
        newVersion.setTooltip(
                new Tooltip(
                        "Copia questa scheda in una nuova bozza. Se esiste già una bozza, aprila"
                                + " dal selettore delle versioni."));
        reload.setDisable(detail == null);
    }

    public IdentityDraft identity() {
        var identity =
                new IdentityDraft(
                        code.getText(), name.getText(), color.getValue(), active.isSelected());
        identity.requireColor();
        return identity;
    }

    public TechnicalDraft technical() {
        return new TechnicalDraft(
                PowderFormats.decimal(strength.getText(), "Resistenza"),
                PowderFormats.decimal(translucency.getText(), "Traslucenza"),
                PowderFormats.yttria(yttria.getEditor().getText()),
                notes.getText(),
                oxides.values());
    }

    public List<String> fingerprint() {
        var values =
                new ArrayList<>(
                        List.of(
                                code.getText().strip(),
                                name.getText().strip(),
                                color.getValue() == null ? "" : color.getValue().name(),
                                Boolean.toString(active.isSelected()),
                                strength.getText().strip(),
                                translucency.getText().strip(),
                                yttria.getEditor().getText().strip(),
                                notes.getText().strip()));
        values.addAll(oxides.fingerprint());
        return List.copyOf(values);
    }

    public void feedback(String message, boolean error) {
        feedback.setText(message);
        feedback.getStyleClass().remove("ton-error-feedback");
        if (error) feedback.getStyleClass().add("ton-error-feedback");
    }

    public void saving(boolean value) {
        setDisable(value);
        save.setText(value ? "Salvataggio…" : "Salva bozza");
    }

    public boolean updating() {
        return updating;
    }

    public void selectVersion(Revision value) {
        updating = true;
        try {
            versions.setValue(value);
        } finally {
            updating = false;
        }
    }

    public Button back() {
        return back;
    }

    public Button reload() {
        return reload;
    }

    public Button save() {
        return save;
    }

    public Button approve() {
        return approve;
    }

    public Button identitySave() {
        return identitySave;
    }

    public Button cancel() {
        return cancel;
    }

    public Button newVersion() {
        return newVersion;
    }

    public Button retire() {
        return retire;
    }

    public ComboBox<Revision> versions() {
        return versions;
    }

    public void newEditor() {
        show(null, null);
        tabs.getSelectionModel().selectFirst();
    }

    private static void visible(javafx.scene.Node node, boolean value) {
        node.setVisible(value);
        node.setManaged(value);
    }

    private static Tab tab(String title, javafx.scene.Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        return new Tab(title, scroll);
    }

    private static VBox form(String label, javafx.scene.Node node) {
        return new VBox(5, new Label(label), node);
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.getStyleClass().add("muted-label");
        return label;
    }

    private static TextField field(String id, String prompt) {
        TextField field = new TextField();
        field.setId(id);
        field.setPromptText(prompt);
        field.setAccessibleText(prompt);
        return field;
    }

    private static Button button(String title, String id, String style) {
        return PowderListView.button(title, id, style);
    }

    private static TableColumn<Revision, String> historyColumn(
            String title, java.util.function.Function<Revision, String> value) {
        TableColumn<Revision, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }
}

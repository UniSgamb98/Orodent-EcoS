package com.orodent.ecos.ton.features.powders.view;

import com.orodent.ecos.ton.features.powders.model.PowderModel.*;
import com.orodent.ecos.ton.features.technical.view.TechnicalDetailView;
import com.orodent.ecos.ton.features.technical.view.TechnicalRecordsView;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class PowderEditorView extends TechnicalDetailView {
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
    private final Label status = new Label();
    private final ObjectProperty<Revision> selectedVersion = new SimpleObjectProperty<>();
    private final TextField code = field("ton-powder-code", "Es. P001");
    private final TextField name = field("ton-powder-name", "Nome della polvere");
    private final TextField supplier =
            field("ton-powder-supplier", "Nome dell’azienda, facoltativo");
    private final ComboBox<PowderColor> color = new ComboBox<>();
    private final CheckBox active = new CheckBox("Polvere attiva");
    private final TextField strength = field("ton-powder-strength", "Facoltativa");
    private final TextField translucency = field("ton-powder-translucency", "Facoltativa");
    private final ComboBox<String> yttria = new ComboBox<>();
    private final TextArea notes = new TextArea();
    private final OxidesEditor oxides = new OxidesEditor();
    private final TechnicalRecordsView<Revision> history =
            new TechnicalRecordsView<>(
                    "ton-powder-versions",
                    "Versioni della scheda polvere",
                    "Le versioni approvate conservano le caratteristiche e i riferimenti delle"
                        + " ricette precedenti.");
    private final TechnicalRecordsView<Usage> usages =
            new TechnicalRecordsView<>(
                    "ton-powder-usages",
                    "Prodotti che utilizzano questa polvere",
                    "Prodotti collegati alla polvere tramite le loro composizioni, comprese quelle"
                        + " storiche.");
    private final Tab usagesSheet;
    private final VBox technical;
    private boolean updating;

    public PowderEditorView() {
        super("ton-powder", "← Torna alle polveri");
        back.setId("ton-powders-back");
        status.setId("ton-powder-state");
        status.setWrapText(true);
        active.setId("ton-powder-active");
        color.setId("ton-powder-color");
        color.getItems().setAll(PowderColor.values());
        color.setPromptText("Seleziona il colore");
        color.setAccessibleText("Colore della polvere");
        color.setMaxWidth(Double.MAX_VALUE);
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
                        form("Fornitore", supplier),
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
        history.table().setId("ton-powder-history");
        history.table()
                .getColumns()
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
        TechnicalUi.column(usages.table(), "Prodotto", 160, Usage::productCode);
        TechnicalUi.column(usages.table(), "Descrizione", 180, Usage::productName);
        TechnicalUi.column(
                usages.table(), "Linea", 120, u -> u.lineCode() == null ? "—" : u.lineCode());
        TechnicalUi.column(
                usages.table(),
                "Colore",
                100,
                u ->
                        com.orodent.ecos.ton.features.catalog.model.CatalogModels.colorLabelOf(
                                u.color()));
        TechnicalUi.column(usages.table(), "Composizioni", 100, u -> "" + u.compositions());
        TechnicalUi.column(usages.table(), "Stato", 100, u -> u.active() ? "Attivo" : "Archiviato");
        addSheet("Anagrafica", identity, true);
        addSheet("Dati tecnici", technical, true);
        addSheet("Ossidi", oxides, true);
        usagesSheet = addSheet("Utilizzi", usages, true);
        addSheet("Versioni", history, true);
        status(true, status);
        addStatusAction(newVersion);
        actions(cancel, save, identitySave, approve, retire);
    }

    public void show(Detail detail, Revision revision) {
        updating = true;
        try {
            var powder = detail == null ? null : detail.powder();
            headerTitle.set(powder == null ? "Nuova polvere" : powder.code());
            selectedVersion.set(revision);
            code.setText(powder == null ? "" : powder.code());
            name.setText(powder == null ? "" : powder.name());
            supplier.setText(powder == null ? "" : powder.supplier());
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
            if (detail == null) history.reset(false);
            else history.show(detail.revisions());
            usages.reset(detail != null);
            state(locked, detail, revision);
            feedback.setText("");
        } finally {
            updating = false;
        }
    }

    private void state(boolean locked, Detail detail, Revision revision) {
        String sheet = "Scheda tecnica" + (detail == null ? "" : " " + detail.powder().code());
        status.setText(
                revision == null
                        ? sheet + " · nuova bozza: i dati non sono ancora salvati."
                        : sheet
                                + " · "
                                + revision
                                + ": "
                                + (revision.state() == State.BOZZA
                                        ? "modificabile. La scheda approvata precedente resta"
                                                + " disponibile."
                                        : "caratteristiche e ossidi in sola lettura. L'anagrafica"
                                                + " resta modificabile."));
        visible(save, !locked);
        visible(approve, !locked);
        visible(identitySave, locked);
        visible(retire, revision != null && revision.state() == State.APPROVATA);
        visible(newVersion, locked);
        newVersion.setDisable(detail != null && detail.hasDraft());
        newVersion.setTooltip(
                new Tooltip(
                        "Copia questa scheda in una nuova bozza. Se esiste già una bozza, aprila"
                                + " dalla scheda Versioni."));
        reload.setDisable(detail == null);
    }

    public IdentityDraft identity() {
        var identity =
                new IdentityDraft(
                        code.getText(),
                        name.getText(),
                        color.getValue(),
                        active.isSelected(),
                        supplier.getText());
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
                                supplier.getText().strip(),
                                color.getValue() == null ? "" : color.getValue().name(),
                                Boolean.toString(active.isSelected()),
                                strength.getText().strip(),
                                translucency.getText().strip(),
                                yttria.getEditor().getText().strip(),
                                notes.getText().strip()));
        values.addAll(oxides.fingerprint());
        return List.copyOf(values);
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
            selectedVersion.set(value);
        } finally {
            updating = false;
        }
    }

    public TechnicalRecordsView<Revision> versions() {
        return history;
    }

    public TechnicalRecordsView<Usage> usages() {
        return usages;
    }

    public boolean showingUsages() {
        return tabs.getSelectionModel().getSelectedItem() == usagesSheet;
    }

    public void showTechnical() {
        tabs.getSelectionModel().select(1);
    }

    public void dispose() {
        history.dispose();
        usages.dispose();
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

    public ObjectProperty<Revision> selectedVersionProperty() {
        return selectedVersion;
    }

    public ReadOnlyStringProperty headerCodeProperty() {
        return headerTitleProperty();
    }

    public void newEditor() {
        show(null, null);
        tabs.getSelectionModel().selectFirst();
    }

    private static void visible(javafx.scene.Node node, boolean value) {
        node.setVisible(value);
        node.setManaged(value);
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

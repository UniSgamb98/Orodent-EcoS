package com.orodent.ecos.ton.features.diskmodels.view;

import static com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import com.orodent.ecos.ton.features.technical.view.TechnicalDetailView;
import com.orodent.ecos.ton.features.technical.view.TechnicalRecordsView;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class DiskModelEditorView extends TechnicalDetailView {
    public enum Sheet {
        PARAMETERS("Parametri"),
        LAYERS("Strati"),
        RANGES("Sovramateriali"),
        MOULDS("Stampi ammissibili"),
        USAGES("Utilizzi"),
        VERSIONS("Versioni");
        private final String title;

        Sheet(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    private final TextField code = TechnicalUi.input("ton-model-code", "Codice modello"),
            pressure = TechnicalUi.input("ton-model-pressure", "Pressione"),
            top = TechnicalUi.input("ton-model-top", "Sovramateriale superiore"),
            bottom = TechnicalUi.input("ton-model-bottom", "Sovramateriale inferiore");
    private final TextArea notes = new TextArea();
    private final Label status = TechnicalUi.label("", "muted-label"),
            readonly = TechnicalUi.label("", "muted-label");
    private final Map<Tab, Sheet> sheets = new HashMap<>();
    private final EnumMap<Sheet, GridPane> grids = new EnumMap<>(Sheet.class);
    private final DiskModelPreview preview = new DiskModelPreview();
    private final ModelDiametersEditor diameters = new ModelDiametersEditor(this::refreshPreview);
    private final LayersEditor layers = new LayersEditor(this::refreshPreview);
    private final RangesEditor ranges = new RangesEditor(this::refreshPreview);
    private final TechnicalRecordsView<Usage> usages =
            new TechnicalRecordsView<>(
                    "ton-model-usages",
                    "Prodotti che utilizzano questo modello",
                    "Le specifiche dei prodotti conservano questa versione precisa del modello."
                        + " Approvare una nuova versione non aggiorna quelle esistenti.");
    private final TechnicalRecordsView<Version> history =
            new TechnicalRecordsView<>(
                    "ton-model-versions",
                    "Versioni del modello",
                    "Le versioni approvate e ritirate conservano parametri, strati e sovramateriali"
                        + " in sola lettura.");
    private final Button
            cancel = TechnicalUi.button("Annulla modifiche", "ton-model-cancel", false),
            save = TechnicalUi.button("Salva bozza", "ton-model-save", true),
            approve = TechnicalUi.button("Salva e approva", "ton-model-approve", true),
            copy = TechnicalUi.button("Crea nuova versione", "ton-model-copy", false),
            retire = TechnicalUi.button("Ritira versione", "ton-model-retire", false),
            delete = TechnicalUi.button("Elimina bozza", "ton-model-delete", false);
    private final List<Node> editable = new ArrayList<>();
    private boolean updating;

    DiskModelEditorView() {
        super("ton-model", "← Torna ai modelli");
        status.setId("ton-model-status");
        readonly.setId("ton-model-readonly");
        notes.setId("ton-model-notes");
        notes.setWrapText(true);
        notes.setPrefRowCount(3);
        var parameters =
                TechnicalUi.panel(
                        "Identità e pressatura",
                        "Il modello può essere riutilizzato per più prodotti e altezze.",
                        TechnicalUi.fields(
                                TechnicalUi.field("Codice modello *", code),
                                TechnicalUi.field("Pressione · kg/cm² *", pressure)),
                        diameters,
                        TechnicalUi.field("Note tecniche", notes),
                        TechnicalUi.label(
                                "Ogni diametro articolo ha stampo di riferimento, g/mm e stampi"
                                    + " ammissibili propri. Pressione, strati e sovramateriali sono"
                                    + " comuni a tutti i diametri del modello.",
                                "muted-label"));
        var layerPanel =
                TechnicalUi.panel(
                        "Struttura degli strati",
                        "Posizione dall'alto al basso · quote percentuali dell'altezza.",
                        layers,
                        TechnicalUi.label(
                                "I colori della sezione sono illustrativi. Le polveri verranno"
                                        + " definite nella composizione.",
                                "muted-label"));
        var defaults =
                TechnicalUi.panel(
                        "Sovramateriali predefiniti",
                        "Usati quando l'altezza dell'articolo non rientra in una fascia specifica.",
                        TechnicalUi.fields(
                                TechnicalUi.field("Superiore · mm *", top),
                                TechnicalUi.field("Inferiore · mm *", bottom)));
        var rangePanel =
                new VBox(
                        18,
                        defaults,
                        TechnicalUi.panel(
                                "Eccezioni per altezza",
                                "Facoltative · limiti iniziali inclusi, limiti finali esclusi."
                                        + " Nessuna sovrapposizione.",
                                ranges));
        tabWithPreview(Sheet.PARAMETERS, parameters);
        tabWithPreview(Sheet.LAYERS, layerPanel);
        tabWithPreview(Sheet.RANGES, rangePanel);
        tab(Sheet.MOULDS, diameters.comparisons());
        TechnicalUi.column(usages.table(), "Prodotto", 180, Usage::productCode);
        TechnicalUi.column(usages.table(), "Descrizione", 180, Usage::productName);
        TechnicalUi.column(
                usages.table(),
                "Specifica",
                170,
                u ->
                        "v"
                                + u.specification()
                                + " · "
                                + u.state()
                                + (u.preferred() ? " · predefinita" : ""));
        tab(Sheet.USAGES, usages);
        TechnicalUi.column(history.table(), "Versione", 100, v -> "v" + v.number());
        TechnicalUi.column(history.table(), "Stato", 130, v -> v.state().toString());
        TechnicalUi.column(
                history.table(),
                "Ultimo aggiornamento",
                170,
                v -> v.updated().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        tab(Sheet.VERSIONS, history);
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> movePreview());
        delete.getStyleClass().add("ton-editor-danger");
        retire.getStyleClass().add("ton-editor-danger");
        status(true, status, readonly);
        addStatusAction(copy);
        actions(cancel, save, approve, retire, delete);
        editable.addAll(List.of(pressure, top, bottom, notes, layers, ranges));
        for (TextField field : List.of(top, bottom))
            field.textProperty().addListener((o, a, b) -> refreshPreview());
        movePreview();
    }

    private void tabWithPreview(Sheet sheet, Node left) {
        var grid = new GridPane();
        grid.setMinWidth(0);
        grid.setHgap(20);
        grid.setVgap(18);
        grids.put(sheet, grid);
        grid.getChildren().add(left);
        GridPane.setColumnIndex(left, 0);
        GridPane.setHgrow(left, Priority.ALWAYS);
        Runnable layout =
                () -> {
                    boolean wide = grid.getWidth() >= 760;
                    grid.getColumnConstraints().clear();
                    var first = new ColumnConstraints();
                    first.setHgrow(Priority.ALWAYS);
                    first.setMinWidth(0);
                    if (wide) {
                        first.setPercentWidth(67);
                        var second = new ColumnConstraints();
                        second.setPercentWidth(33);
                        second.setMinWidth(0);
                        grid.getColumnConstraints().addAll(first, second);
                    } else {
                        first.setPercentWidth(100);
                        grid.getColumnConstraints().add(first);
                    }
                    if (preview.getParent() == grid) {
                        GridPane.setColumnIndex(preview, wide ? 1 : 0);
                        GridPane.setRowIndex(preview, wide ? 0 : 1);
                    }
                };
        grid.widthProperty().addListener((o, a, b) -> layout.run());
        grid.getProperties().put("layout", layout);
        layout.run();
        tab(sheet, grid);
    }

    private void tab(Sheet sheet, Node content) {
        var tab =
                addSheet(sheet.title(), content, sheet == Sheet.VERSIONS || sheet == Sheet.USAGES);
        sheets.put(tab, sheet);
    }

    private void movePreview() {
        for (GridPane grid : grids.values()) grid.getChildren().remove(preview);
        GridPane target = grids.get(sheet());
        if (target != null) {
            target.getChildren().add(preview);
            GridPane.setValignment(preview, javafx.geometry.VPos.TOP);
            ((Runnable) target.getProperties().get("layout")).run();
        }
    }

    public void show(Detail detail) {
        updating = true;
        try {
            Revision revision = detail == null ? null : detail.revision();
            Draft data = revision == null ? null : revision.data();
            boolean frozen = revision != null && revision.frozen();
            code.setText(data == null ? "" : data.code());
            code.setDisable(revision != null);
            diameters.show(data, frozen);
            pressure.setText(data == null ? "" : TechnicalUi.number(data.pressure()));
            top.setText(data == null ? "0" : TechnicalUi.number(data.top()));
            bottom.setText(data == null ? "0" : TechnicalUi.number(data.bottom()));
            notes.setText(data == null ? "" : data.notes());
            for (Node n : editable) n.setDisable(frozen);
            layers.show(data == null ? List.of(BigDecimal.valueOf(100)) : data.layers(), frozen);
            ranges.show(data == null ? List.of() : data.ranges(), frozen);
            headerTitle.set(data == null ? "Nuovo modello disco" : data.code());
            status.setText(
                    revision == null
                            ? "Prima versione · non ancora salvata."
                            : "v"
                                    + revision.number()
                                    + " · "
                                    + revision.state()
                                    + (data.diameters().isEmpty()
                                            ? " · Diametro storico stampo "
                                                    + TechnicalUi.number(data.diameter())
                                                    + " mm"
                                            : " · Diametri articolo: "
                                                    + data.diameters().stream()
                                                            .map(
                                                                    g ->
                                                                            TechnicalUi.number(
                                                                                    g
                                                                                            .articleDiameter()))
                                                            .collect(
                                                                    java.util.stream.Collectors
                                                                            .joining(" / "))
                                                    + " mm")
                                    + " · "
                                    + data.layers().size()
                                    + " strati");
            readonly.setText(
                    frozen
                            ? "Versione "
                                    + revision.state().toString().toLowerCase(Locale.ITALIAN)
                                    + ": contenuto in sola lettura. Crea una nuova versione per"
                                    + " modificarlo."
                            : "");
            TechnicalUi.visible(readonly, frozen);
            TechnicalUi.visible(save, !frozen);
            TechnicalUi.visible(approve, !frozen);
            TechnicalUi.visible(copy, frozen);
            TechnicalUi.visible(retire, revision != null && revision.state() == State.APPROVATA);
            TechnicalUi.visible(delete, revision != null && revision.state() == State.BOZZA);
            delete.setDisable(detail == null || !detail.deletable());
            reload.setDisable(detail == null);
            usages.reset(detail != null);
            history.reset(detail != null);
            feedback.setText("");
            refreshPreview();
        } finally {
            updating = false;
        }
    }

    public Draft draft() {
        return new Draft(
                code.getText(),
                TechnicalUi.decimal(pressure.getText(), "Pressione"),
                TechnicalUi.decimal(top.getText(), "Sovramateriale superiore"),
                TechnicalUi.decimal(bottom.getText(), "Sovramateriale inferiore"),
                notes.getText(),
                layers.data(),
                ranges.data(),
                diameters.data());
    }

    public List<String> fingerprint() {
        var result = new ArrayList<String>();
        for (TextField t : List.of(code, pressure, top, bottom)) result.add(t.getText());
        result.add(notes.getText());
        result.addAll(diameters.fingerprint());
        result.add("LAYERS:" + layers.texts().size());
        result.addAll(layers.texts());
        result.add("RANGES:" + ranges.texts().size());
        result.addAll(ranges.texts());
        return List.copyOf(result);
    }

    private void refreshPreview() {
        BigDecimal diameter = diameters.previewDiameter();
        preview.update(
                top.getText(), bottom.getText(), TechnicalUi.number(diameter), layers.texts());
    }

    public void sheet(Sheet sheet) {
        boolean before = updating;
        updating = true;
        try {
            sheets.entrySet().stream()
                    .filter(e -> e.getValue() == sheet)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(tabs.getSelectionModel()::select);
        } finally {
            updating = before;
        }
        movePreview();
    }

    public Sheet sheet() {
        return sheets.getOrDefault(tabs.getSelectionModel().getSelectedItem(), Sheet.PARAMETERS);
    }

    public boolean updating() {
        return updating;
    }

    public ReadOnlyStringProperty headerCodeProperty() {
        return headerTitleProperty();
    }

    public ModelDiametersEditor diameters() {
        return diameters;
    }

    public ModelMouldsPane moulds() {
        return diameters.selectedMoulds();
    }

    public LayersEditor layers() {
        return layers;
    }

    public TechnicalRecordsView<Usage> usages() {
        return usages;
    }

    public TechnicalRecordsView<Version> history() {
        return history;
    }

    public Button cancel() {
        return cancel;
    }

    public Button save() {
        return save;
    }

    public Button approve() {
        return approve;
    }

    public Button copy() {
        return copy;
    }

    public Button retire() {
        return retire;
    }

    public Button delete() {
        return delete;
    }

    void dispose() {
        diameters.dispose();
        layers.dispose();
        ranges.dispose();
        usages.dispose();
        history.dispose();
    }
}

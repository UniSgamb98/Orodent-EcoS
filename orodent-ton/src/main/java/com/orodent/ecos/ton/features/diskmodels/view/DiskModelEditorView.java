package com.orodent.ecos.ton.features.diskmodels.view;

import static com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import com.orodent.ecos.ton.features.technical.view.TechnicalTablePage;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class DiskModelEditorView extends VBox {
    public enum Sheet {
        PARAMETERS("Parametri"),
        LAYERS("Strati"),
        RANGES("Sovramateriali"),
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
            diameter = TechnicalUi.input("ton-model-diameter", "Diametro di pressatura"),
            pressure = TechnicalUi.input("ton-model-pressure", "Pressione"),
            grams = TechnicalUi.input("ton-model-grams", "Peso per millimetro"),
            top = TechnicalUi.input("ton-model-top", "Sovramateriale superiore"),
            bottom = TechnicalUi.input("ton-model-bottom", "Sovramateriale inferiore");
    private final TextArea notes = new TextArea();
    private final Label heading = TechnicalUi.label("", "ton-page-heading"),
            status = TechnicalUi.label("", "muted-label"),
            feedback = TechnicalUi.label("", "muted-label"),
            readonly = TechnicalUi.label("", "ton-model-readonly");
    private final ComboBox<Version> versions = new ComboBox<>();
    private final TabPane tabs = new TabPane();
    private final Map<Tab, Sheet> sheets = new HashMap<>();
    private final EnumMap<Sheet, GridPane> grids = new EnumMap<>(Sheet.class);
    private final DiskModelPreview preview = new DiskModelPreview();
    private final LayersEditor layers = new LayersEditor(this::refreshPreview);
    private final RangesEditor ranges = new RangesEditor(this::refreshPreview);
    private final TechnicalTablePage<Usage> usages =
            new TechnicalTablePage<>("ton-model-usages", "Apri articolo");
    private final TechnicalTablePage<Version> history =
            new TechnicalTablePage<>("ton-model-versions", "Apri versione");
    private final Button back = TechnicalUi.button("← Elenco modelli", "ton-model-back", false),
            reload = TechnicalUi.button("Ricarica scheda", "ton-model-reload", false),
            cancel = TechnicalUi.button("Annulla modifiche", "ton-model-cancel", false),
            save = TechnicalUi.button("Salva bozza", "ton-model-save", false),
            approve = TechnicalUi.button("Salva e approva", "ton-model-approve", true),
            copy = TechnicalUi.button("Crea nuova versione", "ton-model-copy", true),
            retire = TechnicalUi.button("Ritira versione", "ton-model-retire", false),
            delete = TechnicalUi.button("Elimina bozza", "ton-model-delete", false);
    private final List<Node> editable = new ArrayList<>();
    private boolean updating;

    DiskModelEditorView() {
        super(12);
        setId("ton-model-editor");
        setMinWidth(0);
        getStyleClass().add("ton-model-page");
        heading.setId("ton-model-heading");
        status.setId("ton-model-status");
        feedback.setId("ton-model-feedback");
        readonly.setId("ton-model-readonly");
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        versions.setId("ton-model-version");
        versions.setPrefWidth(220);
        versions.setAccessibleText("Versione del modello");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var title = new HBox(15, heading, spacer, new Label("Versione"), versions);
        title.setAlignment(Pos.CENTER_LEFT);
        notes.setId("ton-model-notes");
        notes.setWrapText(true);
        notes.setPrefRowCount(3);
        var parameters =
                TechnicalUi.panel(
                        "Identità e pressatura",
                        "Il modello può essere riutilizzato per più prodotti e altezze.",
                        TechnicalUi.fields(
                                TechnicalUi.field("Codice modello *", code),
                                TechnicalUi.field("Diametro di pressatura · mm *", diameter),
                                TechnicalUi.field("Pressione · kg/cm² *", pressure),
                                TechnicalUi.field("Peso per millimetro · g/mm *", grams)),
                        TechnicalUi.field("Note tecniche", notes),
                        TechnicalUi.label(
                                "Il diametro di pressatura è distinto dal diametro nominale"
                                    + " dell'articolo. L'altezza nominale appartiene all'articolo;"
                                    + " le polveri appartengono alla composizione.",
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
        var usagePanel =
                TechnicalUi.panel(
                        "Specifiche che utilizzano questa versione",
                        "Ogni collegamento conserva il modello preciso. Approvare una nuova"
                                + " versione non aggiorna automaticamente le specifiche esistenti.",
                        usages);
        TechnicalUi.column(usages.table(), "Articolo", 170, Usage::articleCode);
        TechnicalUi.column(usages.table(), "Prodotto", 120, Usage::productCode);
        TechnicalUi.column(
                usages.table(),
                "Formato",
                120,
                u ->
                        TechnicalUi.number(u.diameter())
                                + " × "
                                + TechnicalUi.number(u.height())
                                + " mm");
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
        tab(Sheet.USAGES, usagePanel);
        var historyPanel =
                TechnicalUi.panel(
                        "Versioni del modello",
                        "Le versioni approvate e ritirate conservano parametri, strati e"
                                + " sovramateriali in sola lettura.",
                        history);
        TechnicalUi.column(history.table(), "Versione", 100, v -> "v" + v.number());
        TechnicalUi.column(history.table(), "Stato", 130, v -> v.state().toString());
        TechnicalUi.column(
                history.table(),
                "Ultimo aggiornamento",
                170,
                v -> v.updated().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        tab(Sheet.VERSIONS, historyPanel);
        tabs.setId("ton-model-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setMinHeight(230);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> movePreview());
        delete.getStyleClass().add("ton-model-danger");
        retire.getStyleClass().add("ton-model-danger");
        var actions = new FlowPane(10, 8, cancel, save, approve, copy, retire, delete);
        actions.getStyleClass().add("ton-model-actions");
        getChildren()
                .addAll(
                        new FlowPane(10, 8, back, reload),
                        title,
                        status,
                        readonly,
                        tabs,
                        actions,
                        feedback);
        editable.addAll(List.of(diameter, pressure, grams, top, bottom, notes, layers, ranges));
        for (TextField field : List.of(diameter, top, bottom))
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
        var tab = new Tab(sheet.title(), TechnicalUi.scroll(content));
        sheets.put(tab, sheet);
        tabs.getTabs().add(tab);
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
            diameter.setText(data == null ? "" : TechnicalUi.number(data.diameter()));
            pressure.setText(data == null ? "" : TechnicalUi.number(data.pressure()));
            grams.setText(data == null ? "" : TechnicalUi.number(data.grams()));
            top.setText(data == null ? "0" : TechnicalUi.number(data.top()));
            bottom.setText(data == null ? "0" : TechnicalUi.number(data.bottom()));
            notes.setText(data == null ? "" : data.notes());
            for (Node n : editable) n.setDisable(frozen);
            layers.show(data == null ? List.of(BigDecimal.valueOf(100)) : data.layers(), frozen);
            ranges.show(data == null ? List.of() : data.ranges(), frozen);
            heading.setText(data == null ? "Nuovo modello disco" : data.code());
            status.setText(
                    revision == null
                            ? "Prima versione · non ancora salvata."
                            : "v"
                                    + revision.number()
                                    + " · "
                                    + revision.state()
                                    + " · Diametro pressatura "
                                    + TechnicalUi.number(data.diameter())
                                    + " mm · "
                                    + data.layers().size()
                                    + " strati");
            var choices = new ArrayList<Version>();
            if (detail != null) {
                choices.addAll(detail.versions().rows());
                if (choices.stream().noneMatch(v -> v.id() == revision.id()))
                    choices.add(
                            new Version(
                                    revision.id(),
                                    revision.number(),
                                    revision.state(),
                                    revision.updated()));
            }
            versions.getItems().setAll(choices);
            versions.setValue(
                    revision == null
                            ? null
                            : choices.stream()
                                    .filter(v -> v.id() == revision.id())
                                    .findFirst()
                                    .orElseThrow());
            versions.setDisable(revision == null);
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
                TechnicalUi.decimal(diameter.getText(), "Diametro di pressatura"),
                TechnicalUi.decimal(pressure.getText(), "Pressione"),
                TechnicalUi.decimal(grams.getText(), "Peso per millimetro"),
                TechnicalUi.decimal(top.getText(), "Sovramateriale superiore"),
                TechnicalUi.decimal(bottom.getText(), "Sovramateriale inferiore"),
                notes.getText(),
                layers.data(),
                ranges.data());
    }

    public List<String> fingerprint() {
        var result = new ArrayList<String>();
        for (TextField t : List.of(code, diameter, pressure, grams, top, bottom))
            result.add(t.getText());
        result.add(notes.getText());
        result.add("LAYERS:" + layers.texts().size());
        result.addAll(layers.texts());
        result.add("RANGES:" + ranges.texts().size());
        result.addAll(ranges.texts());
        return List.copyOf(result);
    }

    private void refreshPreview() {
        preview.update(top.getText(), bottom.getText(), diameter.getText(), layers.texts());
    }

    public void feedback(String message, boolean error) {
        feedback.setText(message);
        feedback.getStyleClass().remove("ton-error-feedback");
        if (error) feedback.getStyleClass().add("ton-error-feedback");
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

    public void selectVersion(Version v) {
        updating = true;
        try {
            versions.setValue(v);
        } finally {
            updating = false;
        }
    }

    public boolean updating() {
        return updating;
    }

    public TabPane tabs() {
        return tabs;
    }

    public ComboBox<Version> versions() {
        return versions;
    }

    public LayersEditor layers() {
        return layers;
    }

    public TechnicalTablePage<Usage> usages() {
        return usages;
    }

    public TechnicalTablePage<Version> history() {
        return history;
    }

    public Button back() {
        return back;
    }

    public Button reload() {
        return reload;
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
        layers.dispose();
        ranges.dispose();
        versions.getItems().clear();
        usages.dispose();
        history.dispose();
    }
}

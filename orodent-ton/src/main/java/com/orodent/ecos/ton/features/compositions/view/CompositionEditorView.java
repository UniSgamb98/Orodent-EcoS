package com.orodent.ecos.ton.features.compositions.view;

import static com.orodent.ecos.ton.features.compositions.model.Compositions.*;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.Choice;
import com.orodent.ecos.ton.features.catalog.view.CatalogLookup;
import com.orodent.ecos.ton.features.technical.view.*;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

public final class CompositionEditorView extends VBox {
    public enum Sheet {
        DATA("Dati generali"),
        RECIPE("Ricetta per strato"),
        SUMMARY("Riepilogo"),
        MODELS("Modelli"),
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

    private final CatalogLookup product =
            new CatalogLookup(CatalogKind.PRODUCTS, "ton-composition-product", false);
    private final RecipeState state = new RecipeState();
    private final RecipeEditor recipe = new RecipeEditor(state);
    private final CompositionSummary summary = new CompositionSummary();
    private final Label heading = TechnicalUi.label("", "ton-page-heading"),
            status = TechnicalUi.label("", "muted-label"),
            inherited =
                    TechnicalUi.label(
                            "Scegli il prodotto per visualizzare linea e colore.", "muted-label"),
            readonly = TechnicalUi.label("", "ton-comp-readonly"),
            feedback = TechnicalUi.label("", "muted-label");
    private final TextArea notes = new TextArea();
    private final TabPane tabs = new TabPane();
    private final Map<Tab, Sheet> sheets = new HashMap<>();
    private final ComboBox<Version> versions = new ComboBox<>();
    private final TextField modelSearch =
            TechnicalUi.input("ton-composition-model-search", "Cerca codice modello");
    private final CheckBox matching = new CheckBox("Solo strati corrispondenti");
    private final TechnicalTablePage<ModelMatch> models =
            new TechnicalTablePage<>("ton-composition-models", "Apri modello");
    private final TechnicalTablePage<Usage> usages =
            new TechnicalTablePage<>("ton-composition-usages", "Apri articolo");
    private final TechnicalTablePage<Version> history =
            new TechnicalTablePage<>("ton-composition-versions", "Apri versione");
    private final Button
            back = TechnicalUi.button("← Elenco composizioni", "ton-composition-back", false),
            reload = TechnicalUi.button("Ricarica scheda", "ton-composition-reload", false),
            cancel = TechnicalUi.button("Annulla modifiche", "ton-composition-cancel", false),
            save = TechnicalUi.button("Salva bozza", "ton-composition-save", false),
            approve = TechnicalUi.button("Salva e approva", "ton-composition-approve", true),
            copy = TechnicalUi.button("Crea nuova versione", "ton-composition-copy", true),
            retire = TechnicalUi.button("Ritira versione", "ton-composition-retire", false),
            delete = TechnicalUi.button("Elimina bozza", "ton-composition-delete", false);
    private boolean updating;

    CompositionEditorView() {
        super(12);
        setId("ton-composition-editor");
        setMinWidth(0);
        getStyleClass().add("ton-comp-page");
        heading.setId("ton-composition-heading");
        status.setId("ton-composition-status");
        readonly.setId("ton-composition-readonly");
        feedback.setId("ton-composition-feedback");
        inherited.setId("ton-composition-inherited");
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        versions.setId("ton-composition-version");
        versions.setPrefWidth(210);
        versions.setAccessibleText("Versione della composizione");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var title = new HBox(15, heading, spacer, new Label("Versione"), versions);
        title.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        notes.setId("ton-composition-notes");
        notes.setWrapText(true);
        notes.setPrefRowCount(3);
        product.choices()
                .valueProperty()
                .addListener(
                        (o, a, b) ->
                                inherited.setText(
                                        b == null
                                                ? "Scegli il prodotto per visualizzare linea e"
                                                        + " colore."
                                                : "Linea "
                                                        + b.lineCode()
                                                        + " · Colore "
                                                        + ("SENZA_COLORE".equals(b.color())
                                                                ? "Senza colore"
                                                                : b.color())));
        tab(
                Sheet.DATA,
                TechnicalUi.panel(
                        "Prodotto e revisione",
                        "Linea e colore derivano dal prodotto. Il prodotto rimane fisso dopo il"
                                + " primo salvataggio.",
                        TechnicalUi.field("Prodotto *", product),
                        inherited,
                        TechnicalUi.field("Note della versione", notes),
                        TechnicalUi.label(
                                "La ricetta definisce le polveri. Geometria, percentuali di"
                                    + " altezza, pressione e sovramateriali appartengono al modello"
                                    + " disco.",
                                "muted-label")));
        tab(Sheet.RECIPE, recipe);
        tab(Sheet.SUMMARY, summary);
        matching.setId("ton-composition-model-matching");
        matching.setSelected(true);
        TechnicalUi.column(models.table(), "Modello", 170, ModelMatch::code);
        TechnicalUi.column(models.table(), "Versione", 85, m -> "v" + m.version());
        TechnicalUi.column(
                models.table(),
                "Diametro pressatura",
                160,
                m -> TechnicalUi.number(m.diameter()) + " mm");
        TechnicalUi.column(models.table(), "Strati", 80, m -> "" + m.layers().size());
        TechnicalUi.column(
                models.table(),
                "Corrispondenza",
                180,
                m -> m.matches() ? "Strati corrispondenti" : "Strati diversi");
        tab(
                Sheet.MODELS,
                TechnicalUi.panel(
                        "Modelli per struttura degli strati",
                        "L'abbinamento con il modello si salva nella specifica dell'articolo. La"
                                + " corrispondenza degli strati non certifica la compatibilità del"
                                + " formato.",
                        modelSearch,
                        matching,
                        models));
        TechnicalUi.column(usages.table(), "Articolo", 180, Usage::articleCode);
        TechnicalUi.column(
                usages.table(),
                "Formato",
                125,
                u ->
                        TechnicalUi.number(u.diameter())
                                + " × "
                                + TechnicalUi.number(u.height())
                                + " mm");
        TechnicalUi.column(
                usages.table(),
                "Specifica",
                160,
                u ->
                        "v"
                                + u.specification()
                                + " · "
                                + u.state()
                                + (u.preferred() ? " · predefinita" : ""));
        TechnicalUi.column(
                usages.table(), "Modello", 150, u -> u.modelCode() + " · v" + u.modelVersion());
        tab(
                Sheet.USAGES,
                TechnicalUi.panel(
                        "Specifiche che utilizzano questa versione",
                        "Approvare una nuova ricetta non modifica i riferimenti degli articoli già"
                                + " configurati.",
                        usages));
        TechnicalUi.column(history.table(), "Versione", 100, v -> "v" + v.number());
        TechnicalUi.column(history.table(), "Stato", 130, v -> v.state().toString());
        TechnicalUi.column(
                history.table(),
                "Aggiornata il",
                180,
                v -> v.updated().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        tab(
                Sheet.VERSIONS,
                TechnicalUi.panel(
                        "Versioni della composizione",
                        "Ogni versione conserva ricetta, note e riferimenti precisi alle schede"
                                + " delle polveri.",
                        history));
        tabs.setId("ton-composition-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setMinHeight(240);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        tabs.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, a, b) -> {
                            if (sheet() == Sheet.SUMMARY) summary.show(state);
                        });
        state.onChanged(
                () -> {
                    if (!updating) {
                        recipe.updateMetrics();
                        if (sheet() == Sheet.SUMMARY) summary.show(state);
                    }
                });
        for (Button b : List.of(retire, delete)) b.getStyleClass().add("ton-comp-danger");
        var actions = new FlowPane(10, 8, cancel, save, approve, copy, retire, delete);
        actions.getStyleClass().add("ton-comp-actions");
        getChildren()
                .addAll(
                        new FlowPane(10, 8, back, reload),
                        title,
                        status,
                        readonly,
                        tabs,
                        actions,
                        feedback);
    }

    private void tab(Sheet sheet, Node content) {
        var tab = new Tab(sheet.title(), TechnicalUi.scroll(content));
        sheets.put(tab, sheet);
        tabs.getTabs().add(tab);
    }

    public void show(Detail detail) {
        updating = true;
        try {
            Revision r = detail == null ? null : detail.revision();
            boolean frozen = r != null && r.frozen();
            product.reset();
            if (detail != null) {
                var p = detail.product();
                product.select(
                        new Choice(
                                p.id(),
                                p.code(),
                                p.name(),
                                p.available(),
                                p.lineId(),
                                p.lineCode(),
                                p.color()));
            }
            product.setDisable(detail != null);
            notes.setText(r == null ? "" : r.data().notes());
            notes.setDisable(frozen);
            state.show(
                    r == null ? List.of(new Layer(1, "", List.of())) : r.data().layers(),
                    detail == null ? Map.of() : detail.powders());
            recipe.show(frozen);
            heading.setText(detail == null ? "Nuova composizione" : detail.product().label());
            status.setText(
                    r == null
                            ? "Prima versione · non ancora salvata."
                            : "v"
                                    + r.number()
                                    + " · "
                                    + r.state()
                                    + " · "
                                    + r.data().layers().size()
                                    + " strati"
                                    + (detail.product().available()
                                            ? ""
                                            : " · Prodotto o linea non disponibili"));
            var choices = new ArrayList<Version>();
            if (detail != null) {
                choices.addAll(detail.versions().rows());
                if (choices.stream().noneMatch(v -> v.id() == r.id()))
                    choices.add(new Version(r.id(), r.number(), r.state(), r.updated()));
            }
            versions.getItems().setAll(choices);
            versions.setValue(
                    r == null
                            ? null
                            : choices.stream()
                                    .filter(v -> v.id() == r.id())
                                    .findFirst()
                                    .orElseThrow());
            versions.setDisable(r == null);
            readonly.setText(
                    frozen
                            ? "Versione "
                                    + r.state().toString().toLowerCase(Locale.ITALIAN)
                                    + " · ricetta e riferimenti alle polveri in sola lettura."
                            : "");
            TechnicalUi.visible(readonly, frozen);
            TechnicalUi.visible(save, !frozen);
            TechnicalUi.visible(approve, !frozen);
            TechnicalUi.visible(copy, frozen);
            TechnicalUi.visible(retire, r != null && r.state() == State.APPROVATA);
            TechnicalUi.visible(delete, r != null && r.state() == State.BOZZA);
            delete.setDisable(detail == null || !detail.deletable());
            reload.setDisable(detail == null);
            history.reset(detail != null);
            usages.reset(detail != null);
            models.reset(true);
            modelSearch.clear();
            recipe.search().clear();
            matching.setSelected(true);
            feedback.setText("");
        } finally {
            updating = false;
        }
    }

    public Draft draft() {
        return new Draft(product.id() == null ? 0 : product.id(), notes.getText(), state.data());
    }

    public List<String> fingerprint() {
        var result = new ArrayList<String>();
        result.add(product.id() == null ? "" : "" + product.id());
        result.add(notes.getText());
        result.addAll(state.fingerprint());
        return List.copyOf(result);
    }

    public Sheet sheet() {
        return sheets.getOrDefault(tabs.getSelectionModel().getSelectedItem(), Sheet.DATA);
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
        if (sheet == Sheet.SUMMARY) summary.show(state);
    }

    public void feedback(String text, boolean error) {
        feedback.setText(text);
        feedback.getStyleClass().remove("ton-error-feedback");
        if (error) feedback.getStyleClass().add("ton-error-feedback");
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

    public RecipeState state() {
        return state;
    }

    public RecipeEditor recipe() {
        return recipe;
    }

    public CatalogLookup product() {
        return product;
    }

    public TabPane tabs() {
        return tabs;
    }

    public ComboBox<Version> versions() {
        return versions;
    }

    public TextField modelSearch() {
        return modelSearch;
    }

    public CheckBox matching() {
        return matching;
    }

    public TechnicalTablePage<ModelMatch> models() {
        return models;
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

    public void dispose() {
        recipe.dispose();
        state.dispose();
        models.dispose();
        usages.dispose();
        history.dispose();
        versions.getItems().clear();
    }
}

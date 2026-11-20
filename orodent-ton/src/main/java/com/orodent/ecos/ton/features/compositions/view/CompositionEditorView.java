package com.orodent.ecos.ton.features.compositions.view;

import static com.orodent.ecos.ton.features.compositions.model.Compositions.*;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.Choice;
import com.orodent.ecos.ton.features.catalog.view.CatalogLookup;
import com.orodent.ecos.ton.features.technical.view.*;

import javafx.scene.Node;
import javafx.scene.control.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

public final class CompositionEditorView extends TechnicalDetailView {
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
    private final Label status = TechnicalUi.label("", "muted-label"),
            inherited =
                    TechnicalUi.label(
                            "Scegli il prodotto per visualizzare linea e colore.", "muted-label"),
            readonly = TechnicalUi.label("", "muted-label");
    private final TextArea notes = new TextArea();
    private final Map<Tab, Sheet> sheets = new HashMap<>();
    private final ComboBox<Version> versions = new ComboBox<>();
    private final TextField modelSearch =
            TechnicalUi.input("ton-composition-model-search", "Cerca codice modello");
    private final CheckBox matching = new CheckBox("Solo strati corrispondenti");
    private final TechnicalTablePage<ModelMatch> models =
            new TechnicalTablePage<>("ton-composition-models", "Apri modello");
    private final TechnicalRecordsView<Usage> usages =
            new TechnicalRecordsView<>(
                    "ton-composition-usages",
                    "Prodotti che utilizzano questa composizione",
                    "Le specifiche dei prodotti conservano questa versione precisa della"
                        + " composizione. Approvare una nuova ricetta non aggiorna quelle"
                        + " esistenti.");
    private final TechnicalRecordsView<Version> history =
            new TechnicalRecordsView<>(
                    "ton-composition-versions",
                    "Versioni della composizione",
                    "Ogni versione conserva ricetta, note e riferimenti precisi alle schede delle"
                        + " polveri.");
    private final Button
            cancel = TechnicalUi.button("Annulla modifiche", "ton-composition-cancel", false),
            save = TechnicalUi.button("Salva bozza", "ton-composition-save", true),
            approve = TechnicalUi.button("Salva e approva", "ton-composition-approve", true),
            copy = TechnicalUi.button("Crea nuova versione", "ton-composition-copy", false),
            retire = TechnicalUi.button("Ritira versione", "ton-composition-retire", false),
            delete = TechnicalUi.button("Elimina bozza", "ton-composition-delete", false);
    private boolean updating;

    CompositionEditorView() {
        super("ton-composition", "← Torna alle composizioni");
        status.setId("ton-composition-status");
        readonly.setId("ton-composition-readonly");
        inherited.setId("ton-composition-inherited");
        versions.setId("ton-composition-version");
        versions.setPrefWidth(210);
        versions.setAccessibleText("Versione della composizione");
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
                "Diametri articolo",
                160,
                m ->
                        m.articleDiameters().isEmpty()
                                ? "Da associare · stampo "
                                        + TechnicalUi.number(m.diameter())
                                        + " mm"
                                : m.articleDiameters().stream()
                                                .map(TechnicalUi::number)
                                                .collect(java.util.stream.Collectors.joining(" / "))
                                        + " mm");
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
                        "L'abbinamento con il modello si salva nella specifica del prodotto. La"
                                + " corrispondenza degli strati non certifica la compatibilità del"
                                + " formato.",
                        modelSearch,
                        matching,
                        models));
        TechnicalUi.column(usages.table(), "Prodotto", 180, Usage::productCode);
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
        tab(Sheet.USAGES, usages);
        TechnicalUi.column(history.table(), "Versione", 100, v -> "v" + v.number());
        TechnicalUi.column(history.table(), "Stato", 130, v -> v.state().toString());
        TechnicalUi.column(
                history.table(),
                "Aggiornata il",
                180,
                v -> v.updated().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        tab(Sheet.VERSIONS, history);
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
        for (Button b : List.of(retire, delete)) b.getStyleClass().add("ton-editor-danger");
        status(true, status, readonly);
        addStatusAction(TechnicalUi.field("Versione", versions));
        addStatusAction(copy);
        actions(cancel, save, approve, retire, delete);
    }

    private void tab(Sheet sheet, Node content) {
        var tab =
                addSheet(
                        sheet.title(),
                        content,
                        sheet == Sheet.RECIPE || sheet == Sheet.VERSIONS || sheet == Sheet.USAGES);
        sheets.put(tab, sheet);
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
            headerTitle.set(detail == null ? "Nuova composizione" : detail.product().label());
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

    public void dispose() {
        recipe.dispose();
        state.dispose();
        models.dispose();
        usages.dispose();
        history.dispose();
        versions.getItems().clear();
    }
}

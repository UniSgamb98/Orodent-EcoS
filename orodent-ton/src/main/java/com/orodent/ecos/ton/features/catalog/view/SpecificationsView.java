package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.database.CubatureRuleVersions.Version;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

public final class SpecificationsView extends VBox {
    private final ComboBox<Specification> versions = new ComboBox<>();
    private final ComboBox<TechnicalChoice> composition = new ComboBox<>(),
            model = new ComboBox<>();
    private final ComboBox<Version> calculation = new ComboBox<>();
    private final ComboBox<TechnicalChoice> labelTemplate = new ComboBox<>(),
            markingTemplate = new ComboBox<>();
    private final TextField
            compositionQuery =
                    CatalogUi.field("ton-product-spec-composition-search", "Numero versione"),
            modelQuery = CatalogUi.field("ton-product-spec-model-search", "Codice del modello");
    private final TextArea notes = new TextArea();
    private final Button save = CatalogUi.button("Salva bozza", "ton-product-spec-save", true),
            approve = CatalogUi.button("Salva e approva", "ton-product-spec-approve", true),
            copy = CatalogUi.button("Crea nuova versione", "ton-product-spec-copy", false),
            prefer = CatalogUi.button("Imposta predefinita", "ton-product-spec-prefer", false),
            retire = CatalogUi.button("Ritira versione", "ton-product-spec-retire", false),
            previous = CatalogUi.button("←", "ton-product-spec-previous", false),
            next = CatalogUi.button("→", "ton-product-spec-next", false),
            refresh = CatalogUi.button("Aggiorna versioni", "ton-product-spec-refresh", false);
    private final Label configurationNotice =
            CatalogUi.feedback("ton-product-spec-configuration-notice");
    private final Label state = CatalogUi.hint(""),
            result = new Label(),
            feedback = CatalogUi.feedback("ton-product-spec-feedback");
    private boolean updating, available;

    public SpecificationsView() {
        super(12);
        getStyleClass().add("ton-catalogue-tab");
        versions.setId("ton-product-spec-version");
        versions.setPromptText("Prima versione");
        versions.setPrefWidth(260);
        composition.setId("ton-product-spec-composition");
        composition.setMaxWidth(Double.MAX_VALUE);
        model.setId("ton-product-spec-model");
        model.setMaxWidth(Double.MAX_VALUE);
        notes.setId("ton-product-spec-notes");
        notes.setWrapText(true);
        notes.setPrefRowCount(3);
        calculation.setId("ton-product-spec-calculation");
        calculation.setPromptText("Seleziona una regola di cubatura");
        calculation.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(labelTemplate, Priority.ALWAYS);
        labelTemplate.setId("ton-product-spec-label-template");
        labelTemplate.setMaxWidth(Double.MAX_VALUE);
        labelTemplate.setPromptText("Nessuna etichetta · scegli una versione approvata");
        HBox.setHgrow(markingTemplate, Priority.ALWAYS);
        markingTemplate.setId("ton-product-spec-marking-template");
        markingTemplate.setMaxWidth(Double.MAX_VALUE);
        markingTemplate.setPromptText("Nessuna marcatura · scegli una versione approvata");
        var fields =
                new VBox(
                        12,
                        CatalogUi.fields(
                                CatalogUi.field(
                                        "Composizione approvata",
                                        new VBox(5, compositionQuery, composition)),
                                CatalogUi.field(
                                        "Modello approvato", new VBox(5, modelQuery, model))),
                        CatalogUi.field("Riferimento metodo di calcolo *", calculation),
                        CatalogUi.field(
                                "Modello etichetta",
                                new HBox(8, labelTemplate, clearChoice(labelTemplate))),
                        CatalogUi.field(
                                "Marcatura del dorso",
                                new HBox(8, markingTemplate, clearChoice(markingTemplate))),
                        CatalogUi.field("Note della versione", notes));
        previous.setAccessibleText("Versioni più recenti");
        next.setAccessibleText("Versioni precedenti");
        feedback.getStyleClass().add("ton-error-feedback");
        configurationNotice.getStyleClass().add("ton-error-feedback");
        getChildren()
                .setAll(
                        CatalogUi.hint(
                                "Ogni produzione userà una versione precisa di composizione e"
                                        + " modello. Le versioni approvate sono di sola lettura."),
                        new FlowPane(10, 8, versions, previous, next, result, refresh),
                        configurationNotice,
                        state,
                        fields,
                        new FlowPane(10, 8, save, approve, copy, prefer, retire),
                        feedback);
    }

    private Button clearChoice(ComboBox<TechnicalChoice> choice) {
        var b = new Button("Rimuovi");
        b.disableProperty().bind(choice.disabledProperty());
        b.setOnAction(e -> choice.setValue(null));
        return b;
    }

    public void reset(boolean saved, boolean available) {
        this.available = available;
        updating = true;
        try {
            versions.getItems().clear();
            versions.setValue(null);
            composition.getItems().clear();
            model.getItems().clear();
            composition.setValue(null);
            model.setValue(null);
            compositionQuery.clear();
            modelQuery.clear();
            labelTemplate.getItems().clear();
            markingTemplate.getItems().clear();
            markingTemplate.setValue(null);
            labelTemplate.setValue(null);
            calculation.getItems().clear();
            calculation.setValue(null);
            notes.clear();
            disableFields(true);
            for (var b : List.of(save, approve, copy, prefer, retire, previous, next))
                b.setDisable(true);
            refresh.setDisable(!saved);
            state.setText(
                    saved
                            ? "Carica le versioni dalla scheda Specifiche di produzione."
                            : "Salva prima l'anagrafica del prodotto.");
            configurationNotice(false);
            result.setText("");
            feedback.setText("");
        } finally {
            updating = false;
        }
    }

    public void show(SpecificationPage page, Long selectedId) {
        updating = true;
        try {
            versions.getItems().setAll(page.rows());
            Specification selected =
                    selectedId == null
                            ? page.rows().stream().findFirst().orElse(null)
                            : page.rows().stream()
                                    .filter(v -> v.id() == selectedId)
                                    .findFirst()
                                    .orElse(page.rows().stream().findFirst().orElse(null));
            versions.setValue(selected);
            choices(page);
            fill(selected);
            previous.setDisable(page.offset() == 0);
            next.setDisable(!page.hasNext());
            result.setText(
                    page.total() == 0
                            ? "Nessuna versione"
                            : (page.offset() + 1)
                                    + "–"
                                    + (page.offset() + page.rows().size())
                                    + " di "
                                    + page.total());
            feedback.setText("");
        } finally {
            updating = false;
        }
    }

    public void configurationNotice(boolean conflict) {
        configurationNotice.setText(
                conflict
                        ? "Specifiche importate dagli articoli da verificare: scegli e imposta"
                                + " esplicitamente la versione predefinita comune al prodotto."
                        : "");
    }

    public void choices(SpecificationPage page) {
        boolean before = updating;
        updating = true;
        try {
            replace(composition, page.compositions());
            replace(model, page.models());
            replace(labelTemplate, page.labelTemplates());
            replace(markingTemplate, page.markingTemplates());
            Version selectedRule = calculation.getValue();
            calculation.getItems().setAll(page.calculationRules());
            calculation.setValue(
                    selectedRule == null
                            ? null
                            : calculation.getItems().stream()
                                    .filter(r -> r.id() == selectedRule.id())
                                    .findFirst()
                                    .orElse(null));
        } finally {
            updating = before;
        }
    }

    private static void replace(ComboBox<TechnicalChoice> box, List<TechnicalChoice> values) {
        TechnicalChoice selected = box.getValue();
        var choices = new ArrayList<>(values);
        if (selected != null && choices.stream().noneMatch(c -> c.id() == selected.id()))
            choices.addFirst(selected);
        box.getItems().setAll(choices);
        box.setValue(selected);
    }

    public void fill(Specification selected) {
        boolean before = updating;
        updating = true;
        try {
            if (selected == null) {
                composition.setValue(null);
                model.setValue(null);
                calculation.setValue(null);
                labelTemplate.setValue(null);
                markingTemplate.setValue(null);
                notes.clear();
            } else {
                select(composition, selected.compositionId(), selected.compositionLabel());
                select(model, selected.modelId(), selected.modelLabel());
                calculation.setValue(
                        calculation.getItems().stream()
                                .filter(
                                        r ->
                                                selected.calculationVersionId() != null
                                                        ? r.id() == selected.calculationVersionId()
                                                        : r.code().equals(selected.calculation()))
                                .findFirst()
                                .orElse(null));
                if (selected.labelTemplateId() != null)
                    select(
                            labelTemplate,
                            selected.labelTemplateId(),
                            selected.labelTemplateLabel());
                else labelTemplate.setValue(null);
                if (selected.markingVersionId() != null)
                    select(markingTemplate, selected.markingVersionId(), selected.markingLabel());
                else markingTemplate.setValue(null);
                notes.setText(selected.notes() == null ? "" : selected.notes());
            }
            boolean locked = selected != null && selected.frozen();
            disableFields(locked || !available);
            CatalogUi.visible(save, !locked);
            CatalogUi.visible(approve, !locked);
            save.setDisable(!available);
            approve.setDisable(!available);
            CatalogUi.visible(copy, locked);
            copy.setDisable(!available);
            CatalogUi.visible(prefer, selected != null && "APPROVATA".equals(selected.state()));
            prefer.setDisable(
                    !available || selected == null || !selected.recorded() || selected.preferred());
            CatalogUi.visible(retire, selected != null && "APPROVATA".equals(selected.state()));
            retire.setDisable(selected == null);
            state.setText(
                    selected == null
                            ? "Prodotto da configurare. Per iniziare servono una composizione e un"
                                    + " modello approvati. Puoi mantenere l'anagrafica senza"
                                    + " configurazione."
                            : !selected.recorded() && "APPROVATA".equals(selected.state())
                                    ? "Versione precedente senza revisioni anagrafiche registrate:"
                                          + " crea e approva una nuova versione prima di usarla per"
                                          + " nuove produzioni."
                                    : selected.toString()
                                            + (selected.frozen()
                                                    ? " · sola lettura"
                                                    : " · modificabile"));
        } finally {
            updating = before;
        }
    }

    private static void select(ComboBox<TechnicalChoice> box, long id, String label) {
        TechnicalChoice choice =
                box.getItems().stream()
                        .filter(c -> c.id() == id)
                        .findFirst()
                        .orElse(new TechnicalChoice(id, label));
        if (box.getItems().stream().noneMatch(c -> c.id() == id)) box.getItems().add(choice);
        box.setValue(choice);
    }

    public SpecificationDraft draft() {
        if (calculation.getValue() == null)
            throw new IllegalArgumentException("Seleziona una regola di cubatura.");
        return new SpecificationDraft(
                composition.getValue() == null ? 0 : composition.getValue().id(),
                model.getValue() == null ? 0 : model.getValue().id(),
                calculation.getValue().code(),
                notes.getText(),
                calculation.getValue().id(),
                labelTemplate.getValue() == null ? null : labelTemplate.getValue().id(),
                markingTemplate.getValue() == null ? null : markingTemplate.getValue().id());
    }

    public List<String> fingerprint() {
        return List.of(
                composition.getValue() == null ? "" : "" + composition.getValue().id(),
                model.getValue() == null ? "" : "" + model.getValue().id(),
                calculation.getValue() == null ? "" : "" + calculation.getValue().id(),
                notes.getText(),
                labelTemplate.getValue() == null ? "" : "" + labelTemplate.getValue().id(),
                markingTemplate.getValue() == null ? "" : "" + markingTemplate.getValue().id());
    }

    public void selectVersion(Specification value) {
        updating = true;
        try {
            versions.setValue(value);
        } finally {
            updating = false;
        }
    }

    public void error(String text) {
        feedback.setText(text);
    }

    public void loading() {
        disableFields(true);
        versions.setDisable(true);
        for (var b : List.of(save, approve, copy, prefer, retire, previous, next))
            b.setDisable(true);
        state.setText("Caricamento versioni…");
    }

    private void disableFields(boolean disabled) {
        for (var input :
                List.of(
                        compositionQuery,
                        composition,
                        modelQuery,
                        model,
                        calculation,
                        labelTemplate,
                        markingTemplate,
                        notes)) input.setDisable(disabled);
    }

    public boolean updating() {
        return updating;
    }

    public ComboBox<Specification> versions() {
        return versions;
    }

    public TextField compositionQuery() {
        return compositionQuery;
    }

    public TextField modelQuery() {
        return modelQuery;
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

    public Button prefer() {
        return prefer;
    }

    public Button retire() {
        return retire;
    }

    public Button previous() {
        return previous;
    }

    public Button next() {
        return next;
    }

    public Button refresh() {
        return refresh;
    }
}

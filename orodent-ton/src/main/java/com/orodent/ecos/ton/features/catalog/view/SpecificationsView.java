package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

public final class SpecificationsView extends VBox {
    private final ComboBox<Specification> versions = new ComboBox<>();
    private final ComboBox<TechnicalChoice> composition = new ComboBox<>(),
            model = new ComboBox<>();
    private final TextField
            compositionQuery =
                    CatalogUi.field("ton-article-spec-composition-search", "Numero versione"),
            modelQuery = CatalogUi.field("ton-article-spec-model-search", "Codice del modello"),
            calculation =
                    CatalogUi.field(
                            "ton-article-spec-calculation",
                            "Riferimento al metodo di calcolo validato");
    private final TextArea notes = new TextArea();
    private final Button save = CatalogUi.button("Salva bozza", "ton-article-spec-save", true),
            approve = CatalogUi.button("Salva e approva", "ton-article-spec-approve", true),
            copy = CatalogUi.button("Crea nuova versione", "ton-article-spec-copy", false),
            prefer = CatalogUi.button("Imposta predefinita", "ton-article-spec-prefer", false),
            retire = CatalogUi.button("Ritira versione", "ton-article-spec-retire", false),
            previous = CatalogUi.button("←", "ton-article-spec-previous", false),
            next = CatalogUi.button("→", "ton-article-spec-next", false),
            refresh = CatalogUi.button("Aggiorna versioni", "ton-article-spec-refresh", false);
    private final Label state = CatalogUi.hint(""),
            result = new Label(),
            feedback = CatalogUi.feedback("ton-article-spec-feedback");
    private final VBox fields;
    private boolean updating, available;

    public SpecificationsView() {
        super(12);
        getStyleClass().add("ton-catalogue-tab");
        versions.setId("ton-article-spec-version");
        versions.setPromptText("Prima versione");
        versions.setPrefWidth(260);
        composition.setId("ton-article-spec-composition");
        composition.setMaxWidth(Double.MAX_VALUE);
        model.setId("ton-article-spec-model");
        model.setMaxWidth(Double.MAX_VALUE);
        notes.setId("ton-article-spec-notes");
        notes.setWrapText(true);
        notes.setPrefRowCount(3);
        fields =
                new VBox(
                        12,
                        CatalogUi.fields(
                                CatalogUi.field(
                                        "Composizione approvata",
                                        new VBox(5, compositionQuery, composition)),
                                CatalogUi.field(
                                        "Modello approvato", new VBox(5, modelQuery, model))),
                        CatalogUi.field("Riferimento metodo di calcolo *", calculation),
                        CatalogUi.field("Note della versione", notes));
        previous.setAccessibleText("Versioni più recenti");
        next.setAccessibleText("Versioni precedenti");
        feedback.getStyleClass().add("ton-error-feedback");
        getChildren()
                .setAll(
                        CatalogUi.hint(
                                "Ogni produzione userà una versione precisa di composizione e"
                                    + " modello. Le versioni approvate sono di sola lettura."),
                        new FlowPane(10, 8, versions, previous, next, result, refresh),
                        state,
                        fields,
                        new FlowPane(10, 8, save, approve, copy, prefer, retire),
                        feedback);
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
            calculation.clear();
            notes.clear();
            fields.setDisable(true);
            for (var b : List.of(save, approve, copy, prefer, retire, previous, next))
                b.setDisable(true);
            refresh.setDisable(!saved);
            state.setText(
                    saved
                            ? "Carica le versioni dalla scheda Specifiche di produzione."
                            : "Salva prima l'anagrafica dell'articolo.");
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

    public void choices(SpecificationPage page) {
        boolean before = updating;
        updating = true;
        try {
            replace(composition, page.compositions());
            replace(model, page.models());
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
                calculation.clear();
                notes.clear();
            } else {
                select(composition, selected.compositionId(), selected.compositionLabel());
                select(model, selected.modelId(), selected.modelLabel());
                calculation.setText(selected.calculation());
                notes.setText(selected.notes() == null ? "" : selected.notes());
            }
            boolean locked = selected != null && selected.frozen();
            fields.setDisable(locked || !available);
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
                            ? "Articolo da configurare. Per iniziare servono una composizione e un"
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
        return new SpecificationDraft(
                composition.getValue() == null ? 0 : composition.getValue().id(),
                model.getValue() == null ? 0 : model.getValue().id(),
                calculation.getText(),
                notes.getText());
    }

    public List<String> fingerprint() {
        return List.of(
                composition.getValue() == null ? "" : "" + composition.getValue().id(),
                model.getValue() == null ? "" : "" + model.getValue().id(),
                calculation.getText(),
                notes.getText());
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
        fields.setDisable(true);
        versions.setDisable(true);
        for (var b : List.of(save, approve, copy, prefer, retire, previous, next))
            b.setDisable(true);
        state.setText("Caricamento versioni…");
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

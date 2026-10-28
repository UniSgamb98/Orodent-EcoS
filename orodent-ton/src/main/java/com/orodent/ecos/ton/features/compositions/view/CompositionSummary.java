package com.orodent.ecos.ton.features.compositions.view;

import com.orodent.ecos.ton.features.compositions.model.Compositions.*;
import com.orodent.ecos.ton.features.compositions.service.CompositionChemistry;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.*;

final class CompositionSummary extends VBox {
    private final TableView<PowderRef> matrix = new TableView<>();
    private final TableView<String> oxides = new TableView<>();
    private final Label totals = TechnicalUi.label("", "muted-label");

    CompositionSummary() {
        super(18);
        matrix.setId("ton-composition-matrix");
        oxides.setId("ton-composition-oxides");
        totals.setId("ton-composition-summary-totals");
        matrix.setPlaceholder(new Label("Aggiungi le polveri nella ricetta."));
        oxides.setPlaceholder(
                new Label("Nessuna scheda ossidi disponibile per le polveri selezionate."));
        getChildren()
                .addAll(
                        TechnicalUi.panel(
                                "Ricetta a colpo d'occhio",
                                "Ogni colonna è una miscela distinta con totale 100% in massa.",
                                matrix,
                                totals),
                        TechnicalUi.panel(
                                "Composizione chimica nominale",
                                "Bilancio di massa dalle revisioni di polvere selezionate.",
                                oxides,
                                TechnicalUi.label(
                                        "Un ossido mancante non vale zero. I risultati parziali"
                                            + " riportano la copertura dei dati; le sole quote note"
                                            + " non vengono rinormalizzate.",
                                        "muted-label")),
                        TechnicalUi.label(
                                "Le classi Y restano distinte da wt% Y₂O₃. Resistenza e traslucenza"
                                        + " della miscela richiedono misure o un modello validato.",
                                "muted-label"));
    }

    void show(RecipeState state) {
        Map<Long, PowderRef> refs = state.powders();
        matrix.getColumns().clear();
        oxides.getColumns().clear();
        TechnicalUi.column(matrix, "Polvere · versione", 230, PowderRef::label);
        TechnicalUi.column(oxides, "Ossido", 140, s -> s);
        var known = new ArrayList<Layer>();
        var totalLabels = new ArrayList<String>();
        for (int i = 0; i < state.layers().size(); i++) {
            Layer layer;
            try {
                layer = state.layer(i);
            } catch (IllegalArgumentException invalid) {
                layer = null;
            }
            known.add(layer);
            totalLabels.add(
                    "Strato "
                            + state.layers().get(i).number()
                            + ": "
                            + (layer == null
                                    ? "da completare"
                                    : (CompositionChemistry.complete(layer, refs) ? "✓ " : "○ ")
                                            + TechnicalUi.number(layer.total())
                                            + "%"));
            int column = i;
            var c = new TableColumn<PowderRef, String>("Strato " + state.layers().get(i).number());
            c.setPrefWidth(140);
            c.setSortable(false);
            c.setCellValueFactory(
                    v -> {
                        Layer l = known.get(column);
                        String text =
                                l == null
                                        ? "Da completare"
                                        : l.ingredients().stream()
                                                .filter(
                                                        row ->
                                                                row.powderRevisionId()
                                                                        == v.getValue().id())
                                                .findFirst()
                                                .map(row -> TechnicalUi.number(row.mass()) + "%")
                                                .orElse("—");
                        return new SimpleStringProperty(text);
                    });
            matrix.getColumns().add(c);
            var oxide = new TableColumn<String, String>("Strato " + state.layers().get(i).number());
            oxide.setPrefWidth(185);
            oxide.setSortable(false);
            oxide.setCellValueFactory(
                    v -> {
                        Layer l = known.get(column);
                        if (l == null) return new SimpleStringProperty("Ricetta incompleta");
                        var b = CompositionChemistry.balance(l, v.getValue(), refs);
                        return new SimpleStringProperty(
                                !b.completeRecipe()
                                        ? "Ricetta incompleta"
                                        : b.weightPercent() == null
                                                ? "Dati parziali · "
                                                        + TechnicalUi.number(b.coverage())
                                                        + "% copertura"
                                                : CompositionFormats.oxide(b.weightPercent()));
                    });
            oxides.getColumns().add(oxide);
        }
        var powders =
                refs.values().stream()
                        .sorted(
                                Comparator.comparing(PowderRef::code)
                                        .thenComparing(PowderRef::number))
                        .toList();
        matrix.getItems().setAll(powders);
        totals.setText(String.join(" · ", totalLabels));
        matrix.setPrefHeight(Math.min(380, 65 + powders.size() * 34));
        matrix.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        var codes = new TreeSet<String>();
        for (PowderRef p : refs.values()) codes.addAll(p.oxides().keySet());
        oxides.getItems().setAll(codes);
        oxides.setPrefHeight(Math.min(400, 65 + codes.size() * 34));
        oxides.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
    }
}

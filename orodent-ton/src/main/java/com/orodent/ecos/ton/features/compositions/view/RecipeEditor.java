package com.orodent.ecos.ton.features.compositions.view;

import com.orodent.ecos.ton.features.compositions.model.Compositions.*;
import com.orodent.ecos.ton.features.compositions.service.CompositionChemistry;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.*;

/** Materialises controls for the selected stratum only; the editing buffer retains every layer. */
public final class RecipeEditor extends GridPane {
    private final RecipeState state;
    private final TilePane navigation = new TilePane(8, 8);
    private final VBox rows = new VBox(10), legend = new VBox(8), center;
    private final Label title = TechnicalUi.label("", "ton-technical-panel-title"),
            total = TechnicalUi.label("", "ton-comp-total"),
            yttria = TechnicalUi.label("—", "ton-comp-chemical-value"),
            progress = TechnicalUi.label("", "muted-label");
    private final TextField
            name = TechnicalUi.input("ton-composition-layer-name", "Nome facoltativo dello strato"),
            search =
                    TechnicalUi.input(
                            "ton-composition-powder-search", "Cerca polvere per codice o nome");
    private final HBox mix = new HBox(2);
    private final Button
            powderPrevious = TechnicalUi.button("←", "ton-composition-powders-previous", false),
            powderNext = TechnicalUi.button("→", "ton-composition-powders-next", false);
    private final Label powderPage = TechnicalUi.label("", "muted-label");
    private final Button
            addLayer = TechnicalUi.button("+ Strato", "ton-composition-layer-add", false),
            addPowder =
                    TechnicalUi.button(
                            "+ Aggiungi polvere", "ton-composition-ingredient-add", false),
            copy = TechnicalUi.button("Copia da strato…", "ton-composition-layer-copy", false),
            up = TechnicalUi.button("↑ Sposta su", "ton-composition-layer-up", false),
            down = TechnicalUi.button("↓ Sposta giù", "ton-composition-layer-down", false),
            remove = TechnicalUi.button("Rimuovi strato", "ton-composition-layer-remove", false);
    private final List<ComboBox<PowderRef>> boxes = new ArrayList<>();
    private final List<RecipeState.Item> shown = new ArrayList<>();
    private final List<TextField> amounts = new ArrayList<>();
    private final List<Label> rowMessages = new ArrayList<>(),
            navNames = new ArrayList<>(),
            navTotals = new ArrayList<>();
    private final VBox summary;
    private List<PowderRef> choices = List.of();
    private RecipeState.Stratum bound;
    private int selected;
    private boolean frozen, updatingChoices;

    RecipeEditor(RecipeState state) {
        this.state = state;
        setHgap(18);
        setVgap(18);
        setMinWidth(0);
        navigation.setPrefColumns(1);
        navigation.setPrefTileWidth(132);
        navigation.setTileAlignment(Pos.TOP_LEFT);
        navigation.setMinWidth(0);
        navigation.setMaxWidth(Double.MAX_VALUE);
        title.setId("ton-composition-layer-heading");
        total.setId("ton-composition-layer-total");
        yttria.setId("ton-composition-yttria");
        progress.setId("ton-composition-layer-progress");
        mix.setPrefHeight(18);
        mix.setMinHeight(18);
        mix.setMaxWidth(Double.MAX_VALUE);
        mix.widthProperty().addListener((o, a, b) -> updateMetrics());
        powderPrevious.setAccessibleText("Schede polveri precedenti");
        powderNext.setAccessibleText("Schede polveri successive");
        powderPrevious.setDisable(true);
        powderNext.setDisable(true);
        var powderSearch = new HBox(6, search, powderPrevious, powderNext);
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMinWidth(80);
        center =
                new VBox(
                        12,
                        title,
                        TechnicalUi.field("Nome dello strato", name),
                        powderSearch,
                        powderPage,
                        rows,
                        total,
                        new FlowPane(10, 8, addPowder, copy),
                        TechnicalUi.label(
                                "Ogni strato deve arrivare al 100%. Le quote indicano la quantità"
                                        + " di polvere nella miscela, non l'altezza dello strato.",
                                "muted-label"),
                        new FlowPane(8, 8, up, down, remove));
        center.setMinWidth(0);
        center.getStyleClass().add("ton-technical-panel");
        summary =
                TechnicalUi.panel(
                        "Riepilogo miscela",
                        "Strato selezionato",
                        mix,
                        legend,
                        new Label("Y₂O₃ nominale"),
                        yttria,
                        TechnicalUi.label(
                                "Bilancio di massa dalle revisioni scelte. La classe 3Y/4Y/5Y/6Y è"
                                        + " distinta da wt% Y₂O₃.",
                                "muted-label"),
                        progress);
        getChildren().addAll(navigation, center, summary);
        setFillHeight(summary, false);
        setFillHeight(navigation, false);
        widthProperty().addListener((o, a, b) -> arrange(b.doubleValue()));
        arrange(0);
        addLayer.setOnAction(
                e -> {
                    state.addLayer();
                    selected = state.layers().size() - 1;
                    render();
                });
        addPowder.setOnAction(
                e -> {
                    state.addItem(selected);
                    renderRows();
                });
        up.setOnAction(
                e -> {
                    state.moveLayer(selected, -1);
                    selected--;
                    render();
                });
        down.setOnAction(
                e -> {
                    state.moveLayer(selected, 1);
                    selected++;
                    render();
                });
    }

    private void arrange(double width) {
        getColumnConstraints().clear();
        boolean wide = width >= 800, medium = width >= 620;
        navigation.setPrefTileWidth(width >= 1080 ? 132 : 108);
        if (medium) {
            var nav = new ColumnConstraints(width >= 1080 ? 136 : 112);
            var main = new ColumnConstraints();
            main.setHgrow(Priority.ALWAYS);
            main.setMinWidth(0);
            getColumnConstraints().addAll(nav, main);
            if (wide) {
                var extra = new ColumnConstraints(width >= 1080 ? 245 : 220);
                getColumnConstraints().add(extra);
            }
            setColumnIndex(navigation, 0);
            setRowIndex(navigation, 0);
            setColumnIndex(center, 1);
            setRowIndex(center, 0);
            setColumnIndex(summary, wide ? 2 : 1);
            setRowIndex(summary, wide ? 0 : 1);
        } else {
            var col = new ColumnConstraints();
            col.setHgrow(Priority.ALWAYS);
            col.setPercentWidth(100);
            getColumnConstraints().add(col);
            setColumnIndex(navigation, 0);
            setRowIndex(navigation, 0);
            setColumnIndex(center, 0);
            setRowIndex(center, 1);
            setColumnIndex(summary, 0);
            setRowIndex(summary, 2);
        }
        setValignment(navigation, javafx.geometry.VPos.TOP);
        setValignment(center, javafx.geometry.VPos.TOP);
        setValignment(summary, javafx.geometry.VPos.TOP);
    }

    public void show(boolean frozen) {
        this.frozen = frozen;
        powderPrevious.setDisable(true);
        powderNext.setDisable(true);
        powderPage.setText("");
        selected = Math.min(selected, Math.max(0, state.layers().size() - 1));
        render();
    }

    private void render() {
        renderNavigation();
        renderRows();
    }

    private void renderNavigation() {
        navigation.getChildren().clear();
        navNames.clear();
        navTotals.clear();
        for (int i = 0; i < state.layers().size(); i++) {
            int index = i;
            var layer = state.layers().get(i);
            var caption = TechnicalUi.label(layer.name().get(), "muted-label");
            var number = TechnicalUi.label("", "ton-comp-layer-amount");
            navNames.add(caption);
            navTotals.add(number);
            var button = new Button();
            button.setId("ton-composition-layer-select-" + layer.number());
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass()
                    .add(i == selected ? "ton-comp-layer-selected" : "ton-comp-layer-choice");
            button.setGraphic(new VBox(6, new Label("Strato " + layer.number()), caption, number));
            button.setOnAction(
                    e -> {
                        selected = index;
                        render();
                    });
            navigation.getChildren().add(button);
        }
        addLayer.setDisable(frozen);
        navigation.getChildren().add(addLayer);
    }

    private void unbind() {
        if (bound != null) name.textProperty().unbindBidirectional(bound.name());
        for (int i = 0; i < shown.size(); i++) {
            amounts.get(i).textProperty().unbindBidirectional(shown.get(i).mass());
            boxes.get(i).setOnAction(null);
        }
        bound = null;
        shown.clear();
        amounts.clear();
        boxes.clear();
        rowMessages.clear();
    }

    private void renderRows() {
        unbind();
        rows.getChildren().clear();
        boolean empty = state.layers().isEmpty();
        center.setDisable(empty);
        if (empty) {
            title.setText("Aggiungi uno strato per iniziare.");
            updateMetrics();
            return;
        }
        bound = state.layers().get(selected);
        name.textProperty().bindBidirectional(bound.name());
        name.setDisable(frozen);
        search.setDisable(frozen);
        for (int i = 0; i < bound.items().size(); i++) {
            var item = bound.items().get(i);
            shown.add(item);
            var box = new ComboBox<PowderRef>();
            box.setId("ton-composition-ingredient-" + (i + 1) + "-powder");
            box.setPromptText("Scegli polvere e versione");
            box.setAccessibleText("Polvere e revisione ingrediente " + (i + 1));
            box.setMaxWidth(Double.MAX_VALUE);
            box.setMinWidth(130);
            box.setDisable(frozen);
            boxes.add(box);
            var amount =
                    TechnicalUi.input(
                            "ton-composition-ingredient-" + (i + 1) + "-mass",
                            "Percentuale ingrediente " + (i + 1));
            amount.setPrefWidth(85);
            amount.setMinWidth(65);
            amount.setMaxWidth(100);
            amount.textProperty().bindBidirectional(item.mass());
            amount.setDisable(frozen);
            amounts.add(amount);
            var delete =
                    TechnicalUi.button(
                            "×", "ton-composition-ingredient-" + (i + 1) + "-remove", false);
            delete.setAccessibleText("Rimuovi ingrediente");
            delete.setDisable(frozen);
            delete.setOnAction(
                    e -> {
                        state.removeItem(selected, item);
                        renderRows();
                    });
            var unit = new Label("%");
            unit.setMinWidth(Region.USE_PREF_SIZE);
            var row = new HBox(8, box, amount, unit, delete);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(box, Priority.ALWAYS);
            var warning = TechnicalUi.label("", "ton-comp-warning");
            rowMessages.add(warning);
            rows.getChildren().add(new VBox(5, row, warning));
            box.setOnAction(
                    e -> {
                        if (!updatingChoices) item.powder().set(box.getValue());
                    });
        }
        choices(choices);
        addPowder.setDisable(frozen);
        copy.setDisable(frozen || state.layers().size() < 2);
        up.setDisable(frozen || selected == 0);
        down.setDisable(frozen || selected == state.layers().size() - 1);
        remove.setDisable(frozen || state.layers().size() == 1);
        updateMetrics();
    }

    public void choices(List<PowderRef> choices) {
        this.choices = List.copyOf(choices);
        updatingChoices = true;
        try {
            for (int i = 0; i < boxes.size(); i++) {
                var selected = shown.get(i).powder().get();
                var items = new ArrayList<>(choices);
                if (selected != null) {
                    items.removeIf(p -> p.id() == selected.id());
                    items.addFirst(selected);
                }
                boxes.get(i).getItems().setAll(items);
                boxes.get(i).setValue(selected);
            }
        } finally {
            updatingChoices = false;
        }
        updateMetrics();
    }

    public void choices(Page<PowderRef> page) {
        choices(page.rows());
        powderPage.setText("Schede polveri: " + page.label());
        powderPrevious.setDisable(frozen || page.offset() == 0);
        powderNext.setDisable(frozen || !page.hasNext());
    }

    public Button powderPrevious() {
        return powderPrevious;
    }

    public Button powderNext() {
        return powderNext;
    }

    public void updateMetrics() {
        selected = Math.min(selected, Math.max(0, state.layers().size() - 1));
        var refs = state.powders();
        int complete = 0;
        for (int i = 0; i < state.layers().size(); i++) {
            boolean valid = false;
            BigDecimal sum = BigDecimal.ZERO;
            try {
                Layer l = state.layer(i);
                sum = l.total();
                valid = CompositionChemistry.complete(l, refs);
            } catch (IllegalArgumentException pending) {
                for (var item : state.layers().get(i).items()) {
                    BigDecimal n = TechnicalUi.preview(item.mass().get());
                    if (n != null) sum = sum.add(n);
                }
            }
            if (valid) complete++;
            if (i < navTotals.size()) {
                navTotals.get(i).setText((valid ? "✓ " : "○ ") + TechnicalUi.number(sum) + "%");
                navNames.get(i).setText(state.layers().get(i).name().get());
            }
        }
        progress.setText(complete + " / " + state.layers().size() + " strati completi");
        mix.getChildren().clear();
        legend.getChildren().clear();
        yttria.setText("—");
        if (state.layers().isEmpty()) return;
        title.setText(state.layers().get(selected).label());
        try {
            Layer layer = state.layer(selected);
            boolean valid = CompositionChemistry.complete(layer, refs);
            total.setText(
                    (valid ? "✓ Miscela completa · " : "Miscela da completare · ")
                            + TechnicalUi.number(layer.total())
                            + "%");
            total.getStyleClass().remove("ton-comp-invalid");
            if (!valid) total.getStyleClass().add("ton-comp-invalid");
            if (valid) {
                for (Ingredient i : layer.ingredients()) {
                    PowderRef p = refs.get(i.powderRevisionId());
                    var segment = new Region();
                    segment.setMinWidth(0);
                    segment.setPrefWidth(
                            Math.max(0, mix.getWidth()) * i.mass().doubleValue() / 100);
                    segment.setStyle(
                            "-fx-background-color: " + CompositionFormats.color(p.color()) + ";");
                    mix.getChildren().add(segment);
                    legend.getChildren()
                            .add(
                                    TechnicalUi.label(
                                            p.code()
                                                    + " · v"
                                                    + p.number()
                                                    + " · "
                                                    + TechnicalUi.number(i.mass())
                                                    + "%",
                                            "muted-label"));
                }
                var y = CompositionChemistry.balance(layer, "Y2O3", refs);
                yttria.setText(
                        y.weightPercent() == null
                                ? "Dati parziali"
                                : CompositionFormats.oxide(y.weightPercent()));
            }
        } catch (IllegalArgumentException pending) {
            total.setText(pending.getMessage());
            if (!total.getStyleClass().contains("ton-comp-invalid"))
                total.getStyleClass().add("ton-comp-invalid");
        }
        for (int i = 0; i < shown.size(); i++) {
            PowderRef p = shown.get(i).powder().get();
            String message =
                    p != null && !p.usable()
                            ? "Questa revisione non è disponibile per una nuova approvazione."
                            : "";
            rowMessages.get(i).setText(message);
            TechnicalUi.visible(rowMessages.get(i), !message.isEmpty());
        }
    }

    public int selected() {
        return selected;
    }

    public TextField search() {
        return search;
    }

    public Button copy() {
        return copy;
    }

    public Button remove() {
        return remove;
    }

    public void copied() {
        render();
    }

    public void removed() {
        selected = Math.min(selected, state.layers().size() - 1);
        render();
    }

    public void dispose() {
        unbind();
        for (Button b :
                List.of(addLayer, addPowder, copy, up, down, remove, powderPrevious, powderNext))
            b.setOnAction(null);
        rows.getChildren().clear();
        navigation.getChildren().clear();
    }
}

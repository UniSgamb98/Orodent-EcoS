package com.orodent.ecos.costi.scenarios;

import static com.orodent.ecos.costi.scenarios.ScenarioTables.*;

import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.scenarios.Scenarios.*;
import com.orodent.ecos.costi.view.PriceFormats;

import javafx.beans.property.*;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** Buffered technical editors: invalid text never silently replaces the applied scenario. */
public final class ScenarioEditors extends BorderPane {
    public final BooleanProperty pending = new SimpleBooleanProperty();
    public final Label feedback = new Label();
    private Draft draft;
    private Catalogue valued;
    private Consumer<Draft> applied = d -> {};
    private boolean rendering;
    private final TabPane tabs = new TabPane();
    private final ComboBox<Recipe> recipeSource = new ComboBox<>();
    private final ListView<Integer> layers = new ListView<>();
    private final TableView<EditRow> ingredients =
            edit(
                    new String[] {"Polvere / revisione", "Scenario massa %"},
                    new boolean[] {false, true},
                    this::changed);
    private final Map<Integer, ObservableList<EditRow>> recipeRows = new TreeMap<>();
    private Recipe recipeTemplate, shownRecipe;
    private final ComboBox<Model> modelSource = new ComboBox<>();
    private Model modelTemplate, shownModel;
    private Product shownProduct;
    private final TextField grams = new TextField(),
            top = new TextField(),
            bottom = new TextField();
    private final TableView<EditRow> heights =
            edit(
                    new String[] {"Strato", "Altezza nominale %"},
                    new boolean[] {false, true},
                    this::changed);
    private final TableView<EditRow> ranges =
            edit(
                    new String[] {
                        "Da mm · incluso", "A mm · escluso", "Superiore mm", "Inferiore mm"
                    },
                    new boolean[] {true, true, true, true},
                    this::changed);
    private final ComboBox<Product> transformProduct = new ComboBox<>();
    private final TextField common = new TextField();
    private final TableView<EditRow> transforms =
            edit(
                    new String[] {"Articolo", "Base effettiva €/art.", "Eccezione scenario €/art."},
                    new boolean[] {false, false, true},
                    this::changed);
    private final TableView<EditRow> prices =
            edit(
                    new String[] {
                        "Polvere", "Base €/kg", "Tipo modifica", "Valore", "Scenario €/kg"
                    },
                    new boolean[] {false, false, false, true, false},
                    () -> {});
    private final ObservableList<EditRow> priceRows = FXCollections.observableArrayList();
    private final TextField priceSearch = new TextField(), batchValue = new TextField();
    private final ComboBox<String> color = new ComboBox<>();
    private final Map<Long, Powder> powders = new HashMap<>();
    private final Label recipeAffected = new Label(),
            modelAffected = new Label(),
            recipeTotal = new Label();

    public ScenarioEditors() {
        setId("scenarios-editors");
        grams.setId("scenarios-model-gmm");
        top.setId("scenarios-model-top");
        bottom.setId("scenarios-model-bottom");
        common.setId("scenarios-transform-common");
        tabs.setId("scenarios-editor-tabs");
        feedback.setWrapText(true);
        feedback.setId("scenarios-editor-feedback");
        setTop(feedback);
        setCenter(tabs);
        Button applyPrice = button("Applica prezzi allo scenario", this::applyPrices);
        applyPrice.setId("scenarios-apply-prices");
        var modeColumn = (TableColumn<EditRow, String>) prices.getColumns().get(2);
        modeColumn.setCellFactory(
                ComboBoxTableCell.forTableColumn("Invariato", "Variazione %", "Prezzo €/kg"));
        modeColumn.setOnEditCommit(
                e -> {
                    e.getRowValue().cells[2].set(e.getNewValue());
                    if (e.getRowValue().get(3).isBlank() && !e.getNewValue().equals("Invariato"))
                        e.getRowValue().cells[3].set("0");
                    priceChanged();
                });
        prices.getColumns()
                .get(3)
                .setOnEditCommit(
                        e -> {
                            e.getRowValue().cells[3].set((String) e.getNewValue());
                            priceChanged();
                        });
        prices.setId("scenarios-price-table");
        color.getItems().setAll("Tutte", "BIANCA", "GIALLA", "ROSA", "GRIGIA", "ALTRE");
        color.setValue("Tutte");
        priceSearch.setPromptText("Cerca polvere per codice o nome");
        priceSearch.textProperty().addListener((o, b, a) -> filterPrices());
        color.setOnAction(e -> filterPrices());
        batchValue.setPromptText("Variazione %");
        batchValue.setPrefWidth(110);
        VBox pricePane =
                new VBox(
                        10,
                        new Label(
                                "Prezzi applicati alle polveri · doppio clic sulle celle per"
                                        + " modificare"),
                        new FlowPane(
                                10,
                                8,
                                priceSearch,
                                color,
                                batchValue,
                                button("Applica % alle righe visibili", this::batch),
                                button(
                                        "Ripristina polvere selezionata",
                                        () -> {
                                            var row = prices.getSelectionModel().getSelectedItem();
                                            if (row != null) {
                                                row.cells[2].set("Invariato");
                                                row.cells[3].set("");
                                                priceChanged();
                                            }
                                        })),
                        prices,
                        new Label(
                                "Le percentuali partono dalla base. I prezzi imposti valgono solo"
                                        + " nello scenario."),
                        new HBox(
                                10,
                                applyPrice,
                                button(
                                        "Ripristina tutti i prezzi",
                                        () ->
                                                change(
                                                        draft.state(
                                                                draft.name(),
                                                                draft.notes(),
                                                                draft.archived(),
                                                                draft.selection(),
                                                                draft.products(),
                                                                Map.of(),
                                                                draft.recipes(),
                                                                draft.models(),
                                                                draft.transforms())))));
        recipeSource.setId("scenarios-recipe-source");
        recipeAffected.setWrapText(true);
        layers.setPrefWidth(145);
        layers.setMaxWidth(180);
        layers.setPrefHeight(230);
        layers.setCellFactory(
                v ->
                        new ListCell<>() {
                            protected void updateItem(Integer layer, boolean empty) {
                                super.updateItem(layer, empty);
                                setText(
                                        empty || layer == null
                                                ? null
                                                : "Strato "
                                                        + layer
                                                        + " · "
                                                        + layerTotal(layer)
                                                        + "%");
                            }
                        });
        layers.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, b, a) -> {
                            if (a != null) {
                                ingredients.setItems(recipeRows.get(a));
                                recipeTotal.setText(
                                        "Strato "
                                                + a
                                                + ": "
                                                + layerTotal(a)
                                                + "% · Obiettivo 100%");
                            }
                        });
        ingredients.setId("scenarios-recipe-ingredients");
        Button applyRecipe = button("Applica ricetta allo scenario", this::applyRecipe);
        applyRecipe.setId("scenarios-apply-recipe");
        VBox recipePane =
                new VBox(
                        10,
                        new Label("Composizione di partenza"),
                        recipeSource,
                        recipeAffected,
                        new HBox(
                                10,
                                button("Configurazioni coinvolte…", () -> affected(true)),
                                button("Carica alternativa approvata…", this::recipeAlternative),
                                button(
                                        "Ripristina originale",
                                        () -> {
                                            if (recipeSource.getValue() != null) {
                                                resetRecipe();
                                            }
                                        })),
                        new HBox(
                                14,
                                layers,
                                new VBox(
                                        8,
                                        ingredients,
                                        recipeTotal,
                                        new FlowPane(
                                                8,
                                                8,
                                                button("Aggiungi polvere…", this::addPowder),
                                                button(
                                                        "Rimuovi polvere",
                                                        () -> {
                                                            Integer layer =
                                                                    layers.getSelectionModel()
                                                                            .getSelectedItem();
                                                            var row =
                                                                    ingredients
                                                                            .getSelectionModel()
                                                                            .getSelectedItem();
                                                            if (layer != null && row != null) {
                                                                recipeRows.get(layer).remove(row);
                                                                changed();
                                                            }
                                                        }),
                                                button("Copia da strato…", this::copyLayer)))),
                        new FlowPane(
                                8,
                                8,
                                button(
                                        "Aggiungi strato",
                                        () -> {
                                            recipeRows.put(
                                                    recipeRows.size() + 1,
                                                    FXCollections.observableArrayList());
                                            showLayers(recipeRows.size());
                                            changed();
                                        }),
                                button("Rimuovi strato", this::removeLayer),
                                button("↑ Strato", () -> moveLayer(-1)),
                                button("↓ Strato", () -> moveLayer(1))),
                        applyRecipe,
                        new Label(
                                "Ogni strato deve totalizzare 100%. La copia non modifica né"
                                        + " approva composizioni in TON."));
        for (TextField field : List.of(grams, top, bottom, common))
            field.textProperty().addListener((o, b, a) -> changed());
        Button applyModel = button("Applica modello allo scenario", this::applyModel);
        applyModel.setId("scenarios-apply-model");
        modelSource.setId("scenarios-model-source");
        modelAffected.setWrapText(true);
        VBox modelPane =
                new VBox(
                        10,
                        new Label("Modello di partenza"),
                        modelSource,
                        modelAffected,
                        new FlowPane(
                                8,
                                8,
                                button("Configurazioni coinvolte…", () -> affected(false)),
                                button("Carica alternativa approvata…", this::modelAlternative),
                                button(
                                        "Ripristina originale",
                                        () -> {
                                            if (modelSource.getValue() != null) {
                                                resetModel();
                                            }
                                        })),
                        new FlowPane(
                                12,
                                8,
                                field("Coefficiente g/mm", grams),
                                field("Sovramateriale superiore mm", top),
                                field("Sovramateriale inferiore mm", bottom)),
                        new Label("Proporzioni degli strati · totale 100% dell'altezza nominale"),
                        heights,
                        new HBox(
                                8,
                                button(
                                        "Aggiungi strato",
                                        () -> {
                                            heights.getItems()
                                                    .add(
                                                            new EditRow(
                                                                    0,
                                                                    0,
                                                                    "",
                                                                    String.valueOf(
                                                                            heights.getItems()
                                                                                            .size()
                                                                                    + 1),
                                                                    "0"));
                                            changed();
                                        }),
                                button(
                                        "Rimuovi ultimo strato",
                                        () -> {
                                            if (!heights.getItems().isEmpty()) {
                                                heights.getItems().removeLast();
                                                changed();
                                            }
                                        })),
                        new Label(
                                "Eccezioni per altezza nominale · fuori dalle fasce valgono i"
                                        + " sovramateriali comuni"),
                        ranges,
                        new HBox(
                                8,
                                button(
                                        "Aggiungi fascia",
                                        () -> {
                                            ranges.getItems()
                                                    .add(new EditRow(0, 0, "", "0", "0", "0", "0"));
                                            changed();
                                        }),
                                button(
                                        "Rimuovi fascia",
                                        () -> {
                                            var row = ranges.getSelectionModel().getSelectedItem();
                                            if (row != null) {
                                                ranges.getItems().remove(row);
                                                changed();
                                            }
                                        })),
                        applyModel,
                        new Label(
                                "Le fasce non devono sovrapporsi. Numero di strati e composizione"
                                        + " devono essere compatibili."));
        ranges.setPrefHeight(180);
        heights.setPrefHeight(210);
        ranges.setId("scenarios-model-ranges");
        heights.setId("scenarios-model-layers");
        VBox transformPane =
                new VBox(
                        10,
                        new Label("Prodotto"),
                        transformProduct,
                        field("Trasformazione comune scenario €/articolo", common),
                        new Label(
                                "Eccezione vuota: eredita il costo comune. Zero: costo esplicito."),
                        transforms,
                        button("Applica trasformazione allo scenario", this::applyTransform),
                        button(
                                "Ripristina trasformazione del prodotto",
                                () -> {
                                    var p = transformProduct.getValue();
                                    if (p != null) {
                                        var map = new HashMap<>(draft.transforms());
                                        map.remove(p.id());
                                        change(
                                                draft.state(
                                                        draft.name(),
                                                        draft.notes(),
                                                        draft.archived(),
                                                        draft.selection(),
                                                        draft.products(),
                                                        draft.prices(),
                                                        draft.recipes(),
                                                        draft.models(),
                                                        map));
                                    }
                                }));
        recipePane.disableProperty().bind(recipeSource.valueProperty().isNull());
        modelPane.disableProperty().bind(modelSource.valueProperty().isNull());
        transformPane.disableProperty().bind(transformProduct.valueProperty().isNull());
        String[] names = {
            "Prezzi polveri", "Ricette multistrato", "Modelli disco", "Trasformazione"
        };
        VBox[] panes = {pricePane, recipePane, modelPane, transformPane};
        for (int i = 0; i < names.length; i++) {
            panes[i].setStyle("-fx-padding: 14;");
            ScrollPane scroll = new ScrollPane(panes[i]);
            scroll.setFitToWidth(true);
            Tab tab = new Tab(names[i], scroll);
            tab.setClosable(false);
            tabs.getTabs().add(tab);
        }
        recipeSource.setOnAction(
                e -> {
                    if (!rendering && recipeSource.getValue() != null) {
                        if (discard()) loadRecipe(recipeSource.getValue());
                        else {
                            rendering = true;
                            recipeSource.setValue(shownRecipe);
                            rendering = false;
                        }
                    }
                });
        modelSource.setOnAction(
                e -> {
                    if (!rendering && modelSource.getValue() != null) {
                        if (discard()) loadModel(modelSource.getValue());
                        else {
                            rendering = true;
                            modelSource.setValue(shownModel);
                            rendering = false;
                        }
                    }
                });
        transformProduct.setOnAction(
                e -> {
                    if (!rendering && transformProduct.getValue() != null) {
                        if (discard()) loadTransform(transformProduct.getValue());
                        else {
                            rendering = true;
                            transformProduct.setValue(shownProduct);
                            rendering = false;
                        }
                    }
                });
        tabs.getSelectionModel()
                .selectedIndexProperty()
                .addListener(
                        (o, b, a) -> {
                            if (rendering || !pending.get()) return;
                            if (discard()) {
                                int target = a.intValue();
                                show(draft, valued);
                                tabs.getSelectionModel().select(target);
                            } else {
                                rendering = true;
                                tabs.getSelectionModel().select(b.intValue());
                                rendering = false;
                            }
                        });
    }

    public void onApplied(Consumer<Draft> action) {
        applied = action;
    }

    private static Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(e -> action.run());
        return button;
    }

    private static VBox field(String text, TextField field) {
        return new VBox(5, new Label(text), field);
    }

    private void changed() {
        if (rendering) return;
        pending.set(true);
        feedback.setText(
                "Modifiche non applicate: premi Applica nella scheda prima di salvare o"
                        + " confrontare.");
        layers.refresh();
        Integer n = layers.getSelectionModel().getSelectedItem();
        if (n != null)
            recipeTotal.setText("Strato " + n + ": " + layerTotal(n) + "% · Obiettivo 100%");
    }

    public boolean discard() {
        if (!pending.get()) return true;
        if (!confirm(
                getScene() == null ? null : getScene().getWindow(),
                "Scartare le modifiche tecniche non ancora applicate?")) return false;
        pending.set(false);
        return true;
    }

    private void change(Draft changed) {
        pending.set(false);
        feedback.setText("");
        applied.accept(changed);
    }

    private void error(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            feedback.setText(e.getMessage());
        }
    }

    public void show(Draft draft, Catalogue valued) {
        this.draft = draft;
        this.valued = valued;
        rendering = true;
        try {
            var previousRecipe = recipeSource.getValue();
            var previousModel = modelSource.getValue();
            var previousProduct = transformProduct.getValue();
            powders.clear();
            draft.base().powders().forEach(p -> powders.put(p.id(), p));
            for (Recipe r : draft.base().catalogue().recipes().values())
                for (var layer : r.layers().values())
                    for (Ingredient p : layer)
                        powders.putIfAbsent(
                                p.powderId(),
                                new Powder(
                                        p.powderId(),
                                        p.code(),
                                        "Scheda della fotografia",
                                        "ALTRE",
                                        p.technicalVersion()));
            draft.prices()
                    .keySet()
                    .forEach(
                            id ->
                                    powders.putIfAbsent(
                                            id,
                                            new Powder(
                                                    id,
                                                    "#" + id,
                                                    "Collegamento da verificare",
                                                    "ALTRE",
                                                    0)));
            priceRows.clear();
            for (Powder p :
                    powders.values().stream().sorted(Comparator.comparing(Powder::code)).toList()) {
                var change = draft.prices().get(p.id());
                var price = valued.prices().get(p.id());
                priceRows.add(
                        new EditRow(
                                p.id(),
                                0,
                                p.color(),
                                p.code() + " · " + p.name(),
                                price == null ? "—" : edit(price.price()),
                                change == null
                                        ? "Invariato"
                                        : change.mode() == PriceMode.PERCENT
                                                ? "Variazione %"
                                                : "Prezzo €/kg",
                                change == null ? "" : edit(change.value()),
                                ""));
            }
            filterPrices();
            priceOutput();
            var sourceSpecs = sourceSpecs();
            Set<Long> recipeIds = new HashSet<>(), modelIds = new HashSet<>();
            sourceSpecs.forEach(
                    s -> {
                        recipeIds.add(s.compositionId());
                        modelIds.add(s.modelId());
                    });
            recipeSource.setConverter(
                    new javafx.util.StringConverter<>() {
                        public String toString(Recipe r) {
                            return r == null ? "" : recipeLabel(r);
                        }

                        public Recipe fromString(String s) {
                            return null;
                        }
                    });
            modelSource.setConverter(
                    new javafx.util.StringConverter<>() {
                        public String toString(Model m) {
                            return m == null
                                    ? ""
                                    : m.code()
                                            + " · v"
                                            + m.version()
                                            + " · "
                                            + m.layers().size()
                                            + " strati";
                        }

                        public Model fromString(String s) {
                            return null;
                        }
                    });
            values(
                    recipeSource,
                    recipeIds.stream()
                            .map(id -> draft.base().catalogue().recipes().get(id))
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparing(this::recipeLabel))
                            .toList(),
                    previousRecipe);
            values(
                    modelSource,
                    modelIds.stream()
                            .map(id -> draft.base().catalogue().models().get(id))
                            .filter(Objects::nonNull)
                            .sorted(
                                    Comparator.comparing(Model::code)
                                            .thenComparingInt(Model::version))
                            .toList(),
                    previousModel);
            values(
                    transformProduct,
                    draft.base().catalogue().products().stream()
                            .filter(p -> draft.products().contains(p.id()))
                            .toList(),
                    previousProduct);
            loadRecipe(recipeSource.getValue());
            loadModel(modelSource.getValue());
            loadTransform(transformProduct.getValue());
            pending.set(false);
            feedback.setText(
                    "Doppio clic sulle celle per modificare. Applica una scheda per aggiornare"
                            + " l'anteprima.");
        } finally {
            rendering = false;
        }
    }

    private List<Specification> sourceSpecs() {
        return draft.base().catalogue().articles().stream()
                .filter(a -> draft.products().contains(a.productId()))
                .flatMap(
                        a ->
                                draft
                                        .base()
                                        .catalogue()
                                        .specifications()
                                        .getOrDefault(a.id(), List.of())
                                        .stream()
                                        .filter(s -> Objects.equals(a.preferredId(), s.id())))
                .toList();
    }

    private String recipeLabel(Recipe r) {
        long p = draft.base().recipeProducts().getOrDefault(r.id(), 0L);
        String product =
                draft.base().catalogue().products().stream()
                        .filter(v -> v.id() == p)
                        .map(Product::code)
                        .findFirst()
                        .orElse("Prodotto #" + p);
        return product
                + " · composizione v"
                + r.version()
                + " · "
                + r.layers().size()
                + " strati · ID "
                + r.id();
    }

    private void filterPrices() {
        String query = priceSearch.getText().strip().toLowerCase(Locale.ROOT);
        prices.setItems(
                new FilteredList<>(
                        priceRows,
                        r ->
                                r.get(0).toLowerCase(Locale.ROOT).contains(query)
                                        && (color.getValue() == null
                                                || color.getValue().equals("Tutte")
                                                || color.getValue().equals(r.label))));
    }

    private void priceOutput() {
        for (EditRow row : priceRows) {
            try {
                var p = valued.prices().get(row.id);
                BigDecimal base = p == null ? null : p.price();
                BigDecimal value =
                        row.get(2).equals("Invariato")
                                ? base
                                : new PriceChange(
                                                row.get(2).equals("Variazione %")
                                                        ? PriceMode.PERCENT
                                                        : PriceMode.PRICE,
                                                decimal(row.get(3), "Prezzo", false))
                                        .apply(base);
                row.cells[4].set(PriceFormats.number(value, 2, 8));
            } catch (RuntimeException e) {
                row.cells[4].set("Da correggere");
            }
        }
    }

    private void priceChanged() {
        priceOutput();
        changed();
    }

    private void batch() {
        error(
                () -> {
                    var percent = decimal(batchValue.getText(), "Variazione %", false);
                    new PriceChange(PriceMode.PERCENT, percent);
                    for (EditRow row : prices.getItems()) {
                        row.cells[2].set("Variazione %");
                        row.cells[3].set(edit(percent));
                    }
                    priceChanged();
                });
    }

    private void applyPrices() {
        error(
                () -> {
                    var map = new HashMap<Long, PriceChange>();
                    for (EditRow row : priceRows)
                        if (!row.get(2).equals("Invariato"))
                            map.put(
                                    row.id,
                                    new PriceChange(
                                            row.get(2).equals("Variazione %")
                                                    ? PriceMode.PERCENT
                                                    : PriceMode.PRICE,
                                            decimal(row.get(3), row.get(0), false)));
                    change(
                            draft.state(
                                    draft.name(),
                                    draft.notes(),
                                    draft.archived(),
                                    draft.selection(),
                                    draft.products(),
                                    map,
                                    draft.recipes(),
                                    draft.models(),
                                    draft.transforms()));
                });
    }

    private EditRow ingredient(Ingredient p) {
        return new EditRow(
                p.powderId(),
                p.technicalVersion(),
                p.code(),
                p.code() + " · v" + p.technicalVersion(),
                edit(p.percent()));
    }

    private void loadRecipe(Recipe source) {
        shownRecipe = source;
        boolean before = rendering;
        rendering = true;
        try {
            recipeRows.clear();
            recipeTemplate =
                    source == null ? null : draft.recipes().getOrDefault(source.id(), source);
            if (recipeTemplate != null)
                recipeTemplate
                        .layers()
                        .forEach(
                                (n, list) ->
                                        recipeRows.put(
                                                n,
                                                FXCollections.observableArrayList(
                                                        list.stream()
                                                                .map(this::ingredient)
                                                                .toList())));
            showLayers(1);
            recipeAffected.setText(
                    source == null
                            ? "Nessuna composizione nelle configurazioni predefinite dell'ambito."
                            : affectedText(true, source.id())
                                    + "\nScenario: "
                                    + recipeLabel(recipeTemplate)
                                    + (draft.recipes().containsKey(source.id())
                                            ? " · copia locale"
                                            : " · originale"));
        } finally {
            rendering = before;
        }
    }

    private void showLayers(int keep) {
        layers.getItems().setAll(recipeRows.keySet());
        layers.getSelectionModel().select(Integer.valueOf(keep));
        if (layers.getSelectionModel().getSelectedItem() == null && !layers.getItems().isEmpty())
            layers.getSelectionModel().selectFirst();
        if (layers.getItems().isEmpty()) ingredients.setItems(FXCollections.observableArrayList());
        else if (layers.getSelectionModel().getSelectedItem() != null)
            ingredients.setItems(recipeRows.get(layers.getSelectionModel().getSelectedItem()));
    }

    private String layerTotal(int n) {
        try {
            return edit(
                    recipeRows.getOrDefault(n, FXCollections.observableArrayList()).stream()
                            .map(r -> decimal(r.get(1), "Percentuale", false))
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private void resetRecipe() {
        if (recipeSource.getValue() == null || !discard()) return;
        var map = new HashMap<>(draft.recipes());
        map.remove(recipeSource.getValue().id());
        change(
                draft.state(
                        draft.name(),
                        draft.notes(),
                        draft.archived(),
                        draft.selection(),
                        draft.products(),
                        draft.prices(),
                        map,
                        draft.models(),
                        draft.transforms()));
    }

    private void resetModel() {
        if (modelSource.getValue() == null || !discard()) return;
        var map = new HashMap<>(draft.models());
        map.remove(modelSource.getValue().id());
        change(
                draft.state(
                        draft.name(),
                        draft.notes(),
                        draft.archived(),
                        draft.selection(),
                        draft.products(),
                        draft.prices(),
                        draft.recipes(),
                        map,
                        draft.transforms()));
    }

    private void applyRecipe() {
        error(
                () -> {
                    Recipe source = recipeSource.getValue();
                    if (source == null)
                        throw new IllegalArgumentException("Seleziona una composizione.");
                    var layers = new TreeMap<Integer, List<Ingredient>>();
                    for (var entry : recipeRows.entrySet()) {
                        var list = new ArrayList<Ingredient>();
                        for (EditRow row : entry.getValue()) {
                            var percent = decimal(row.get(1), row.get(0), false);
                            if (percent.signum() != 0)
                                list.add(new Ingredient(row.id, row.label, row.version, percent));
                        }
                        layers.put(entry.getKey(), list);
                    }
                    Recipe recipe =
                            new Recipe(
                                    recipeTemplate.id(),
                                    recipeTemplate.version(),
                                    "SCENARIO",
                                    layers);
                    ScenarioEngine.validateRecipe(recipe);
                    var map = new HashMap<>(draft.recipes());
                    if (recipe.layers().equals(source.layers()) && recipe.id() == source.id())
                        map.remove(source.id());
                    else map.put(source.id(), recipe);
                    change(
                            draft.state(
                                    draft.name(),
                                    draft.notes(),
                                    draft.archived(),
                                    draft.selection(),
                                    draft.products(),
                                    draft.prices(),
                                    map,
                                    draft.models(),
                                    draft.transforms()));
                });
    }

    private void addPowder() {
        Integer n = layers.getSelectionModel().getSelectedItem();
        if (n == null) return;
        var selected = choose("Aggiungi polvere", draft.base().powders(), Powder::toString);
        if (selected != null) {
            recipeRows
                    .get(n)
                    .add(
                            ingredient(
                                    new Ingredient(
                                            selected.id(),
                                            selected.code(),
                                            selected.technicalVersion(),
                                            BigDecimal.ZERO)));
            changed();
        }
    }

    private <T> T choose(
            String title, List<T> values, java.util.function.Function<T, String> text) {
        Dialog<T> dialog = new Dialog<>();
        if (getScene() != null) dialog.initOwner(getScene().getWindow());
        dialog.setTitle(title);
        TextField search = new TextField();
        search.setPromptText("Cerca per codice o nome");
        ListView<T> list = new ListView<>();
        list.setPrefSize(630, 300);
        list.setCellFactory(
                v ->
                        new ListCell<>() {
                            protected void updateItem(T item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty || item == null ? null : text.apply(item));
                            }
                        });
        list.getItems().setAll(values);
        search.textProperty()
                .addListener(
                        (o, b, a) ->
                                list.getItems()
                                        .setAll(
                                                values.stream()
                                                        .filter(
                                                                v ->
                                                                        text.apply(v)
                                                                                .toLowerCase(
                                                                                        Locale.ROOT)
                                                                                .contains(
                                                                                        a
                                                                                                .toLowerCase(
                                                                                                        Locale
                                                                                                                .ROOT)))
                                                        .toList()));
        dialog.getDialogPane().setContent(new VBox(10, search, list));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(
                b -> b == ButtonType.OK ? list.getSelectionModel().getSelectedItem() : null);
        return dialog.showAndWait().orElse(null);
    }

    private void recipeAlternative() {
        Recipe source = recipeSource.getValue();
        if (source == null) return;
        Long product = draft.base().recipeProducts().get(source.id());
        Recipe choice =
                choose(
                        "Composizioni approvate del prodotto",
                        draft.base().catalogue().recipes().values().stream()
                                .filter(
                                        r ->
                                                r.state().equals("APPROVATA")
                                                        && Objects.equals(
                                                                product,
                                                                draft.base()
                                                                        .recipeProducts()
                                                                        .get(r.id())))
                                .sorted(Comparator.comparingInt(Recipe::version).reversed())
                                .toList(),
                        this::recipeLabel);
        if (choice != null && discard()) {
            loadRecipe(source);
            recipeTemplate = choice;
            recipeRows.clear();
            choice.layers()
                    .forEach(
                            (n, list) ->
                                    recipeRows.put(
                                            n,
                                            FXCollections.observableArrayList(
                                                    list.stream().map(this::ingredient).toList())));
            showLayers(1);
            changed();
        }
    }

    private void copyLayer() {
        Integer target = layers.getSelectionModel().getSelectedItem();
        if (target == null) return;
        Integer source =
                choose(
                        "Copia miscela da strato",
                        recipeRows.keySet().stream().filter(n -> !n.equals(target)).toList(),
                        n -> "Strato " + n);
        if (source != null) {
            recipeRows.put(
                    target,
                    FXCollections.observableArrayList(
                            recipeRows.get(source).stream()
                                    .map(
                                            r ->
                                                    new EditRow(
                                                            r.id, r.version, r.label, r.get(0),
                                                            r.get(1)))
                                    .toList()));
            ingredients.setItems(recipeRows.get(target));
            changed();
        }
    }

    private void removeLayer() {
        Integer selected = layers.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        recipeRows.remove(selected);
        var lists = new ArrayList<>(recipeRows.values());
        recipeRows.clear();
        for (int i = 0; i < lists.size(); i++) recipeRows.put(i + 1, lists.get(i));
        showLayers(1);
        changed();
    }

    private void moveLayer(int direction) {
        Integer n = layers.getSelectionModel().getSelectedItem();
        if (n == null || !recipeRows.containsKey(n + direction)) return;
        var list = recipeRows.get(n);
        recipeRows.put(n, recipeRows.get(n + direction));
        recipeRows.put(n + direction, list);
        showLayers(n + direction);
        changed();
    }

    private void loadModel(Model source) {
        shownModel = source;
        boolean before = rendering;
        rendering = true;
        try {
            modelTemplate =
                    source == null ? null : draft.models().getOrDefault(source.id(), source);
            grams.setText(edit(modelTemplate == null ? null : modelTemplate.gramsPerMm()));
            top.setText(edit(modelTemplate == null ? null : modelTemplate.top()));
            bottom.setText(edit(modelTemplate == null ? null : modelTemplate.bottom()));
            heights.getItems().clear();
            ranges.getItems().clear();
            if (modelTemplate != null) {
                modelTemplate.layers().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(
                                e ->
                                        heights.getItems()
                                                .add(
                                                        new EditRow(
                                                                0,
                                                                0,
                                                                "",
                                                                e.getKey().toString(),
                                                                edit(e.getValue()))));
                for (Range r : modelTemplate.ranges())
                    ranges.getItems()
                            .add(
                                    new EditRow(
                                            0,
                                            0,
                                            "",
                                            edit(r.from()),
                                            edit(r.to()),
                                            edit(r.top()),
                                            edit(r.bottom())));
            }
            modelAffected.setText(
                    source == null
                            ? "Nessun modello nell'ambito scelto."
                            : affectedText(false, source.id())
                                    + "\nScenario: "
                                    + modelTemplate.code()
                                    + " · v"
                                    + modelTemplate.version()
                                    + (draft.models().containsKey(source.id())
                                            ? " · copia locale"
                                            : " · originale"));
        } finally {
            rendering = before;
        }
    }

    private void modelAlternative() {
        Model source = modelSource.getValue();
        if (source == null) return;
        Model choice =
                choose(
                        "Modelli approvati nella fotografia",
                        draft.base().catalogue().models().values().stream()
                                .filter(m -> m.state().equals("APPROVATA"))
                                .sorted(
                                        Comparator.comparing(Model::code)
                                                .thenComparingInt(Model::version))
                                .toList(),
                        m ->
                                m.code()
                                        + " · v"
                                        + m.version()
                                        + " · "
                                        + m.layers().size()
                                        + " strati");
        if (choice != null && discard()) {
            modelTemplate = choice;
            boolean b = rendering;
            rendering = true;
            grams.setText(edit(choice.gramsPerMm()));
            top.setText(edit(choice.top()));
            bottom.setText(edit(choice.bottom()));
            heights.getItems().clear();
            choice.layers().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(
                            e ->
                                    heights.getItems()
                                            .add(
                                                    new EditRow(
                                                            0,
                                                            0,
                                                            "",
                                                            e.getKey().toString(),
                                                            edit(e.getValue()))));
            ranges.getItems()
                    .setAll(
                            choice.ranges().stream()
                                    .map(
                                            r ->
                                                    new EditRow(
                                                            0,
                                                            0,
                                                            "",
                                                            edit(r.from()),
                                                            edit(r.to()),
                                                            edit(r.top()),
                                                            edit(r.bottom())))
                                    .toList());
            rendering = b;
            changed();
        }
    }

    private void applyModel() {
        error(
                () -> {
                    Model source = modelSource.getValue();
                    if (source == null) throw new IllegalArgumentException("Seleziona un modello.");
                    var layer = new TreeMap<Integer, BigDecimal>();
                    for (int i = 0; i < heights.getItems().size(); i++)
                        layer.put(
                                i + 1,
                                decimal(heights.getItems().get(i).get(1), "Quota strato", false));
                    var bands =
                            ranges.getItems().stream()
                                    .map(
                                            r ->
                                                    new Range(
                                                            decimal(r.get(0), "Da mm", false),
                                                            decimal(r.get(1), "A mm", false),
                                                            decimal(
                                                                    r.get(2),
                                                                    "Superiore mm",
                                                                    false),
                                                            decimal(
                                                                    r.get(3),
                                                                    "Inferiore mm",
                                                                    false)))
                                    .sorted(Comparator.comparing(Range::from))
                                    .toList();
                    Model model =
                            new Model(
                                    modelTemplate.id(),
                                    modelTemplate.code(),
                                    modelTemplate.version(),
                                    "SCENARIO",
                                    decimal(grams.getText(), "g/mm", false),
                                    decimal(top.getText(), "Superiore", false),
                                    decimal(bottom.getText(), "Inferiore", false),
                                    layer,
                                    bands);
                    ScenarioEngine.validateModel(model);
                    var map = new HashMap<>(draft.models());
                    if (model.id() == source.id()
                            && model.layers().equals(source.layers())
                            && model.ranges().equals(source.ranges())
                            && Objects.equals(model.gramsPerMm(), source.gramsPerMm())
                            && Objects.equals(model.top(), source.top())
                            && Objects.equals(model.bottom(), source.bottom()))
                        map.remove(source.id());
                    else map.put(source.id(), model);
                    change(
                            draft.state(
                                    draft.name(),
                                    draft.notes(),
                                    draft.archived(),
                                    draft.selection(),
                                    draft.products(),
                                    draft.prices(),
                                    draft.recipes(),
                                    map,
                                    draft.transforms()));
                });
    }

    private void loadTransform(Product product) {
        shownProduct = product;
        boolean before = rendering;
        rendering = true;
        try {
            transforms.getItems().clear();
            if (product == null) {
                common.clear();
                return;
            }
            Parameters base = draft.base().catalogue().parameters(product.id()),
                    value = draft.transforms().getOrDefault(product.id(), base);
            common.setText(edit(value.commonCost()));
            for (Article a : draft.base().catalogue().articles())
                if (a.productId() == product.id())
                    transforms
                            .getItems()
                            .add(
                                    new EditRow(
                                            a.id(),
                                            0,
                                            a.code(),
                                            a.code(),
                                            edit(base.forArticle(a.id())),
                                            edit(value.overrides().get(a.id()))));
        } finally {
            rendering = before;
        }
    }

    private void applyTransform() {
        error(
                () -> {
                    Product product = transformProduct.getValue();
                    if (product == null)
                        throw new IllegalArgumentException("Seleziona un prodotto.");
                    var overrides = new HashMap<Long, BigDecimal>();
                    for (EditRow row : transforms.getItems()) {
                        var value = decimal(row.get(2), row.get(0), true);
                        if (value != null) overrides.put(row.id, value);
                    }
                    Parameters parameters =
                            new Parameters(
                                    draft.base().catalogue().parameters(product.id()).version(),
                                    decimal(common.getText(), "Trasformazione comune", true),
                                    overrides);
                    var map = new HashMap<>(draft.transforms());
                    if (parameters.sameValues(draft.base().catalogue().parameters(product.id())))
                        map.remove(product.id());
                    else map.put(product.id(), parameters);
                    change(
                            draft.state(
                                    draft.name(),
                                    draft.notes(),
                                    draft.archived(),
                                    draft.selection(),
                                    draft.products(),
                                    draft.prices(),
                                    draft.recipes(),
                                    draft.models(),
                                    map));
                });
    }

    private String affectedText(boolean recipe, long id) {
        var specs =
                sourceSpecs().stream()
                        .filter(s -> recipe ? s.compositionId() == id : s.modelId() == id)
                        .toList();
        return specs.size()
                + " configurazioni predefinite coinvolte, nei prodotti selezionati. Le altre"
                + " configurazioni e i dati reali restano invariati.";
    }

    private void affected(boolean recipe) {
        Long id =
                recipe
                        ? recipeSource.getValue() == null ? null : recipeSource.getValue().id()
                        : modelSource.getValue() == null ? null : modelSource.getValue().id();
        if (id == null) return;
        var articles = new HashMap<Long, Article>();
        draft.base().catalogue().articles().forEach(a -> articles.put(a.id(), a));
        String text =
                sourceSpecs().stream()
                        .filter(s -> recipe ? s.compositionId() == id : s.modelId() == id)
                        .map(
                                s ->
                                        articles.get(s.articleId()).code()
                                                + " · configurazione v"
                                                + s.version()
                                                + " · ID "
                                                + s.id())
                        .collect(java.util.stream.Collectors.joining("\n"));
        message(
                getScene() == null ? null : getScene().getWindow(),
                "Configurazioni coinvolte nello scenario",
                text);
    }
}

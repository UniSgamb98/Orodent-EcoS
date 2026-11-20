package com.orodent.ecos.ton.features.diskmodels.view;

import static com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.*;

/** Each parameter row owns its dose and independent mould comparison panel. */
public final class ModelDiametersEditor extends VBox {
    private final List<Row> rows = new ArrayList<>();
    private final VBox rowBox = new VBox(12),
            comparisons = new VBox(14),
            comparisonBody = new VBox();
    private final TabPane selected = new TabPane();
    private static final double[] WIDTHS = {23, 54, 23};
    private final Button
            add = TechnicalUi.button("+ Aggiungi diametro", "ton-model-add-diameter", false),
            refresh =
                    TechnicalUi.button("Aggiorna elenco stampi", "ton-model-moulds-refresh", false);
    private final Label legacy =
            TechnicalUi.label(
                    "Diametro articolo non associato nella versione storica. In una nuova versione"
                            + " specifica il diametro nominale: non viene dedotto dallo stampo.",
                    "muted-label");
    private List<Mould> available = List.of();
    private final Runnable changed;
    private boolean frozen, updating;
    private int sequence;

    public ModelDiametersEditor(Runnable changed) {
        super(12);
        this.changed = changed;
        setMinWidth(0);
        setId("ton-model-diameters");
        selected.setId("ton-model-diameter-group");
        selected.setMinWidth(0);
        selected.setMinHeight(0);
        selected.setMaxHeight(Region.USE_PREF_SIZE);
        selected.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        selected.getStyleClass().addAll("ton-editor-tabs", "ton-model-diameter-tabs");
        selected.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, a, b) -> {
                            var row = selectedRow();
                            if (row != null) comparisonBody.getChildren().setAll(row.moulds);
                            else comparisonBody.getChildren().clear();
                            if (!updating) changed.run();
                        });
        HBox.setHgrow(selected, Priority.ALWAYS);
        var actions = new HBox(12, selected, refresh);
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        comparisons.getChildren().addAll(actions, comparisonBody);
        comparisons.setMinWidth(0);
        getChildren()
                .addAll(
                        legacy,
                        ModelInputRows.headings(
                                "ton-model-diameter-headings",
                                WIDTHS,
                                "Diametro articolo · mm *",
                                "Stampo di riferimento *",
                                "Peso · g/mm *"),
                        rowBox,
                        add);
        add.setOnAction(
                e -> {
                    addRow(null, null, null, null);
                    updateSelector(rows.getLast());
                    changed.run();
                });
    }

    private final class Row {
        final TextField diameter, grams;
        final ModelMouldsPane moulds;
        final HBox box;
        final Tab tab;
        final Button remove;
        final BigDecimal historical;

        Row(BigDecimal nominal, BigDecimal dose, Moulds saved, BigDecimal historical, int index) {
            this.historical = historical;
            String suffix = index == 1 ? "" : "-" + index;
            diameter =
                    TechnicalUi.input(
                            "ton-model-article-diameter" + suffix, "Diametro articolo · mm");
            grams = TechnicalUi.input("ton-model-grams" + suffix, "Peso per millimetro · g/mm");
            diameter.setText(TechnicalUi.number(nominal));
            grams.setText(TechnicalUi.number(dose));
            moulds = new ModelMouldsPane(this::onChange);
            moulds.reference().setId("ton-model-reference-mould" + suffix);
            moulds.show(saved, frozen, historical);
            moulds.refresh().setId(null);
            TechnicalUi.visible(moulds.refresh(), false);
            moulds.choices(available);
            moulds.grams(dose);
            remove = TechnicalUi.button("×", "ton-model-remove-diameter" + suffix, false);
            remove.setAccessibleText("Rimuovi diametro articolo");
            remove.setTooltip(new Tooltip("Rimuovi diametro"));
            box =
                    ModelInputRows.row(
                            "ton-model-diameter-row" + suffix,
                            WIDTHS,
                            List.of(diameter, moulds.reference(), grams),
                            remove);
            tab = new Tab();
            tab.setUserData(this);
            tab.textProperty()
                    .bind(
                            javafx.beans.binding.Bindings.createStringBinding(
                                    this::caption, diameter.textProperty()));
            remove.setOnAction(
                    e -> {
                        if (frozen || rows.size() == 1) return;
                        int position = rows.indexOf(this);
                        rows.remove(this);
                        rowBox.getChildren().remove(box);
                        moulds.dispose();
                        updateSelector(rows.get(Math.min(position, rows.size() - 1)));
                        changed.run();
                    });
            diameter.setDisable(frozen);
            grams.setDisable(frozen);
            remove.setDisable(frozen);
            diameter.textProperty().addListener((o, a, b) -> onChange());
            grams.textProperty().addListener((o, a, b) -> onChange());
            for (var control : List.of(diameter, grams, moulds.reference()))
                control.focusedProperty()
                        .addListener(
                                (o, a, b) -> {
                                    if (b && !updating) selected.getSelectionModel().select(tab);
                                });
        }

        void onChange() {
            if (updating) return;
            moulds.grams(TechnicalUi.preview(grams.getText()));
            changed.run();
        }

        String caption() {
            return diameter.getText().isBlank()
                    ? "Diametro articolo da specificare"
                    : "Ø articolo " + diameter.getText() + " mm";
        }

        DiameterGroup data() {
            return new DiameterGroup(
                    TechnicalUi.decimal(diameter.getText(), "Diametro articolo"),
                    TechnicalUi.decimal(grams.getText(), "Peso per millimetro"),
                    moulds.selection());
        }
    }

    private void addRow(BigDecimal nominal, BigDecimal dose, Moulds moulds, BigDecimal historical) {
        var row = new Row(nominal, dose, moulds, historical, ++sequence);
        rows.add(row);
        rowBox.getChildren().add(row.box);
        for (var r : rows) r.remove.setDisable(frozen || rows.size() <= 1);
    }

    private void updateSelector(Row prefer) {
        selected.getTabs().setAll(rows.stream().map(r -> r.tab).toList());
        selected.getSelectionModel().select(prefer.tab);
        for (var r : rows) r.remove.setDisable(frozen || rows.size() <= 1);
    }

    public void show(Draft data, boolean frozen) {
        updating = true;
        try {
            rows.forEach(r -> r.moulds.dispose());
            rows.clear();
            rowBox.getChildren().clear();
            comparisonBody.getChildren().clear();
            selected.getTabs().clear();
            sequence = 0;
            available = List.of();
            this.frozen = frozen;
            boolean historical = data != null && data.diameters().isEmpty();
            TechnicalUi.visible(legacy, historical);
            TechnicalUi.visible(add, !frozen);
            refresh.setDisable(frozen);
            if (data != null && !historical)
                for (var g : data.diameters())
                    addRow(g.articleDiameter(), g.grams(), g.moulds(), null);
            else
                addRow(
                        null,
                        data == null ? null : data.grams(),
                        data == null ? null : data.moulds(),
                        data == null ? null : data.diameter());
            updateSelector(rows.getFirst());
        } finally {
            updating = false;
        }
    }

    public void choices(List<Mould> values) {
        available = List.copyOf(values);
        for (var row : rows) row.moulds.choices(available);
    }

    public List<DiameterGroup> data() {
        return rows.stream().map(Row::data).toList();
    }

    public List<String> fingerprint() {
        var values = new ArrayList<String>();
        for (var row : rows) {
            values.add("DIAMETER:" + row.diameter.getText());
            values.add("GRAMS:" + row.grams.getText());
            values.addAll(row.moulds.fingerprint());
        }
        return values;
    }

    private Row selectedRow() {
        var tab = selected.getSelectionModel().getSelectedItem();
        return tab == null ? null : (Row) tab.getUserData();
    }

    public BigDecimal previewDiameter() {
        var row = selectedRow();
        return row == null
                ? null
                : row.moulds.reference().getValue() == null
                        ? row.historical
                        : row.moulds.reference().getValue().diameter();
    }

    public VBox comparisons() {
        return comparisons;
    }

    public Button refresh() {
        return refresh;
    }

    public ModelMouldsPane selectedMoulds() {
        return selectedRow().moulds;
    }

    public void dispose() {
        rows.forEach(r -> r.moulds.dispose());
        add.setOnAction(null);
        refresh.setOnAction(null);
    }
}

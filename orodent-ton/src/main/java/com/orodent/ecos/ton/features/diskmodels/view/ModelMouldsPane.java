package com.orodent.ecos.ton.features.diskmodels.view;

import static com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import com.orodent.ecos.ton.features.diskmodels.service.MouldComparison;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.*;
import java.util.*;
import java.util.function.Function;

/** Manual admission only. Comparison numbers never select moulds or modify standard doses. */
public final class ModelMouldsPane extends VBox {
    private record Row(Mould mould, boolean reference, boolean current) {}

    private final ComboBox<Mould> reference = new ComboBox<>();
    private final TableView<Row> table = new TableView<>();
    private final Button refresh =
            TechnicalUi.button("Aggiorna elenco stampi", "ton-model-moulds-refresh", false);
    private final Label referenceLabel = TechnicalUi.label("", "muted-label"),
            count = TechnicalUi.label("", "muted-label");
    private final Map<Long, Mould> admitted = new TreeMap<>();
    private List<Mould> available = List.of();
    private boolean updating, frozen;
    private BigDecimal grams;
    private final Runnable changed;
    private final javafx.beans.value.ChangeListener<Mould> referenceChanged;

    public ModelMouldsPane(Runnable changed) {
        super(14);
        this.changed = changed;
        setMinWidth(0);
        getStyleClass().add("ton-technical-panel");
        reference.setId("ton-model-reference-mould");
        reference.setMaxWidth(Double.MAX_VALUE);
        reference.setMinWidth(0);
        reference.setPromptText("Seleziona lo stampo di riferimento");
        reference.setAccessibleText("Stampo di riferimento del modello");
        referenceChanged =
                (o, previous, value) -> {
                    if (updating) return;
                    var selected = reference.getValue();
                    if (selected != null) admitted.remove(selected.id());
                    render();
                    changed.run();
                };
        reference.valueProperty().addListener(referenceChanged);
        table.setId("ton-model-moulds-table");
        table.setMinHeight(220);
        table.setPrefHeight(440);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        var choice = new TableColumn<Row, Row>("Ammesso");
        choice.setMinWidth(85);
        choice.setMaxWidth(100);
        choice.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        choice.setCellFactory(
                c ->
                        new TableCell<>() {
                            private final CheckBox check = new CheckBox();

                            {
                                check.setOnAction(
                                        e -> {
                                            var row = getItem();
                                            if (row == null || frozen || row.reference()) return;
                                            if (check.isSelected())
                                                admitted.put(row.mould().id(), row.mould());
                                            else admitted.remove(row.mould().id());
                                            render();
                                            changed.run();
                                        });
                            }

                            @Override
                            protected void updateItem(Row row, boolean empty) {
                                super.updateItem(row, empty);
                                setText(null);
                                setGraphic(empty || row == null ? null : check);
                                if (empty || row == null) return;
                                check.setSelected(
                                        row.reference() || admitted.containsKey(row.mould().id()));
                                check.setDisable(
                                        frozen
                                                || row.reference()
                                                || !row.current() && !check.isSelected());
                                check.setAccessibleText("Ammetti stampo " + row.mould().code());
                            }
                        });
        table.getColumns().add(choice);
        column("Stampo", r -> r.mould().code() + " · " + r.mould().name(), 240);
        column("Ø stampo · mm", r -> TechnicalUi.number(r.mould().diameter()), 130);
        column("Δ Ø · mm", r -> difference(r, d -> d.diameterMm(), 3), 120);
        column("Δ sezione · %", r -> difference(r, d -> d.sectionPercent(), 4), 150);
        column("Δ peso · g/mm", r -> difference(r, d -> d.extraGramsPerMm(), 5), 150);
        column("Δ peso · g/100 g", r -> difference(r, d -> d.extraGramsPer100g(), 4), 165);
        column(
                "Stato",
                r ->
                        r.reference()
                                ? "Riferimento"
                                : !frozen && !r.current()
                                        ? "Dati da aggiornare"
                                        : admitted.containsKey(r.mould().id())
                                                ? "Ammesso"
                                                : "Non selezionato",
                160);
        table.setPlaceholder(
                new Label("Nessuno stampo. Registra gli stampi in Macchinari e attrezzature."));
        getChildren()
                .addAll(
                        TechnicalUi.label("Stampi ammissibili", "ton-technical-panel-title"),
                        TechnicalUi.label(
                                "Seleziona manualmente gli altri stampi ammessi per questa"
                                        + " versione. Lo stampo di riferimento è già incluso.",
                                "muted-label"),
                        referenceLabel,
                        refresh,
                        table,
                        count,
                        TechnicalUi.label(
                                "Stime geometriche a parità di altezza e densità. Un valore"
                                        + " positivo indica polvere teorica in più, uno negativo"
                                        + " polvere in meno. Le pesate standard del modello restano"
                                        + " invariate.",
                                "muted-label"));
    }

    private void column(String title, Function<Row, String> value, int width) {
        var c = new TableColumn<Row, String>(title);
        c.setPrefWidth(width);
        c.setMinWidth(85);
        c.setCellValueFactory(
                v -> new javafx.beans.property.ReadOnlyStringWrapper(value.apply(v.getValue())));
        table.getColumns().add(c);
    }

    private String difference(
            Row row, Function<MouldComparison.Difference, BigDecimal> value, int scale) {
        if (reference.getValue() == null) return "—";
        var v = value.apply(MouldComparison.between(reference.getValue(), row.mould(), grams));
        if (v == null) return "—";
        v = v.setScale(scale, RoundingMode.HALF_UP);
        return (v.signum() > 0 ? "+" : "") + TechnicalUi.number(v);
    }

    public ComboBox<Mould> reference() {
        return reference;
    }

    public Button refresh() {
        return refresh;
    }

    public void grams(BigDecimal grams) {
        this.grams = grams;
        table.refresh();
    }

    public Moulds selection() {
        if (reference.getValue() == null)
            throw invalid("Seleziona lo stampo di riferimento in Parametri.");
        return new Moulds(reference.getValue(), List.copyOf(admitted.values()));
    }

    public void show(Moulds saved, boolean frozen, BigDecimal historicalDiameter) {
        updating = true;
        try {
            this.frozen = frozen;
            admitted.clear();
            available = List.of();
            if (saved != null) saved.admitted().forEach(m -> admitted.put(m.id(), m));
            reference.setValue(saved == null ? null : saved.reference());
            reference.setDisable(frozen);
            refresh.setDisable(frozen);
            reference.setPromptText(
                    saved == null && historicalDiameter != null
                            ? "Dato storico: Ø "
                                    + TechnicalUi.number(historicalDiameter)
                                    + " mm · stampo non associato"
                            : "Seleziona lo stampo di riferimento");
            choices(List.of());
        } finally {
            updating = false;
        }
    }

    public void choices(List<Mould> values) {
        boolean before = updating;
        updating = true;
        try {
            available = List.copyOf(values);
            var selected = reference.getValue();
            var choices = new ArrayList<>(available);
            if (selected != null && !choices.contains(selected)) choices.add(selected);
            choices.sort(
                    Comparator.comparing(Mould::code, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(Mould::revision, Comparator.reverseOrder()));
            reference.getItems().setAll(choices);
            reference.setValue(selected);
            render();
        } finally {
            updating = before;
        }
    }

    private void render() {
        var ref = reference.getValue();
        referenceLabel.setText(
                ref == null
                        ? "Scegli lo stampo di riferimento in Parametri per vedere gli scostamenti."
                        : "Riferimento: " + ref);
        var current = new HashMap<Long, Mould>();
        available.forEach(m -> current.put(m.id(), m));
        var visible = new HashMap<Long, Mould>();
        if (!frozen) visible.putAll(current);
        visible.putAll(admitted);
        if (ref != null) visible.put(ref.id(), ref);
        table.getItems()
                .setAll(
                        visible.values().stream()
                                .sorted(
                                        Comparator.comparing(
                                                Mould::code, String.CASE_INSENSITIVE_ORDER))
                                .map(
                                        m ->
                                                new Row(
                                                        m,
                                                        ref != null && ref.id() == m.id(),
                                                        m.equals(current.get(m.id()))))
                                .toList());
        count.setText(
                ref == null
                        ? "Nessuno stampo di riferimento selezionato."
                        : "Stampi ammessi: "
                                + (1 + admitted.size())
                                + " · incluso il riferimento.");
    }

    public List<String> fingerprint() {
        var result = new ArrayList<String>();
        result.add("REF:" + Objects.toString(reference.getValue(), ""));
        for (var m : admitted.values()) result.add("AMMESSO:" + m.id() + ":" + m);
        return result;
    }

    public void dispose() {
        reference.valueProperty().removeListener(referenceChanged);
        refresh.setOnAction(null);
        table.getItems().clear();
    }
}

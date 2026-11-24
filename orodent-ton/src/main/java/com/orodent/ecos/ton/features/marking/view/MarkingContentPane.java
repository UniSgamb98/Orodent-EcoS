package com.orodent.ecos.ton.features.marking.view;

import static com.orodent.ecos.ton.features.marking.model.MarkingTemplates.*;

import com.orodent.ecos.core.math.FormulaProgram;
import com.orodent.ecos.database.CubatureRuleVersions.Version;
import com.orodent.ecos.ton.features.marking.model.MarkingTemplates;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Sample data never leave this pane. Only text, rule identity and formatting are saved. */
public final class MarkingContentPane extends VBox {
    private static final String ID = "ton-marking";
    public final TextArea text = new TextArea();
    public final ComboBox<Version> rule = new ComboBox<>();
    public final ComboBox<String> separator = new ComboBox<>();
    private final ComboBox<Field> fields = new ComboBox<>();
    private final Button insert = TechnicalUi.button("Inserisci campo", ID + "-insert", false),
            clear = TechnicalUi.button("Nessuna regola", ID + "-clear-rule", false);
    private final TableView<Field> formats = new TableView<>();
    private final Map<String, Integer> decimals = new TreeMap<>();
    private final TextField article = TechnicalUi.input(ID + "-sample-article", "Codice articolo"),
            product = TechnicalUi.input(ID + "-sample-product", "Codice prodotto"),
            lot = TechnicalUi.input(ID + "-sample-lot", "Lotto"),
            diameter = TechnicalUi.input(ID + "-sample-d", "D · mm"),
            height = TechnicalUi.input(ID + "-sample-h", "Hmedia · mm"),
            weight = TechnicalUi.input(ID + "-sample-p", "P · g");
    private final TextArea preview = new TextArea();
    private final Label error = TechnicalUi.label("", "ton-error-feedback");
    private Map<String, Object> values = Map.of();
    private boolean loading, editable = true;
    private String fieldsError = "";

    public MarkingContentPane() {
        super(14);
        setMinWidth(0);
        rule.setId(ID + "-rule");
        rule.setMinWidth(0);
        rule.setMaxWidth(Double.MAX_VALUE);
        rule.setPromptText("Facoltativa · scegli per usare i risultati calcolati");
        text.setId(ID + "-text");
        text.setPromptText("{codice_articolo} LOT {lotto}");
        text.setPrefRowCount(5);
        text.setWrapText(true);
        text.setStyle("-fx-font-family: monospace; -fx-font-size: 15px;");
        fields.setId(ID + "-field");
        fields.setMinWidth(0);
        fields.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(fields, Priority.ALWAYS);
        insert.setMinWidth(Region.USE_PREF_SIZE);
        clear.setMinWidth(Region.USE_PREF_SIZE);
        insert.setOnAction(
                e -> {
                    if (fields.getValue() != null) {
                        text.insertText(
                                text.getCaretPosition(), "{" + fields.getValue().name() + "}");
                        text.requestFocus();
                    }
                });
        clear.setOnAction(e -> rule.setValue(null));
        separator.setId(ID + "-separator");
        separator.getItems().setAll(".", ",");
        separator.setValue(".");
        formats.setId(ID + "-formats");
        formats.setMinHeight(125);
        formats.setFixedCellSize(42);
        formats.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TechnicalUi.column(formats, "Campo", 150, Field::name);
        var places = new TableColumn<Field, Field>("Decimali");
        places.setCellValueFactory(v -> new SimpleObjectProperty<>(v.getValue()));
        places.setSortable(false);
        places.setCellFactory(
                c ->
                        new TableCell<>() {
                            final ComboBox<Integer> choice = new ComboBox<>();

                            {
                                choice.getItems().addAll(0, 1, 2, 3, 4, 5, 6, 7, 8);
                                choice.setStyle("-fx-padding: 0 6px;");
                                choice.setMinHeight(32);
                                choice.setPrefHeight(32);
                                choice.setMaxHeight(32);
                            }

                            @Override
                            protected void updateItem(Field field, boolean empty) {
                                super.updateItem(field, empty);
                                choice.setOnAction(null);
                                setText(null);
                                if (empty || field == null) {
                                    setGraphic(null);
                                    return;
                                }
                                choice.setValue(decimals.getOrDefault(field.name(), 3));
                                choice.setDisable(!editable);
                                choice.setOnAction(
                                        e -> {
                                            if (choice.getValue() == 3)
                                                decimals.remove(field.name());
                                            else decimals.put(field.name(), choice.getValue());
                                            refreshPreview();
                                        });
                                setGraphic(choice);
                            }
                        });
        formats.getColumns().add(places);
        TechnicalUi.column(formats, "Esempio", 180, f -> formatted(f.name()));
        var formatBox = new VBox(10, TechnicalUi.field("Separatore decimale", separator), formats);
        var textBox =
                new VBox(
                        10,
                        TechnicalUi.field("Scritta da marcare *", text),
                        new HBox(10, fields, insert),
                        TechnicalUi.label(
                                "Inserisci i campi tra parentesi graffe. I nomi distinguono"
                                        + " maiuscole e minuscole.",
                                "muted-label"));
        var ruleRow = new HBox(10, rule, clear);
        HBox.setHgrow(rule, Priority.ALWAYS);
        ruleRow.setAlignment(Pos.CENTER_LEFT);
        article.setText("ZR9816MSTA2");
        product.setText("ZRMSTA2");
        lot.setText("LOTTO-ESEMPIO");
        diameter.setText("98");
        height.setText("16");
        weight.setText("400");
        var samples = new FlowPane(12, 10);
        for (var pair :
                List.of(
                        Map.entry("Codice articolo", article),
                        Map.entry("Codice prodotto", product),
                        Map.entry("Lotto", lot),
                        Map.entry("D · mm", diameter),
                        Map.entry("Hmedia · mm", height),
                        Map.entry("P · g", weight))) {
            pair.getValue().setPrefWidth(170);
            pair.getValue().setMaxWidth(170);
            samples.getChildren().add(TechnicalUi.field(pair.getKey(), pair.getValue()));
            pair.getValue().textProperty().addListener((o, a, b) -> refreshPreview());
        }
        preview.setId(ID + "-preview");
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefRowCount(3);
        preview.setStyle("-fx-font-family: monospace; -fx-font-size: 20px;");
        error.setId(ID + "-preview-error");
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());
        getChildren()
                .addAll(
                        TechnicalUi.field(
                                "Regola di cubatura · versione dei risultati disponibili", ruleRow),
                        TechnicalUi.fields(textBox, formatBox),
                        TechnicalUi.label(
                                "Anteprima con valori di esempio", "ton-technical-panel-title"),
                        TechnicalUi.label(
                                "I decimali arrotondano solo la scritta. I valori di prova non sono"
                                        + " misurazioni e non vengono salvati.",
                                "muted-label"),
                        samples,
                        error,
                        preview);
        rule.valueProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!loading) rebuildFields();
                        });
        text.textProperty().addListener((o, a, b) -> refreshPreview());
        separator.valueProperty().addListener((o, a, b) -> refreshPreview());
        rebuildFields();
    }

    private String formatted(String name) {
        if (!(values.get(name) instanceof BigDecimal value)) return "—";
        return value.setScale(decimals.getOrDefault(name, 3), RoundingMode.HALF_UP)
                .toPlainString()
                .replace(".", separator.getValue());
    }

    private void rebuildFields() {
        fieldsError = "";
        try {
            var available = MarkingTemplates.fields(rule.getValue());
            fields.getItems().setAll(available);
            fields.getSelectionModel().selectFirst();
            formats.getItems().setAll(available.stream().filter(Field::numeric).toList());
            formats.setPrefHeight(Math.min(248, 38 + 42 * formats.getItems().size()));
            decimals.keySet()
                    .retainAll(available.stream().filter(Field::numeric).map(Field::name).toList());
        } catch (IllegalArgumentException invalid) {
            fields.getItems().clear();
            formats.getItems().clear();
            fieldsError = invalid.getMessage();
        }
        refreshPreview();
    }

    private void refreshPreview() {
        if (loading) return;
        preview.clear();
        error.setText("");
        values = Map.of();
        try {
            if (!fieldsError.isEmpty()) throw new IllegalArgumentException(fieldsError);
            var sample = new LinkedHashMap<String, Object>();
            sample.put("codice_articolo", article.getText());
            sample.put("codice_prodotto", product.getText());
            sample.put("lotto", lot.getText());
            BigDecimal d = TechnicalUi.decimal(diameter.getText(), "Diametro"),
                    h = TechnicalUi.decimal(height.getText(), "Altezza media"),
                    p = TechnicalUi.decimal(weight.getText(), "Peso");
            if (d.signum() <= 0 || h.signum() <= 0 || p.signum() <= 0)
                throw new IllegalArgumentException("Usa valori di prova positivi.");
            if (d.precision() > 100 || h.precision() > 100 || p.precision() > 100)
                throw new IllegalArgumentException("Valori di prova fuori intervallo.");
            sample.put("D", d);
            sample.put("Hmedia", h);
            sample.put("P", p);
            if (rule.getValue() != null)
                sample.putAll(FormulaProgram.compile(rule.getValue().formulas()).evaluate(d, h, p));
            values = sample;
            if (!text.getText().isBlank())
                preview.setText(
                        MarkingTemplates.render(
                                content("ANTEPRIMA", "Anteprima", "", true),
                                rule.getValue(),
                                sample));
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage());
        }
        formats.refresh();
    }

    public void show(Snapshot snapshot, List<Version> rules) {
        loading = true;
        try {
            rule.getItems().setAll(rules);
            rule.setValue(
                    snapshot == null || snapshot.ruleId() == null
                            ? null
                            : rules.stream()
                                    .filter(r -> r.id() == snapshot.ruleId())
                                    .findFirst()
                                    .orElse(null));
            text.setText(snapshot == null ? "" : snapshot.text());
            separator.setValue(snapshot == null ? "." : snapshot.separator());
            decimals.clear();
            if (snapshot != null) decimals.putAll(snapshot.decimals());
        } finally {
            loading = false;
        }
        rebuildFields();
    }

    public Draft content(String code, String name, String description, boolean active) {
        return new Draft(
                code,
                name,
                description,
                active,
                text.getText(),
                rule.getValue() == null ? null : rule.getValue().id(),
                decimals,
                separator.getValue());
    }

    public List<String> fingerprint() {
        return List.of(
                text.getText(),
                rule.getValue() == null ? "" : "" + rule.getValue().id(),
                formatsJson(decimals),
                separator.getValue());
    }

    public void editable(boolean value) {
        editable = value;
        text.setEditable(value);
        rule.setDisable(!value);
        fields.setDisable(!value);
        insert.setDisable(!value);
        clear.setDisable(!value);
        separator.setDisable(!value);
        formats.refresh();
    }

    public void dispose() {
        insert.setOnAction(null);
        clear.setOnAction(null);
    }
}

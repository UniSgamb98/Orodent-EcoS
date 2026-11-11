package com.orodent.ecos.costi.orders;

import static com.orodent.ecos.costi.orders.OrderTableSupport.*;

import com.orodent.ecos.costi.orders.PowderOrders.*;
import com.orodent.ecos.costi.view.PriceFormats;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.function.Function;

/** Import buffer and its controls survive tab switches and workspace resizing. */
public final class OrderImportPreview extends VBox {
    private record Mapping(Code code, int index) {}

    private final Button apply;
    private final Map<Code, Long> choices = new HashMap<>();
    private final Map<Long, Powder> powders = new HashMap<>();
    private final List<Mapping> codes = new ArrayList<>();
    private final Label summary = new Label();
    private final TableView<Row> data = new TableView<>();
    private final ListView<Mapping> mappings = new ListView<>();
    private final TextField search = new TextField();
    private final CheckBox missingOnly = new CheckBox("Solo da collegare");
    private final TabPane tabs = new TabPane();
    private final Tab rowsTab = new Tab(), mappingTab = new Tab();
    private Preview preview;

    OrderImportPreview(Button apply, Button cancel) {
        this.apply = apply;
        setSpacing(12);
        getStyleClass().add("orders-card");
        setMinSize(0, 0);
        Label title = new Label("Anteprima CSV");
        title.getStyleClass().add("orders-section-title");
        summary.getStyleClass().add("costs-muted");
        summary.setWrapText(true);
        summary.setId("orders-import-summary");
        tabs.setId("orders-preview-tabs");
        tabs.setMinSize(0, 0);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        data.setId("orders-preview-table");
        data.setMinHeight(80);
        data.setFixedCellSize(40);
        data.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column(data, "Ordine / pos.", 115, Row::key, k -> k.order() + " / " + k.position());
        var powder = column(data, "Polvere", 170, Function.identity(), Row::code);
        powder.setMinWidth(130);
        powder.setCellFactory(c -> powderCell(this::mappedPowder, Row::code));
        column(data, "Residuo kg", 90, Row::remaining, OrderTableSupport::number);
        column(data, "€/kg", 80, Row::price, n -> PriceFormats.number(n, 2, 4));
        var expected = column(data, "Data prevista", 108, Row::expected, OrderTableSupport::date);
        expected.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        column(data, "Operazione", 88, this::operation, Function.identity());
        data.setPlaceholder(new Label("Nessuna riga valida da importare."));
        rowsTab.setContent(data);
        rowsTab.setClosable(false);
        mappings.setId("orders-mappings");
        mappings.setMinHeight(80);
        mappings.setFixedCellSize(60);
        mappings.setCellFactory(v -> mappingCell());
        mappings.setPlaceholder(new Label("Nessun codice con i filtri selezionati."));
        search.setId("orders-mapping-search");
        missingOnly.setId("orders-only-unlinked");
        search.setPromptText("Cerca codice CSV, fornitore o polvere TON");
        search.setPrefColumnCount(24);
        search.textProperty().addListener((o, b, a) -> filterMappings());
        missingOnly.setOnAction(e -> filterMappings());
        Label instructions =
                new Label(
                        "Ogni codice CSV si collega a una polvere TON. Le associazioni vengono"
                                + " conservate dopo l'importazione.");
        instructions.setWrapText(true);
        instructions.getStyleClass().add("costs-muted");
        var mappingBody =
                new VBox(10, instructions, new FlowPane(10, 8, search, missingOnly), mappings);
        mappingBody.setMinHeight(0);
        mappingBody.getStyleClass().add("orders-mapping-body");
        VBox.setVgrow(mappings, Priority.ALWAYS);
        mappingTab.setContent(mappingBody);
        mappingTab.setClosable(false);
        getChildren().addAll(new VBox(5, title, summary), tabs, new FlowPane(10, 8, apply, cancel));
        clear();
    }

    void show(Preview value) {
        preview = value;
        choices.clear();
        choices.putAll(value.mappings());
        powders.clear();
        value.powders().forEach(p -> powders.put(p.id(), p));
        codes.clear();
        var unique = new LinkedHashSet<Code>();
        value.csv().validRows().forEach(r -> unique.add(r.value().source()));
        unique.forEach(c -> codes.add(new Mapping(c, codes.size() + 1)));
        search.clear();
        missingOnly.setSelected(false);
        filterMappings();
        data.getItems().setAll(value.csv().validRows().stream().map(r -> r.value()).toList());
        rowsTab.setText("Righe CSV (" + data.getItems().size() + ")");
        mappingTab.setText("Collegamenti (" + codes.size() + ")");
        tabs.getTabs().setAll(rowsTab, mappingTab);
        var errors = new StringJoiner("\n");
        value.problems().forEach(errors::add);
        value.csv()
                .documentErrors()
                .forEach(e -> errors.add("Riga " + e.lineNumber() + ": " + e.message()));
        value.csv()
                .rowErrors()
                .forEach(
                        e ->
                                errors.add(
                                        "Riga "
                                                + e.lineNumber()
                                                + " · "
                                                + e.field()
                                                + ": "
                                                + e.message()));
        if (errors.length() > 0) {
            var text = new TextArea(errors.toString());
            text.setEditable(false);
            text.setWrapText(true);
            var errorTab = new Tab("Errori da correggere", text);
            errorTab.setClosable(false);
            tabs.getTabs().add(errorTab);
            tabs.getSelectionModel().select(errorTab);
        } else
            tabs.getSelectionModel()
                    .select(
                            codes.stream().anyMatch(c -> !choices.containsKey(c.code()))
                                    ? mappingTab
                                    : rowsTab);
        setVisible(true);
        setManaged(true);
        validate();
    }

    private ListCell<Mapping> mappingCell() {
        return new ListCell<>() {
            private final Label code = new Label(), supplier = new Label(), arrow = new Label("→");
            private final VBox source = new VBox(5, code, supplier);
            private final ComboBox<Powder> choice = new ComboBox<>();
            private final HBox content = new HBox(12, source, arrow, choice);
            private boolean updating;

            {
                getStyleClass().add("orders-mapping-row");
                content.setAlignment(Pos.CENTER_LEFT);
                source.setMinWidth(150);
                source.setMaxWidth(240);
                source.prefWidthProperty().bind(widthProperty().subtract(50).multiply(.4));
                code.setMaxWidth(Double.MAX_VALUE);
                code.getStyleClass().add("orders-mapping-code");
                supplier.getStyleClass().add("costs-muted");
                choice.setMinWidth(180);
                choice.setMaxWidth(300);
                choice.prefWidthProperty().bind(widthProperty().subtract(50).multiply(.5));
                choice.setPromptText("Scegli la polvere TON");
                choice.setCellFactory(l -> powderOption());
                choice.setButtonCell(powderOption());
                choice.valueProperty()
                        .addListener(
                                (o, b, a) -> {
                                    Mapping mapping = getItem();
                                    if (updating || mapping == null) return;
                                    if (a == null) choices.remove(mapping.code());
                                    else choices.put(mapping.code(), a.id());
                                    data.refresh();
                                    validate();
                                    if (missingOnly.isSelected()) filterMappings();
                                });
            }

            @Override
            protected void updateItem(Mapping item, boolean empty) {
                super.updateItem(item, empty);
                updating = true;
                try {
                    setText(null);
                    setGraphic(empty || item == null ? null : content);
                    if (!empty && item != null) {
                        source.setId("orders-source-" + item.index());
                        code.setText(item.code().code());
                        code.setTooltip(new Tooltip(item.code().code()));
                        supplier.setText("Fornitore " + item.code().supplier());
                        choice.getItems().setAll(preview.powders());
                        choice.setValue(powders.get(choices.get(item.code())));
                        choice.setId("orders-mapping-" + item.index());
                        choice.setAccessibleText("Polvere TON per " + item.code().code());
                    }
                } finally {
                    updating = false;
                }
            }
        };
    }

    private String operation(Row row) {
        Stored existing = preview == null ? null : preview.existing().get(row.key());
        if (existing == null) return "Nuova";
        return existing.row().equals(row)
                        && Objects.equals(existing.powderId(), choices.get(row.source()))
                ? "Invariata"
                : "Aggiorna";
    }

    private Powder mappedPowder(Row row) {
        return powders.get(choices.get(row.source()));
    }

    private void filterMappings() {
        String q = search.getText().strip().toLowerCase(Locale.ROOT);
        mappings.getItems()
                .setAll(
                        codes.stream()
                                .filter(
                                        c ->
                                                !missingOnly.isSelected()
                                                        || !choices.containsKey(c.code()))
                                .filter(
                                        c ->
                                                (c.code().code()
                                                                + " "
                                                                + c.code().supplier()
                                                                + " "
                                                                + powders.get(
                                                                        choices.get(c.code())))
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(q))
                                .toList());
    }

    private void validate() {
        long linked = codes.stream().filter(c -> choices.containsKey(c.code())).count();
        int valid = preview == null ? 0 : preview.csv().validRows().size();
        summary.setText(
                valid
                        + (valid == 1 ? " riga valida" : " righe valide")
                        + " · "
                        + linked
                        + "/"
                        + codes.size()
                        + " codici collegati");
        apply.setDisable(preview == null || !preview.valid() || linked != codes.size());
    }

    public Map<Code, Long> mappings() {
        return Map.copyOf(choices);
    }

    public Preview pending() {
        return preview;
    }

    void clear() {
        preview = null;
        choices.clear();
        powders.clear();
        codes.clear();
        mappings.getItems().clear();
        data.getItems().clear();
        tabs.getTabs().clear();
        setVisible(false);
        setManaged(false);
        validate();
    }
}

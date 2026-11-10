package com.orodent.ecos.costi.orders;

import com.orodent.ecos.costi.orders.PowderOrders.*;
import com.orodent.ecos.costi.view.*;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/** Purchase-order workspace. Imported receipts never modify the declared warehouse. */
public final class OrdersView extends CostsShell {
    public final Button refresh = new Button("Aggiorna"),
            importCsv = new Button("Importa CSV ordini"),
            apply = new Button("Conferma importazione"),
            cancel = new Button("Chiudi anteprima"),
            edit = new Button("Data prevista / esclusione");
    public final ComboBox<String> encoding = new ComboBox<>();
    public final CheckBox openOnly = new CheckBox("Solo righe da ricevere");
    public final TextField search = new TextField();
    public final TableView<Stored> table = new TableView<>();
    public final Label feedback = new Label();
    public final VBox previewPane = new VBox(9);
    private final Map<Code, Long> choices = new HashMap<>();
    private Preview preview;
    private List<Stored> rows = List.of();
    private Map<Long, Powder> powders = Map.of();

    public OrdersView() {
        super("Ordini in arrivo");
        setId("costi-orders-module");
        encoding.getItems().setAll("UTF-8", "windows-1252");
        encoding.setValue("UTF-8");
        search.setPromptText("Cerca ordine, fornitore o polvere");
        search.setPrefWidth(300);
        openOnly.setSelected(true);
        openOnly.setOnAction(e -> filter());
        search.textProperty().addListener((o, b, a) -> filter());
        column(table, "Fornitore", 90, s -> s.row().key().supplier());
        column(
                table,
                "Ordine / pos.",
                140,
                s -> s.row().key().order() + " / " + s.row().key().position());
        column(
                table,
                "Polvere",
                230,
                s -> Objects.toString(powders.get(s.powderId()), s.row().code()));
        column(table, "Ordinati kg", 100, s -> number(s.row().ordered()));
        column(table, "Consegnati kg", 110, s -> number(s.row().delivered()));
        column(table, "Da ricevere kg", 120, s -> number(s.row().remaining()));
        column(table, "€/kg", 90, s -> number(s.row().price()));
        column(table, "Data CSV", 100, s -> date(s.row().expected()));
        column(
                table,
                "Data prevista",
                110,
                s -> date(s.date()) + (s.overrideDate() == null ? "" : " *"));
        column(table, "Stato", 190, s -> s.status(LocalDate.now()));
        table.setPrefHeight(380);
        table.setId("orders-table");
        feedback.setWrapText(true);
        previewPane.setVisible(false);
        previewPane.setManaged(false);
        Label note =
                new Label(
                        "Arrivi previsti: quantità ancora da consegnare. Le consegne scadute"
                            + " richiedono una nuova data. Nessun carico automatico di magazzino. *"
                            + " Data modificata manualmente.");
        note.setWrapText(true);
        VBox content =
                new VBox(
                        12,
                        new Label("Ordini in arrivo"),
                        new FlowPane(10, 8, importCsv, encoding, refresh),
                        note,
                        new FlowPane(10, 8, search, openOnly, edit),
                        table,
                        previewPane,
                        feedback);
        content.setStyle("-fx-padding: 24;");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        setCenter(scroll);
        importCsv.setId("orders-import");
        apply.setId("orders-apply");
    }

    public void catalogue(OrderService.Catalogue catalogue) {
        rows = catalogue.rows();
        var map = new HashMap<Long, Powder>();
        catalogue.powders().forEach(p -> map.put(p.id(), p));
        powders = Map.copyOf(map);
        filter();
    }

    private void filter() {
        String q = search.getText().strip().toLowerCase(Locale.ROOT);
        table.getItems()
                .setAll(
                        rows.stream()
                                .filter(
                                        s ->
                                                !openOnly.isSelected()
                                                        || s.row().remaining().signum() > 0)
                                .filter(
                                        s ->
                                                (s.row().key()
                                                                + " "
                                                                + s.row().code()
                                                                + " "
                                                                + powders.get(s.powderId()))
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(q))
                                .toList());
    }

    public void preview(Preview value) {
        preview = value;
        choices.clear();
        choices.putAll(value.mappings());
        previewPane.getChildren().clear();
        Label title =
                new Label(
                        "Anteprima · "
                                + value.csv().validRows().size()
                                + " righe valide su "
                                + value.csv().totalRows());
        previewPane.getChildren().add(title);
        StringJoiner errors = new StringJoiner("\n");
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
            TextArea text = new TextArea(errors.toString());
            text.setEditable(false);
            text.setPrefRowCount(4);
            previewPane.getChildren().add(text);
        }
        var codes = new LinkedHashMap<Code, Row>();
        value.csv().validRows().forEach(r -> codes.putIfAbsent(r.value().source(), r.value()));
        VBox mapping = new VBox(6);
        codes.forEach(
                (code, row) -> {
                    ComboBox<Powder> powder = new ComboBox<>();
                    powder.getItems().setAll(value.powders());
                    powder.setPrefWidth(350);
                    powder.setPromptText("Collega alla polvere TON");
                    powder.setValue(
                            value.powders().stream()
                                    .filter(p -> Objects.equals(choices.get(code), p.id()))
                                    .findFirst()
                                    .orElse(null));
                    powder.valueProperty()
                            .addListener(
                                    (o, b, a) -> {
                                        if (a == null) choices.remove(code);
                                        else choices.put(code, a.id());
                                        validate();
                                    });
                    mapping.getChildren()
                            .add(
                                    new HBox(
                                            12,
                                            new Label(code.supplier() + " · " + code.code()),
                                            powder));
                });
        ScrollPane mappings = new ScrollPane(mapping);
        mappings.setFitToWidth(true);
        mappings.setPrefViewportHeight(Math.min(180, 40 * codes.size()));
        previewPane.getChildren().add(mappings);
        TableView<Row> data = new TableView<>();
        column(data, "Ordine / posizione", 150, r -> r.key().order() + " / " + r.key().position());
        column(data, "Articolo", 180, Row::code);
        column(data, "Data prevista", 110, r -> date(r.expected()));
        column(data, "Residuo kg", 100, r -> number(r.remaining()));
        column(data, "€/kg", 90, r -> number(r.price()));
        column(
                data,
                "Operazione",
                140,
                r -> value.existing().containsKey(r.key()) ? "Confronta / aggiorna" : "Nuova riga");
        data.getItems().setAll(value.csv().validRows().stream().map(r -> r.value()).toList());
        data.setPrefHeight(200);
        previewPane.getChildren().addAll(data, new HBox(10, apply, cancel));
        previewPane.setVisible(true);
        previewPane.setManaged(true);
        validate();
    }

    private void validate() {
        apply.setDisable(
                preview == null
                        || !preview.valid()
                        || preview.csv().validRows().stream()
                                .anyMatch(r -> !choices.containsKey(r.value().source())));
    }

    public Map<Code, Long> mappings() {
        return Map.copyOf(choices);
    }

    public Preview pending() {
        return preview;
    }

    public void clearPreview() {
        preview = null;
        choices.clear();
        previewPane.getChildren().clear();
        previewPane.setVisible(false);
        previewPane.setManaged(false);
    }

    public void busy(boolean busy) {
        importCsv.setDisable(busy);
        refresh.setDisable(busy);
        edit.setDisable(busy);
        previewPane.setDisable(busy);
        home.setDisable(busy);
    }

    static String date(LocalDate date) {
        return date == null
                ? "—"
                : java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu").format(date);
    }

    private static String number(java.math.BigDecimal n) {
        return PriceFormats.number(n, 0, 4);
    }

    private static <T> void column(
            TableView<T> table, String name, int width, Function<T, String> text) {
        TableColumn<T, String> col = new TableColumn<>(name);
        col.setPrefWidth(width);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(text.apply(c.getValue())));
        table.getColumns().add(col);
    }
}

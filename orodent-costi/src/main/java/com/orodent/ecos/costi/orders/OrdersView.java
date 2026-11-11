package com.orodent.ecos.costi.orders;

import com.orodent.ecos.costi.orders.PowderOrders.*;
import com.orodent.ecos.costi.view.*;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
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
    public final VBox previewPane = new VBox(12);
    private final GridPane workspace = new GridPane();
    private final VBox ordersPane = new VBox(10);
    private final Map<Code, Long> choices = new HashMap<>();
    private Preview preview;
    private List<Stored> rows = List.of();
    private Map<Long, Powder> powders = Map.of();
    private Boolean sideBySide;

    public OrdersView() {
        super("Ordini in arrivo");
        setId("costi-orders-module");
        getStylesheets().add(OrdersView.class.getResource("/costi/orders.css").toExternalForm());
        encoding.getItems().setAll("UTF-8", "windows-1252");
        encoding.setValue("UTF-8");
        search.setPromptText("Cerca ordine, fornitore o polvere");
        search.setPrefWidth(280);
        openOnly.setSelected(true);
        openOnly.setOnAction(e -> filter());
        search.textProperty().addListener((o, b, a) -> filter());

        var originalDate = column(table, "Data", 96, s -> s.row().expected(), OrdersView::date);
        originalDate.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        originalDate.setGraphic(info("Data originale Cons. prev. del CSV."));
        var order =
                column(
                        table,
                        "Ordine",
                        124,
                        s -> s.row().key(),
                        k -> k.order() + " / " + k.position());
        order.setComparator(
                Comparator.comparing(Key::order)
                        .thenComparingInt(Key::position)
                        .thenComparing(Key::supplier));
        var powder = column(table, "Polvere", 230, Function.identity(), this::powderText);
        powder.setMinWidth(165);
        powder.setComparator(Comparator.comparing(this::powderText));
        powder.setCellFactory(c -> powderCell(s -> powders.get(s.powderId()), this::powderText));
        column(table, "Ordinati", 96, s -> s.row().ordered(), OrdersView::number);
        column(table, "€/kg", 82, s -> s.row().price(), n -> PriceFormats.number(n, 2, 4));
        var expected = column(table, "Data prevista", 116, Stored::date, OrdersView::date);
        expected.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        expected.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(LocalDate value, boolean empty) {
                                super.updateItem(value, empty);
                                setStyle("-fx-alignment: center;");
                                Stored row = getTableRow() == null ? null : getTableRow().getItem();
                                setText(
                                        empty
                                                ? null
                                                : date(value)
                                                        + (row != null && row.overrideDate() != null
                                                                ? " *"
                                                                : ""));
                                setTooltip(
                                        empty || row == null
                                                ? null
                                                : new Tooltip(orderDetails(row)));
                            }
                        });
        table.getSortOrder().setAll(expected, order);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(44);
        table.setPrefHeight(260);
        table.setMinHeight(150);
        table.setId("orders-table");
        table.setPlaceholder(new Label("Nessun ordine con i filtri selezionati."));
        table.setRowFactory(
                t ->
                        new TableRow<>() {
                            @Override
                            protected void updateItem(Stored item, boolean empty) {
                                super.updateItem(item, empty);
                                setTooltip(
                                        empty || item == null
                                                ? null
                                                : new Tooltip(orderDetails(item)));
                            }
                        });
        ordersPane.getStyleClass().add("orders-card");
        ordersPane
                .getChildren()
                .addAll(
                        new Label("Polveri in ordine"),
                        new FlowPane(10, 8, search, openOnly, edit),
                        table);
        ordersPane.setMinWidth(0);
        ordersPane.setMaxWidth(920);
        previewPane.getStyleClass().add("orders-card");
        previewPane.setId("orders-preview");
        previewPane.setMinWidth(0);
        previewPane.setVisible(false);
        previewPane.setManaged(false);
        workspace.setId("orders-workspace");
        workspace.setHgap(18);
        workspace.setVgap(18);
        workspace.widthProperty().addListener((o, b, a) -> layoutWorkspace());
        feedback.setWrapText(true);
        Label title = new Label("Ordini in arrivo");
        title.getStyleClass().add("costs-title");
        Label note =
                new Label(
                        "Data: originale CSV · Data prevista: consegna attesa (* se modificata). "
                                + "Quantità in kg. Nessun carico automatico di magazzino.");
        note.getStyleClass().add("costs-muted");
        note.setWrapText(true);
        VBox content =
                new VBox(
                        14,
                        title,
                        new FlowPane(10, 8, importCsv, encoding, refresh),
                        note,
                        workspace,
                        feedback);
        content.setStyle("-fx-padding: 24;");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("orders-scroll");
        setCenter(scroll);
        layoutWorkspace();
        importCsv.setId("orders-import");
        apply.setId("orders-apply");
        apply.getStyleClass().add("primary-button");
    }

    /** Reparent the same controls when the breakpoint changes, retaining mappings and selection. */
    private void layoutWorkspace() {
        boolean beside = preview != null && workspace.getWidth() >= 1380;
        if (sideBySide != null
                && sideBySide == beside
                && workspace.getChildren().contains(previewPane) == (preview != null)) return;
        sideBySide = beside;
        workspace.getChildren().clear();
        workspace.getColumnConstraints().clear();
        var left = new ColumnConstraints();
        left.setHgrow(Priority.ALWAYS);
        left.setMinWidth(0);
        workspace.getColumnConstraints().add(left);
        ordersPane.setMaxWidth(beside ? Double.MAX_VALUE : 920);
        previewPane.setMaxWidth(beside ? Double.MAX_VALUE : 920);
        workspace.add(ordersPane, 0, 0);
        GridPane.setValignment(ordersPane, javafx.geometry.VPos.TOP);
        if (preview != null) {
            if (beside) {
                left.setMinWidth(660);
                left.setPrefWidth(740);
                left.setMaxWidth(840);
                var right = new ColumnConstraints();
                right.setHgrow(Priority.ALWAYS);
                right.setMinWidth(560);
                workspace.getColumnConstraints().add(right);
            }
            workspace.add(previewPane, beside ? 1 : 0, beside ? 0 : 1);
            GridPane.setValignment(previewPane, javafx.geometry.VPos.TOP);
        }
    }

    public void catalogue(OrderService.Catalogue catalogue) {
        rows = catalogue.rows();
        var map = new HashMap<Long, Powder>();
        catalogue.powders().forEach(p -> map.put(p.id(), p));
        powders = Map.copyOf(map);
        filter();
        table.refresh();
    }

    private void filter() {
        String q = search.getText().strip().toLowerCase(Locale.ROOT);
        Stored selected = table.getSelectionModel().getSelectedItem();
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
        table.sort();
        if (selected != null && table.getItems().contains(selected))
            table.getSelectionModel().select(selected);
        table.setPrefHeight(Math.min(460, Math.max(180, 46 + table.getItems().size() * 44)));
    }

    public void preview(Preview value) {
        preview = value;
        choices.clear();
        choices.putAll(value.mappings());
        previewPane.getChildren().clear();
        Label title = new Label("Anteprima CSV");
        title.getStyleClass().add("orders-preview-title");
        previewPane
                .getChildren()
                .addAll(
                        title,
                        new Label(
                                value.csv().validRows().size()
                                        + " righe valide su "
                                        + value.csv().totalRows()));
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
            text.setWrapText(true);
            text.setPrefRowCount(3);
            previewPane.getChildren().add(text);
        }
        var codes = new LinkedHashMap<Code, Row>();
        value.csv().validRows().forEach(r -> codes.putIfAbsent(r.value().source(), r.value()));
        GridPane mapping = new GridPane();
        mapping.setId("orders-mappings");
        mapping.setHgap(12);
        mapping.setVgap(8);
        var sourceColumn = new ColumnConstraints();
        sourceColumn.setHgrow(Priority.ALWAYS);
        sourceColumn.setMinWidth(140);
        var targetColumn = new ColumnConstraints(180, 230, 240);
        mapping.getColumnConstraints().addAll(sourceColumn, targetColumn);
        mapping.add(new Label("Codice CSV"), 0, 0);
        mapping.add(new Label("Polvere TON"), 1, 0);
        TableView<Row> data = new TableView<>();
        int index = 1;
        for (var entry : codes.entrySet()) {
            Code code = entry.getKey();
            ComboBox<Powder> powder = new ComboBox<>();
            powder.getItems().setAll(value.powders());
            powder.setMinWidth(180);
            powder.setPrefWidth(230);
            powder.setMaxWidth(240);
            powder.setId("orders-mapping-" + index);
            powder.setPromptText("Collega alla polvere TON");
            powder.setCellFactory(l -> powderOption());
            powder.setButtonCell(powderOption());
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
                                data.refresh();
                                validate();
                            });
            Label source = new Label(code.code());
            source.setMaxWidth(Double.MAX_VALUE);
            source.setTooltip(new Tooltip(code.code() + "\nFornitore: " + code.supplier()));
            Label supplier = new Label("Fornitore " + code.supplier());
            supplier.getStyleClass().add("costs-muted");
            mapping.add(new VBox(3, source, supplier), 0, index);
            mapping.add(powder, 1, index++);
        }
        ScrollPane mappings = new ScrollPane(mapping);
        mappings.setFitToWidth(true);
        mappings.setPrefViewportHeight(Math.min(210, 36 + 48 * codes.size()));
        mappings.getStyleClass().add("orders-scroll");
        previewPane.getChildren().add(mappings);
        column(data, "Ordine / pos.", 112, Row::key, k -> k.order() + " / " + k.position());
        var powderColumn = column(data, "Polvere", 150, Function.identity(), Row::code);
        powderColumn.setMinWidth(120);
        powderColumn.setCellFactory(c -> powderCell(r -> mappedPowder(value, r), Row::code));
        column(data, "Residuo kg", 88, Row::remaining, OrdersView::number);
        column(data, "€/kg", 76, Row::price, n -> PriceFormats.number(n, 2, 4));
        var expected = column(data, "Data prevista", 106, Row::expected, OrdersView::date);
        expected.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        var operation =
                column(
                        data,
                        "Operazione",
                        100,
                        r ->
                                value.existing().containsKey(r.key())
                                        ? "Confronta / aggiorna"
                                        : "Nuova riga",
                        Function.identity());
        operation.setMinWidth(90);
        data.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        data.setFixedCellSize(40);
        data.setId("orders-preview-table");
        data.getItems().setAll(value.csv().validRows().stream().map(r -> r.value()).toList());
        data.setPrefHeight(Math.min(280, Math.max(140, 46 + data.getItems().size() * 40)));
        data.setPlaceholder(new Label("Nessuna riga valida da importare."));
        previewPane.getChildren().addAll(data, new FlowPane(10, 8, apply, cancel));
        previewPane.setVisible(true);
        previewPane.setManaged(true);
        layoutWorkspace();
        validate();
    }

    private Powder mappedPowder(Preview value, Row row) {
        Long id = choices.get(row.source());
        return value.powders().stream()
                .filter(p -> Objects.equals(p.id(), id))
                .findFirst()
                .orElse(null);
    }

    private String powderText(Stored row) {
        return Objects.toString(powders.get(row.powderId()), row.row().code());
    }

    private <T> TableCell<T, T> powderCell(Function<T, Powder> powder, Function<T, String> text) {
        return new TableCell<>() {
            private final Label chip = new Label();

            {
                chip.setMaxWidth(Double.MAX_VALUE);
            }

            @Override
            protected void updateItem(T value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                setGraphic(empty || value == null ? null : chip);
                if (!empty && value != null) {
                    Powder p = powder.apply(value);
                    badge(chip, text.apply(value), p);
                    chip.setTooltip(
                            new Tooltip(
                                    text.apply(value)
                                            + (p == null
                                                    ? "\nDa collegare al catalogo TON"
                                                    : "\nColore: " + p.color())));
                }
            }
        };
    }

    private static ListCell<Powder> powderOption() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Powder item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    Label label = new Label();
                    badge(label, item.toString(), item);
                    setGraphic(label);
                    setTooltip(new Tooltip(item.toString()));
                }
            }
        };
    }

    private static void badge(Label label, String text, Powder powder) {
        label.setText(text);
        String color = powder == null ? "ALTRE" : Objects.toString(powder.color(), "ALTRE");
        label.getStyleClass()
                .setAll(
                        "orders-powder-badge",
                        switch (color) {
                            case "BIANCA" -> "powder-white";
                            case "GIALLA" -> "powder-yellow";
                            case "ROSA" -> "powder-pink";
                            case "GRIGIA" -> "powder-gray";
                            default -> "powder-other";
                        });
    }

    private static String orderDetails(Stored row) {
        return "Fornitore: "
                + row.row().key().supplier()
                + "\nData ordine: "
                + date(row.row().orderDate())
                + "\nConsegnati: "
                + number(row.row().delivered())
                + " kg"
                + "\nDa ricevere: "
                + number(row.row().remaining())
                + " kg"
                + "\nStato: "
                + row.status(LocalDate.now());
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
        layoutWorkspace();
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

    private static String number(BigDecimal n) {
        return PriceFormats.number(n, 0, 4);
    }

    private static Label info(String message) {
        Label label = new Label("ⓘ");
        label.setTooltip(new Tooltip(message));
        return label;
    }

    private static <T, V> TableColumn<T, V> column(
            TableView<T> table,
            String name,
            int width,
            Function<T, V> value,
            Function<V, String> text) {
        TableColumn<T, V> col = new TableColumn<>(name);
        if (name.equals("Ordinati") || name.equals("Residuo kg") || name.equals("€/kg"))
            col.getStyleClass().add("orders-number");
        col.setPrefWidth(width);
        col.setMinWidth(width * .72);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(value.apply(c.getValue())));
        col.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(V value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(empty ? null : text.apply(value));
                                setTooltip(empty ? null : new Tooltip(text.apply(value)));
                                setStyle(
                                        value instanceof BigDecimal
                                                ? "-fx-alignment: center-right;"
                                                : value instanceof LocalDate
                                                        ? "-fx-alignment: center;"
                                                        : "");
                            }
                        });
        table.getColumns().add(col);
        return col;
    }
}

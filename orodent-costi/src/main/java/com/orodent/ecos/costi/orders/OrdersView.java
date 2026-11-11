package com.orodent.ecos.costi.orders;

import static com.orodent.ecos.costi.orders.OrderTableSupport.*;

import com.orodent.ecos.costi.orders.PowderOrders.*;
import com.orodent.ecos.costi.view.*;

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
    public final OrderImportPreview previewPane = new OrderImportPreview(apply, cancel);
    private final BorderPane workspace = new BorderPane();
    private final SplitPane split = new SplitPane();
    private final TabPane compactTabs = new TabPane();
    private final Tab savedTab = new Tab("Ordini salvati"), importTab = new Tab("Importazione CSV");
    private final Label orderCount = new Label();
    private final VBox ordersPane = new VBox(10);
    private List<Stored> rows = List.of();
    private Map<Long, Powder> powders = Map.of();
    private String layoutMode = "";
    private double divider = .5;

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
        column(table, "Ordinati", 96, s -> s.row().ordered(), OrderTableSupport::number);
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
        table.setMinHeight(100);
        table.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
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
        Label section = new Label("Polveri in ordine");
        section.getStyleClass().add("orders-section-title");
        orderCount.getStyleClass().add("orders-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox sectionHeader = new HBox(10, section, spacer, orderCount);
        sectionHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        edit.setText("Modifica consegna");
        edit.setTooltip(new Tooltip("Data prevista ed esclusione dalle previsioni"));
        ordersPane
                .getChildren()
                .addAll(sectionHeader, new FlowPane(10, 8, search, openOnly, edit), table);
        ordersPane.setMinSize(0, 0);
        previewPane.setId("orders-preview");
        previewPane.setMinSize(0, 0);
        workspace.setId("orders-workspace");
        workspace.setMinSize(0, 0);
        workspace.widthProperty().addListener((o, b, a) -> layoutWorkspace(false));
        split.getStyleClass().add("orders-split");
        split.setMinSize(0, 0);
        savedTab.setClosable(false);
        importTab.setClosable(false);
        compactTabs.getTabs().setAll(savedTab, importTab);
        compactTabs.setId("orders-compact-tabs");
        compactTabs.setMinSize(0, 0);
        feedback.setWrapText(true);
        feedback.setMinHeight(Region.USE_PREF_SIZE);
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        feedback.getStyleClass().add("orders-feedback");
        Label title = new Label("Ordini in arrivo");
        title.getStyleClass().add("costs-title");
        Label subtitle = new Label("Consegne delle polveri e importazione degli ordini");
        subtitle.getStyleClass().add("costs-muted");
        importCsv.getStyleClass().add("primary-button");
        encoding.setPrefWidth(120);
        Label help =
                info(
                        "Data: Cons. prev. originale del CSV. Data prevista: consegna attesa; *"
                            + " indica una data manuale.\n"
                            + "Quantità in kg e prezzi €/kg. Ordinamento iniziale per consegna,"
                            + " ordine e posizione.\n"
                            + "Nessun carico automatico di magazzino. L'importazione si conferma"
                            + " solo dopo l'anteprima.");
        var toolbar = new FlowPane(10, 8, importCsv, encoding, refresh, help);
        var heading = new VBox(4, title, subtitle);
        heading.setPrefWidth(370);
        toolbar.setPrefWrapLength(450);
        var top = new FlowPane(18, 10, heading, toolbar);
        top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        top.getStyleClass().add("orders-page-heading");
        var content = new VBox(14, top, workspace, feedback);
        content.setMinSize(0, 0);
        content.setStyle("-fx-padding: 18 22;");
        VBox.setVgrow(workspace, Priority.ALWAYS);
        setCenter(content);
        layoutWorkspace(false);
        importCsv.setId("orders-import");
        apply.setId("orders-apply");
        apply.getStyleClass().add("primary-button");
    }

    /** Use all available height. Narrow windows switch panels rather than stacking scroll areas. */
    private void layoutWorkspace(boolean opened) {
        boolean pending = pending() != null;
        String next = !pending ? "saved" : workspace.getWidth() >= 1320 ? "split" : "compact";
        if (next.equals(layoutMode)) {
            if (opened && next.equals("compact")) compactTabs.getSelectionModel().select(importTab);
            return;
        }
        if (layoutMode.equals("split") && split.getDividerPositions().length > 0)
            divider = split.getDividerPositions()[0];
        workspace.setCenter(null);
        split.getItems().clear();
        savedTab.setContent(null);
        importTab.setContent(null);
        layoutMode = next;
        ordersPane.setMinWidth(0);
        previewPane.setMinWidth(0);
        if (next.equals("saved")) workspace.setCenter(ordersPane);
        else if (next.equals("split")) {
            ordersPane.setMinWidth(600);
            previewPane.setMinWidth(560);
            split.getItems().setAll(ordersPane, previewPane);
            split.setDividerPositions(divider);
            workspace.setCenter(split);
        } else {
            ordersPane.setMinWidth(0);
            previewPane.setMinWidth(0);
            savedTab.setContent(ordersPane);
            importTab.setContent(previewPane);
            workspace.setCenter(compactTabs);
            compactTabs.getSelectionModel().select(importTab);
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
        long orders =
                table.getItems().stream()
                        .map(r -> Map.entry(r.row().key().supplier(), r.row().key().order()))
                        .distinct()
                        .count();
        int count = table.getItems().size();
        orderCount.setText(
                count
                        + (count == 1 ? " riga" : " righe")
                        + " · "
                        + orders
                        + (orders == 1 ? " ordine" : " ordini"));
    }

    public void preview(Preview value) {
        previewPane.show(value);
        layoutWorkspace(true);
    }

    private String powderText(Stored row) {
        return Objects.toString(powders.get(row.powderId()), row.row().code());
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

    public Map<Code, Long> mappings() {
        return previewPane.mappings();
    }

    public Preview pending() {
        return previewPane.pending();
    }

    public void clearPreview() {
        previewPane.clear();
        layoutWorkspace(false);
    }

    public void busy(boolean busy) {
        importCsv.setDisable(busy);
        refresh.setDisable(busy);
        edit.setDisable(busy);
        previewPane.setDisable(busy);
        home.setDisable(busy);
    }

    static String date(LocalDate value) {
        return OrderTableSupport.date(value);
    }
}

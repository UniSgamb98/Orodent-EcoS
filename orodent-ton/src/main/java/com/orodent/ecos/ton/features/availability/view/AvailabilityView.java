package com.orodent.ecos.ton.features.availability.view;

import static com.orodent.ecos.ton.features.availability.model.Availability.*;

import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.animation.PauseTransition;
import javafx.beans.property.*;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

public final class AvailabilityView extends VBox {
    public record Position(Period period, Long selected, Long top, double railScroll) {
        public static Position initial() {
            return new Position(Period.DAYS_90, null, null, 0);
        }
    }

    public final Button
            stockImport =
                    TechnicalUi.button(
                            "Importa disponibilità", "ton-availability-import-stock", false),
            ordersImport =
                    TechnicalUi.button("Importa ordini", "ton-availability-import-orders", false),
            refresh = TechnicalUi.button("Aggiorna", "ton-availability-refresh", false);
    public final ComboBox<Period> period = new ComboBox<>();
    public final TableView<Row> table = new TableView<>();
    private final VBox lines = new VBox(8);
    private final ScrollPane lineScroll = new ScrollPane(lines);
    private final HBox content = new HBox(12);
    private final VBox rail = new VBox(12);
    private final Label freshness = new Label(), feedback = new Label();
    private final Label stockCaption = new Label("Disponibilità totale"),
            ordersCaption = new Label("Da evadere");
    private final Label stockTotal = numberLabel("ton-availability-stock-total"),
            ordersTotal = numberLabel("ton-availability-orders-total"),
            soldTotal = numberLabel("ton-availability-sold-total");
    private final Map<Long, Button> lineButtons = new HashMap<>();
    private final List<ContextMenu> menus = new ArrayList<>();
    private final PauseTransition pulse = new PauseTransition(Duration.seconds(1.8));
    private long highlighted = -1;
    private Consumer<Row> open = r -> {};
    private static final PseudoClass JUMP = PseudoClass.getPseudoClass("jump-target");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public AvailabilityView() {
        super(12);
        setId("ton-availability-view");
        setMinSize(0, 0);
        getStyleClass().addAll("ton-list-page", "ton-availability");
        getStylesheets()
                .add(
                        Objects.requireNonNull(getClass().getResource("/ton/css/lists.css"))
                                .toExternalForm());
        getStylesheets()
                .add(
                        Objects.requireNonNull(getClass().getResource("/ton/css/availability.css"))
                                .toExternalForm());
        Label title = TechnicalUi.label("Disponibilità prodotti", "availability-title");
        Label subtitle =
                TechnicalUi.label(
                        "Giacenze, ordini da evadere e vendite per codice", "muted-label");
        VBox heading = new VBox(4, title, subtitle);
        heading.setMinWidth(150);
        HBox.setHgrow(heading, Priority.ALWAYS);
        HBox totals =
                new HBox(
                        28,
                        total(stockCaption, stockTotal),
                        total(ordersCaption, ordersTotal),
                        total("Venduti", soldTotal));
        totals.setAlignment(Pos.CENTER_RIGHT);
        HBox header = new HBox(20, heading, totals);
        header.setAlignment(Pos.CENTER_LEFT);
        period.setId("ton-availability-period");
        period.getItems().setAll(Period.values());
        period.setValue(Period.DAYS_90);
        period.setAccessibleText("Periodo delle vendite");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar =
                new HBox(
                        10,
                        stockImport,
                        ordersImport,
                        refresh,
                        spacer,
                        new Label("Vendite"),
                        period);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        freshness.setId("ton-availability-freshness");
        freshness.getStyleClass().add("muted-label");
        freshness.setWrapText(true);
        table.setId("ton-availability-table");
        table.setMinSize(300, 100);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setFixedCellSize(36);
        column("Codice prodotto", r -> r.article().code(), false, null, 460);
        column("Disponibilità", r -> number(r.stock()), true, Row::stock, 220);
        column("Da evadere", r -> number(r.orders()), true, null, 220);
        column("Venduti", r -> number(r.sold()), true, null, 220);
        table.setPlaceholder(new Label("Nessun articolo nel catalogo TON."));
        table.setRowFactory(
                t -> {
                    var row =
                            new TableRow<Row>() {
                                @Override
                                protected void updateItem(Row item, boolean empty) {
                                    super.updateItem(item, empty);
                                    pseudoClassStateChanged(
                                            JUMP,
                                            !empty
                                                    && item != null
                                                    && item.article().id() == highlighted);
                                }
                            };
                    row.setOnMouseClicked(
                            e -> {
                                if (e.getButton() == MouseButton.PRIMARY
                                        && e.getClickCount() == 2
                                        && !row.isEmpty()) open.accept(row.getItem());
                            });
                    return row;
                });
        table.setOnKeyPressed(
                e -> {
                    if (e.getCode() == KeyCode.ENTER) {
                        var selected = table.getSelectionModel().getSelectedItem();
                        if (selected != null) open.accept(selected);
                        e.consume();
                    }
                });
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> selectLine(b));
        lines.setFillWidth(true);
        lines.setMinWidth(0);
        lineScroll.setFitToWidth(true);
        lineScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        lineScroll.setId("ton-availability-lines");
        Label indexHint =
                TechnicalUi.label("Tasto destro per scegliere il prodotto", "muted-label");
        indexHint.setStyle("-fx-font-size: 12px;");
        rail.getChildren().setAll(lineScroll, indexHint);
        rail.setId("ton-availability-index");
        rail.getStyleClass().add("availability-rail");
        rail.setMinWidth(Region.USE_PREF_SIZE);
        rail.setMaxWidth(Region.USE_PREF_SIZE);
        rail.setPrefWidth(95);
        VBox.setVgrow(lineScroll, Priority.ALWAYS);
        VBox card = new VBox(table);
        card.getStyleClass().add("availability-table-card");
        card.setMinWidth(300);
        VBox.setVgrow(table, Priority.ALWAYS);
        content.getChildren().setAll(rail, card);
        content.setMinSize(0, 0);
        HBox.setHgrow(card, Priority.ALWAYS);
        VBox.setVgrow(content, Priority.ALWAYS);
        feedback.setId("ton-availability-feedback");
        feedback.setWrapText(true);
        feedback.getStyleClass().add("ton-error-feedback");
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        pulse.setOnFinished(
                e -> {
                    highlighted = -1;
                    table.refresh();
                });
        getChildren().setAll(header, toolbar, freshness, content, feedback);
    }

    private static Label numberLabel(String id) {
        var l = new Label("—");
        l.setId(id);
        l.getStyleClass().add("availability-total-number");
        return l;
    }

    private static VBox total(String title, Label value) {
        return total(new Label(title), value);
    }

    private static VBox total(Label title, Label value) {
        title.getStyleClass().add("availability-total-label");
        return new VBox(3, title, value);
    }

    public static String number(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private void column(
            String title,
            Function<Row, String> format,
            boolean numeric,
            Function<Row, BigDecimal> warning,
            double width) {
        var c = new TableColumn<Row, Row>(title);
        c.setPrefWidth(width);
        c.setMinWidth(numeric ? 95 : 170);
        c.setSortable(false);
        c.getStyleClass().add(numeric ? "availability-number-column" : "availability-code-column");
        c.setCellValueFactory(v -> new SimpleObjectProperty<>(v.getValue()));
        c.setCellFactory(
                col ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(Row row, boolean empty) {
                                super.updateItem(row, empty);
                                getStyleClass().remove("availability-low");
                                setText(empty || row == null ? null : format.apply(row));
                                setAlignment(numeric ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                                setTooltip(null);
                                if (!empty && row != null) {
                                    if (warning != null
                                            && warning.apply(row) != null
                                            && warning.apply(row).signum() <= 0)
                                        getStyleClass().add("availability-low");
                                    if (!numeric)
                                        setTooltip(
                                                new Tooltip(
                                                        row.article().code()
                                                                + "\n"
                                                                + row.article().productCode()
                                                                + " · "
                                                                + Objects.toString(
                                                                        row.article().lineLabel(),
                                                                        "Linea non definita")
                                                                + (row.article().active()
                                                                        ? ""
                                                                        : " · Archiviato o non"
                                                                                + " configurato")));
                                }
                            }
                        });
        table.getColumns().add(c);
    }

    public void onOpen(Consumer<Row> action) {
        open = action;
    }

    public Position position() {
        Long top = null;
        if (table.lookup(".virtual-flow") instanceof VirtualFlow<?> flow
                && flow.getFirstVisibleCell() != null) {
            int first = flow.getFirstVisibleCell().getIndex();
            if (first >= 0 && first < table.getItems().size())
                top = table.getItems().get(first).article().id();
        }
        var selected = table.getSelectionModel().getSelectedItem();
        return new Position(
                period.getValue(),
                selected == null ? null : selected.article().id(),
                top,
                lineScroll.getVvalue());
    }

    public void show(Snapshot data, Position position) {
        menus.forEach(ContextMenu::hide);
        menus.clear();
        lineButtons.clear();
        lines.getChildren().clear();
        table.getItems().setAll(data.rows());
        var groups = new LinkedHashMap<Long, List<Row>>();
        for (var row : data.rows())
            groups.computeIfAbsent(lineKey(row), k -> new ArrayList<>()).add(row);
        groups.forEach(
                (key, rows) -> {
                    var a = rows.getFirst().article();
                    var b = new Button(Objects.toString(a.lineLabel(), "Da associare"));
                    b.setId("ton-availability-line-" + key);
                    b.setMaxWidth(Double.MAX_VALUE);
                    b.getStyleClass().add("availability-line-button");
                    b.setTooltip(
                            new Tooltip("Clic: vai alla linea. Tasto destro: scegli il prodotto."));
                    b.setOnAction(e -> jumpLine(key));
                    var menu = new ContextMenu();
                    menu.setId("ton-availability-products-" + key);
                    var title = new MenuItem("Vai al prodotto · " + b.getText());
                    title.setDisable(true);
                    menu.getItems().addAll(title, new SeparatorMenuItem());
                    var products = new LinkedHashMap<Long, Article>();
                    rows.forEach(r -> products.putIfAbsent(r.article().productId(), r.article()));
                    products.forEach(
                            (id, product) -> {
                                var item =
                                        new MenuItem(
                                                color(product) + " · " + product.productCode());
                                item.setId("ton-availability-product-" + id);
                                item.setOnAction(e -> jumpProduct(id));
                                menu.getItems().add(item);
                            });
                    b.setContextMenu(menu);
                    menus.add(menu);
                    lines.getChildren().add(b);
                    lineButtons.put(key, b);
                });
        long knownStock = data.rows().stream().filter(r -> r.stock() != null).count();
        long knownOrders = data.rows().stream().filter(r -> r.orders() != null).count();
        stockCaption.setText(
                knownStock > 0 && knownStock < data.rows().size()
                        ? "Disponibilità parziale"
                        : "Disponibilità totale");
        ordersCaption.setText(
                knownOrders > 0 && knownOrders < data.rows().size()
                        ? "Da evadere · parziale"
                        : "Da evadere");
        stockTotal.setText(number(data.total(Row::stock)));
        ordersTotal.setText(number(data.total(Row::orders)));
        soldTotal.setText(number(data.total(Row::sold)));
        stockTotal.setTooltip(coverage(data, Row::stock));
        ordersTotal.setTooltip(coverage(data, Row::orders));
        soldTotal.setTooltip(
                new Tooltip(
                        "Pezzi venduti dal "
                                + DATE.format(data.from())
                                + " al "
                                + DATE.format(data.through())
                                + ". Resi e note di credito esclusi."));
        table.getColumns().get(3).setText("Venduti · " + period.getValue().days() + " gg");
        freshness.setText(
                sourceLabel(data.sources().get(Kind.DISPONIBILITA))
                        + "     ·     "
                        + sourceLabel(data.sources().get(Kind.ORDINI))
                        + "     ·     "
                        + (data.lastSale() == null
                                ? "Vendite non importate"
                                : "Vendite dal "
                                        + DATE.format(data.from())
                                        + " al "
                                        + DATE.format(data.through())));
        freshness.setTooltip(
                new Tooltip(
                        "Le due importazioni sono indipendenti. Disponibilità e ordini sono"
                                + " mostrati come riportati nei CSV."));
        feedback.setText(
                data.unmatchedStock() + data.unmatchedOrders() == 0
                        ? ""
                        : (data.unmatchedStock() + data.unmatchedOrders())
                                + " codici importati non associati al catalogo TON; esclusi dai"
                                + " totali mostrati.");
        sizeLineIndex();
        lineScroll.setVvalue(position.railScroll());
        restore(position);
    }

    /** Measure styled button text once per catalogue refresh, independently of the window width. */
    private void sizeLineIndex() {
        rail.applyCss();
        double longest =
                lineButtons.values().stream().mapToDouble(b -> b.prefWidth(-1)).max().orElse(0);
        var bar = lineScroll.lookup(".scroll-bar:vertical");
        double scrollbar = bar instanceof Region region ? region.prefWidth(-1) : 18;
        // Reserve the scrollbar even when hidden so vertical resizing cannot shorten the names.
        double chrome =
                rail.getInsets().getLeft()
                        + rail.getInsets().getRight()
                        + lineScroll.getInsets().getLeft()
                        + lineScroll.getInsets().getRight();
        rail.setPrefWidth(Math.ceil(Math.max(95, longest + chrome + scrollbar)));
    }

    public void restore(Position position) {
        if (position.selected() != null)
            for (int i = 0; i < table.getItems().size(); i++)
                if (table.getItems().get(i).article().id() == position.selected())
                    table.getSelectionModel().select(i);
        if (position.top() != null)
            for (int i = 0; i < table.getItems().size(); i++)
                if (table.getItems().get(i).article().id() == position.top()) table.scrollTo(i);
    }

    private static Tooltip coverage(Snapshot data, Function<Row, BigDecimal> value) {
        long known = data.rows().stream().filter(r -> value.apply(r) != null).count();
        return new Tooltip(
                "Somma degli articoli mostrati: dati disponibili per "
                        + known
                        + " di "
                        + data.rows().size()
                        + " codici. Il trattino indica un dato non disponibile.");
    }

    private static String sourceLabel(Source s) {
        return s.kind().label()
                + " · "
                + (s.present()
                        ? DATE.format(s.date())
                                + " (import "
                                + s.imported().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                                + ")"
                        : "CSV non importato");
    }

    private static String color(Article a) {
        return "SENZA_COLORE".equals(a.color())
                ? "Senza colore"
                : Objects.toString(a.color(), a.productCode());
    }

    private static long lineKey(Row row) {
        return row.article().lineId() == null ? 0 : row.article().lineId();
    }

    private void selectLine(Row row) {
        lineButtons.forEach(
                (id, b) -> {
                    b.getStyleClass().remove("availability-current-line");
                    if (row != null && id == lineKey(row))
                        b.getStyleClass().add("availability-current-line");
                });
    }

    public void jumpLine(long id) {
        jump(r -> lineKey(r) == id);
    }

    public void jumpProduct(long id) {
        jump(r -> r.article().productId() == id);
    }

    private void jump(Predicate<Row> match) {
        for (int i = 0; i < table.getItems().size(); i++)
            if (match.test(table.getItems().get(i))) {
                highlighted = table.getItems().get(i).article().id();
                table.getSelectionModel().select(i);
                table.getFocusModel().focus(i);
                table.scrollTo(i);
                table.refresh();
                pulse.playFromStart();
                return;
            }
    }

    public void loading(boolean busy) {
        refresh.setDisable(busy);
    }

    public void error(String text) {
        loading(false);
        feedback.setText(text);
    }

    public void importing(boolean busy) {
        stockImport.setDisable(busy);
        ordersImport.setDisable(busy);
        refresh.setDisable(busy);
        period.setDisable(busy);
    }

    public void dispose() {
        pulse.stop();
        menus.forEach(ContextMenu::hide);
        menus.clear();
        open = r -> {};
        table.setOnKeyPressed(null);
        table.getItems().clear();
        lines.getChildren().clear();
    }
}

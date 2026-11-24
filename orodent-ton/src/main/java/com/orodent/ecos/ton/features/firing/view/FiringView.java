package com.orodent.ecos.ton.features.firing.view;

import static com.orodent.ecos.ton.features.firing.model.Firing.*;

import com.orodent.ecos.ton.features.technical.view.*;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** One row per physical disc; a load can contain individually selected discs from several plans. */
public final class FiringView extends TechnicalDetailView {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu")
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private static final String ID = "ton-firing";

    public static final class DiscRow {
        public final Disc disc;
        public final BooleanProperty selected = new SimpleBooleanProperty();

        DiscRow(Disc disc) {
            this.disc = disc;
        }
    }

    public final TableView<DiscRow> pending = new TableView<>();
    public final TableView<Load> loads = new TableView<>();
    public final TableView<Disc> loaded = new TableView<>();
    public final ComboBox<Furnace> furnace = new ComboBox<>();
    public final TextField lot = TechnicalUi.input(ID + "-lot", "Codice lotto manuale");
    public final DatePicker date = new DatePicker(LocalDate.now());
    public final TextArea notes = new TextArea();
    public final Button create = TechnicalUi.button("Registra carico", ID + "-create", true),
            selectAll = TechnicalUi.button("Seleziona visibili", ID + "-select-all", false),
            clear = TechnicalUi.button("Deseleziona", ID + "-clear", false);
    private final TextField search =
            TechnicalUi.input(ID + "-search", "Cerca articolo, prodotto, linea o produzione");
    private final Label totals = TechnicalUi.label("", "ton-technical-panel-title"),
            selected = TechnicalUi.label("0 dischi selezionati", "ton-technical-panel-title"),
            historyDetails =
                    TechnicalUi.label(
                            "Seleziona un carico per consultare i dischi.", "muted-label"),
            emptyFurnaces =
                    TechnicalUi.label(
                            "Nessun forno attivo. Aggiungine uno in Attrezzature e Fornitori →"
                                    + " Forni.",
                            "ton-error-feedback");
    private List<DiscRow> rows = List.of();
    private boolean bulkSelection;
    private final Map<Long, Integer> groups = new HashMap<>();

    public FiringView() {
        super(ID, "← Torna a Cotture");
        headerTitle.set("");
        status(true, totals);
        actions.setVisible(false);
        actions.setManaged(false);
        pending.setId(ID + "-pending-table");
        pending.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        pending.setMinSize(0, 140);
        pending.setPlaceholder(
                new Label(
                        "Nessun disco in attesa. Nelle produzioni preparate usa Registra dischi"
                                + " prodotti."));
        var check = new TableColumn<DiscRow, DiscRow>("");
        check.setCellValueFactory(v -> new SimpleObjectProperty<>(v.getValue()));
        check.setMinWidth(42);
        check.setMaxWidth(42);
        check.setSortable(false);
        check.setCellFactory(
                c ->
                        new TableCell<>() {
                            final CheckBox box = new CheckBox();
                            DiscRow bound;

                            @Override
                            protected void updateItem(DiscRow item, boolean empty) {
                                super.updateItem(item, empty);
                                if (bound != null)
                                    box.selectedProperty().unbindBidirectional(bound.selected);
                                bound = empty ? null : item;
                                if (bound != null)
                                    box.selectedProperty().bindBidirectional(bound.selected);
                                setGraphic(bound == null ? null : box);
                                setAlignment(Pos.CENTER);
                            }
                        });
        pending.getColumns().add(check);
        TechnicalUi.column(pending, "ID disco", 135, r -> r.disc.identity());
        TechnicalUi.column(pending, "Articolo", 180, r -> r.disc.article());
        TechnicalUi.column(pending, "Prodotto", 140, r -> r.disc.product());
        TechnicalUi.column(pending, "Formato", 130, r -> r.disc.format());
        TechnicalUi.column(pending, "Produzione", 130, r -> r.disc.production());
        TechnicalUi.column(pending, "Prodotto il", 120, r -> DATE.format(r.disc.producedAt()));
        pending.setRowFactory(
                t ->
                        new TableRow<>() {
                            @Override
                            protected void updateItem(DiscRow row, boolean empty) {
                                super.updateItem(row, empty);
                                setStyle(
                                        empty || row == null
                                                ? ""
                                                : "-fx-background-color: "
                                                        + (groups.getOrDefault(row.disc.id(), 0) % 2
                                                                        == 0
                                                                ? "white"
                                                                : "#edf5f9")
                                                        + "; -fx-border-color: transparent"
                                                        + " transparent #dce7ed transparent;"
                                                        + " -fx-border-width: 0 0 1 0;");
                            }
                        });
        search.textProperty()
                .addListener(
                        (o, a, b) -> {
                            deselect();
                            filter();
                        });
        selectAll.setOnAction(
                e -> {
                    bulkSelection = true;
                    try {
                        pending.getItems().forEach(r -> r.selected.set(true));
                    } finally {
                        bulkSelection = false;
                    }
                    selectionChanged();
                });
        clear.setOnAction(e -> deselect());
        var searchRow = new HBox(10, search, selectAll, clear);
        HBox.setHgrow(search, Priority.ALWAYS);
        selectAll.setMinWidth(Region.USE_PREF_SIZE);
        clear.setMinWidth(Region.USE_PREF_SIZE);
        var listing = new VBox(12, searchRow, pending);
        listing.setMinWidth(0);
        VBox.setVgrow(pending, Priority.ALWAYS);
        HBox.setHgrow(listing, Priority.ALWAYS);
        furnace.setId(ID + "-furnace");
        furnace.setMinWidth(0);
        furnace.setMaxWidth(Double.MAX_VALUE);
        furnace.setPromptText("Scegli forno attivo");
        date.setId(ID + "-date");
        date.setConverter(new javafx.util.converter.LocalDateStringConverter(DATE, DATE));
        date.setPromptText("gg/mm/aaaa");
        date.setMaxWidth(Double.MAX_VALUE);
        notes.setId(ID + "-notes");
        notes.setPrefRowCount(3);
        notes.setWrapText(true);
        var fields =
                new VBox(
                        14,
                        TechnicalUi.field("Forno *", furnace),
                        emptyFurnaces,
                        TechnicalUi.field("Lotto *", lot),
                        TechnicalUi.field("Data del carico *", date),
                        TechnicalUi.field("Note del carico", notes));
        fields.setMinWidth(0);
        var inputScroll = TechnicalUi.scroll(fields);
        inputScroll.setMinSize(0, 0);
        inputScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(inputScroll, Priority.ALWAYS);
        var assignment =
                TechnicalUi.panel(
                        "Nuovo carico",
                        "I dischi selezionati riceveranno lo stesso forno e lotto.",
                        inputScroll,
                        selected,
                        create);
        assignment.setMinHeight(0);
        assignment.setPrefWidth(300);
        assignment.setMinWidth(260);
        assignment.setMaxWidth(320);
        create.setMaxWidth(Double.MAX_VALUE);
        var queue = new HBox(18, listing, assignment);
        queue.setMinSize(0, 0);
        addSheet("Da caricare", queue, true);
        furnace.valueProperty().addListener((o, a, b) -> selectionChanged());
        lot.textProperty().addListener((o, a, b) -> selectionChanged());
        date.valueProperty().addListener((o, a, b) -> selectionChanged());
        loads.setId(ID + "-loads-table");
        loads.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        loads.setMinHeight(100);
        loads.setPlaceholder(new Label("Nessun carico registrato."));
        TechnicalUi.column(loads, "Carico", 120, Load::number);
        TechnicalUi.column(loads, "Data", 130, l -> DATE.format(l.date()));
        TechnicalUi.column(loads, "Forno", 200, Load::furnace);
        TechnicalUi.column(loads, "Lotto", 180, Load::lot);
        TechnicalUi.column(loads, "Dischi", 80, l -> "" + l.count());
        loaded.setId(ID + "-loaded-table");
        loaded.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        loaded.setMinHeight(120);
        loaded.setPlaceholder(new Label("Seleziona un carico."));
        TechnicalUi.column(loaded, "ID disco", 130, Disc::identity);
        TechnicalUi.column(loaded, "Articolo", 180, Disc::article);
        TechnicalUi.column(loaded, "Prodotto", 140, Disc::product);
        TechnicalUi.column(loaded, "Formato", 130, Disc::format);
        TechnicalUi.column(loaded, "Produzione", 130, Disc::production);
        TechnicalUi.column(loaded, "Prodotto il", 130, d -> DATE.format(d.producedAt()));
        historyDetails.setId(ID + "-load-details");
        var detail = new VBox(12, historyDetails, loaded);
        VBox.setVgrow(loaded, Priority.ALWAYS);
        detail.setMinHeight(180);
        var history = new SplitPane(loads, detail);
        history.setOrientation(Orientation.VERTICAL);
        history.setDividerPositions(.36);
        addSheet("Carichi registrati", history, true);
        show(new Workspace(List.of(), List.of(), List.of()));
    }

    public void show(Workspace data) {
        var selectedIds = new HashSet<>(selectedIds());
        rows = data.pending().stream().map(DiscRow::new).toList();
        groups.clear();
        var products = new LinkedHashMap<String, Integer>();
        for (var row : rows) {
            groups.put(
                    row.disc.id(),
                    products.computeIfAbsent(row.disc.product(), k -> products.size()));
            row.selected.set(selectedIds.contains(row.disc.id()));
            row.selected.addListener((o, a, b) -> selectionChanged());
        }
        Furnace previous = furnace.getValue();
        furnace.getItems().setAll(data.furnaces());
        furnace.setValue(
                previous == null
                        ? null
                        : data.furnaces().stream()
                                .filter(f -> f.id() == previous.id())
                                .findFirst()
                                .orElse(null));
        TechnicalUi.visible(emptyFurnaces, data.furnaces().isEmpty());
        Long previousLoad =
                loads.getSelectionModel().getSelectedItem() == null
                        ? null
                        : loads.getSelectionModel().getSelectedItem().id();
        loads.getItems().setAll(data.loads());
        loads.getSelectionModel()
                .select(
                        previousLoad == null
                                ? null
                                : data.loads().stream()
                                        .filter(l -> l.id() == previousLoad)
                                        .findFirst()
                                        .orElse(null));
        totals.setText(
                (data.pending().size() == 1
                                ? "1 disco da caricare"
                                : data.pending().size() + " dischi da caricare")
                        + " · "
                        + (data.loads().size() == 1
                                ? "1 carico registrato"
                                : data.loads().size() + " carichi registrati"));
        filter();
        selectionChanged();
    }

    private void filter() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        pending.setItems(
                FXCollections.observableArrayList(
                        rows.stream()
                                .filter(
                                        r ->
                                                (r.disc.identity()
                                                                + " "
                                                                + r.disc.article()
                                                                + " "
                                                                + r.disc.product()
                                                                + " "
                                                                + r.disc.line()
                                                                + " "
                                                                + r.disc.production())
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query))
                                .toList()));
    }

    private void selectionChanged() {
        if (bulkSelection) return;
        int count = selectedIds().size();
        selected.setText(count == 1 ? "1 disco selezionato" : count + " dischi selezionati");
        create.setDisable(
                count == 0
                        || furnace.getValue() == null
                        || lot.getText().isBlank()
                        || date.getValue() == null);
    }

    public List<Long> selectedIds() {
        return rows.stream().filter(r -> r.selected.get()).map(r -> r.disc.id()).toList();
    }

    public Request request() {
        LocalDate value;
        try {
            value = date.getConverter().fromString(date.getEditor().getText());
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Data del carico: usa gg/mm/aaaa.");
        }
        return new Request(
                furnace.getValue(), lot.getText(), value, notes.getText(), selectedIds());
    }

    public void deselect() {
        bulkSelection = true;
        try {
            rows.forEach(r -> r.selected.set(false));
        } finally {
            bulkSelection = false;
        }
        selectionChanged();
    }

    public boolean dirty() {
        return !selectedIds().isEmpty() || !lot.getText().isBlank() || !notes.getText().isBlank();
    }

    public void resetAssignment() {
        deselect();
        lot.clear();
        notes.clear();
    }

    public void details(Load load, List<Disc> discs) {
        loaded.getItems().setAll(discs);
        historyDetails.setText(
                load == null
                        ? "Seleziona un carico per consultare i dischi."
                        : load.number()
                                + " · Lotto "
                                + load.lot()
                                + " · "
                                + load.furnace()
                                + " · anagrafica r"
                                + load.furnaceRevision()
                                + "\n"
                                + load.notes());
    }

    public void busy(boolean value) {
        setDisable(value);
    }

    public void dispose() {
        selectAll.setOnAction(null);
        clear.setOnAction(null);
        create.setOnAction(null);
        back().setOnAction(null);
        reload().setOnAction(null);
        pending.getItems().clear();
        loaded.getItems().clear();
        loads.getItems().clear();
    }
}

package com.orodent.ecos.ton.features.firing.view;

import static com.orodent.ecos.ton.features.firing.model.Firing.*;

import com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms.Program;
import com.orodent.ecos.ton.features.technical.view.*;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Quantities are grouped by production and article. Individual identities stay in persistence. */
public final class FiringView extends TechnicalDetailView {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu")
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private static final String ID = "ton-firing";

    public static final class GroupRow {
        public final Group group;
        public final TextField quantity = new TextField("0");

        GroupRow(Group group) {
            this.group = group;
            quantity.setId("ton-firing-quantity-" + group.key().planId() + "-" + group.key().row());
            quantity.setTextFormatter(
                    new TextFormatter<String>(
                            change ->
                                    change.getControlNewText().matches("[0-9]{0,7}")
                                            ? change
                                            : null));
            quantity.setTooltip(new Tooltip("Disponibili: " + group.count()));
        }

        public int count() {
            return quantity.getText().isBlank() ? 0 : Integer.parseInt(quantity.getText());
        }
    }

    public final TableView<GroupRow> pending = new TableView<>();
    public final TableView<Load> loads = new TableView<>();
    public final TableView<Group> loaded = new TableView<>();
    public final ComboBox<Furnace> furnace = new ComboBox<>();
    public final ComboBox<Program> program = new ComboBox<>();
    public final TextField finalTemperature =
            TechnicalUi.input(ID + "-final-temperature", "Temperatura finale in °C");
    private final Label programNotice = TechnicalUi.label("", "muted-label");
    private Workspace workspace = new Workspace(List.of(), List.of(), List.of(), List.of());
    private boolean updating, manualProgram;
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
    private List<GroupRow> rows = List.of();
    private boolean bulkSelection;
    private final Map<GroupKey, Integer> groups = new HashMap<>();

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
                        "Nessun disco in attesa. Conferma una produzione per renderne disponibili i"
                            + " pezzi."));
        TechnicalUi.column(pending, "Produzione", 130, r -> r.group.reference().production());
        TechnicalUi.column(pending, "Articolo", 180, r -> r.group.reference().article());
        TechnicalUi.column(pending, "Formato", 130, r -> r.group.reference().format());
        TechnicalUi.column(pending, "Disponibili", 100, r -> "" + r.group.count());
        var quantity = new TableColumn<GroupRow, GroupRow>("Da caricare");
        quantity.setCellValueFactory(v -> new SimpleObjectProperty<>(v.getValue()));
        quantity.setSortable(false);
        quantity.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(GroupRow row, boolean empty) {
                                super.updateItem(row, empty);
                                setGraphic(empty || row == null ? null : row.quantity);
                            }
                        });
        pending.getColumns().add(quantity);
        pending.setRowFactory(
                t ->
                        new TableRow<>() {
                            @Override
                            protected void updateItem(GroupRow row, boolean empty) {
                                super.updateItem(row, empty);
                                setStyle(
                                        empty || row == null
                                                ? ""
                                                : "-fx-background-color: "
                                                        + (groups.getOrDefault(row.group.key(), 0)
                                                                                % 2
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
                        pending.getItems().forEach(r -> r.quantity.setText("" + r.group.count()));
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
        program.setId(ID + "-program");
        program.setPromptText("Scegli programma approvato");
        program.setMinWidth(0);
        program.setMaxWidth(Double.MAX_VALUE);
        programNotice.setId(ID + "-program-notice");
        programNotice.visibleProperty().bind(programNotice.textProperty().isNotEmpty());
        programNotice.managedProperty().bind(programNotice.visibleProperty());
        program.valueProperty()
                .addListener(
                        (o, a, b) -> {
                            if (updating) return;
                            manualProgram =
                                    b != null
                                            && (lastProgram() == null
                                                    || b.id() != lastProgram().id());
                            program.setTooltip(b == null ? null : new Tooltip(b.toString()));
                            programNotice();
                            selectionChanged();
                        });
        finalTemperature.textProperty().addListener((o, a, b) -> selectionChanged());
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
                        TechnicalUi.field("Programma di cottura *", program),
                        programNotice,
                        TechnicalUi.field("Temperatura finale · °C *", finalTemperature),
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
                        "Le quantità indicate riceveranno lo stesso forno e lotto.",
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
        furnace.valueProperty()
                .addListener(
                        (o, a, b) -> {
                            if (updating) return;
                            finalTemperature.clear();
                            manualProgram = false;
                            selectRememberedProgram();
                            selectionChanged();
                        });
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
        TechnicalUi.column(
                loads, "Programma", 240, l -> l.program() == null ? "—" : l.program().toString());
        TechnicalUi.column(
                loads,
                "T finale · °C",
                110,
                l -> l.finalTemperature() == null ? "—" : number(l.finalTemperature()));
        TechnicalUi.column(
                loads, "Cambio programma", 130, l -> l.programChanged() ? "Da cambiare" : "");
        TechnicalUi.column(loads, "Dischi", 80, l -> "" + l.count());
        loaded.setId(ID + "-loaded-table");
        loaded.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        loaded.setMinHeight(120);
        loaded.setPlaceholder(new Label("Seleziona un carico."));
        TechnicalUi.column(loaded, "Produzione", 130, g -> g.reference().production());
        TechnicalUi.column(loaded, "Articolo", 180, g -> g.reference().article());

        TechnicalUi.column(loaded, "Formato", 130, g -> g.reference().format());
        TechnicalUi.column(loaded, "Quantità", 100, g -> "" + g.count());

        historyDetails.setId(ID + "-load-details");
        var detail = new VBox(12, historyDetails, loaded);
        VBox.setVgrow(loaded, Priority.ALWAYS);
        detail.setMinHeight(180);
        var history = new SplitPane(loads, detail);
        history.setOrientation(Orientation.VERTICAL);
        history.setDividerPositions(.36);
        addSheet("Carichi registrati", history, true);
        show(workspace);
    }

    public void show(Workspace data) {
        var previousQuantities = new HashMap<GroupKey, String>();
        rows.forEach(row -> previousQuantities.put(row.group.key(), row.quantity.getText()));
        rows = group(data.pending()).stream().map(GroupRow::new).toList();
        groups.clear();
        var products = new LinkedHashMap<String, Integer>();
        for (var row : rows) {
            groups.put(
                    row.group.key(),
                    products.computeIfAbsent(
                            row.group.reference().product(), k -> products.size()));
            row.quantity.setText(previousQuantities.getOrDefault(row.group.key(), "0"));
            row.quantity
                    .textProperty()
                    .addListener(
                            (o, a, b) -> {
                                feedback("", false);
                                selectionChanged();
                            });
        }
        Furnace previous = furnace.getValue();
        Program previousProgram = program.getValue();
        workspace = data;
        updating = true;
        furnace.getItems().setAll(data.furnaces());
        furnace.setValue(
                previous == null
                        ? null
                        : data.furnaces().stream()
                                .filter(f -> f.id() == previous.id())
                                .findFirst()
                                .orElse(null));
        program.getItems().setAll(data.programs());
        program.setValue(
                manualProgram && previousProgram != null
                        ? data.programs().stream()
                                .filter(p -> p.id() == previousProgram.id())
                                .findFirst()
                                .orElse(null)
                        : rememberedProgram());
        updating = false;
        programNotice();
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

    private Program lastProgram() {
        return furnace.getValue() == null
                ? null
                : workspace.lastPrograms().get(furnace.getValue().id());
    }

    private Program rememberedProgram() {
        Program last = lastProgram();
        return last == null
                ? null
                : workspace.programs().stream()
                        .filter(p -> p.id() == last.id())
                        .findFirst()
                        .orElse(null);
    }

    private void selectRememberedProgram() {
        updating = true;
        try {
            program.setValue(rememberedProgram());
        } finally {
            updating = false;
        }
        programNotice();
    }

    private void programNotice() {
        Program last = lastProgram(), chosen = program.getValue();
        program.setTooltip(chosen == null ? null : new Tooltip(chosen.toString()));
        String text = "";
        if (workspace.programs().isEmpty())
            text =
                    "Nessun programma approvato disponibile. Configuralo in Cotture → Programmi di"
                        + " cottura.";
        else if (furnace.getValue() != null) {
            if (last == null)
                text =
                        "Nessun programma precedente per questo forno: scegli quello impostato"
                            + " sulla macchina.";
            else if (chosen == null)
                text =
                        "Ultimo usato: "
                                + last
                                + ". Non più disponibile: scegli un programma approvato.";
            else if (last.id() != chosen.id())
                text =
                        "CAMBIO PROGRAMMA: ultimo usato "
                                + last
                                + ". Impostare il programma selezionato sul forno.";
            else text = "Ultimo programma usato su questo forno.";
        }
        if (chosen != null && chosen.steps().stream().noneMatch(s -> s.usesFinalTemperature()))
            text +=
                    (text.isEmpty() ? "" : "\n")
                            + "Questo programma usa temperature fisse: Tfinale non è presente negli"
                            + " step.";
        programNotice.setText(text);
    }

    private void filter() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        pending.setItems(
                FXCollections.observableArrayList(
                        rows.stream()
                                .filter(
                                        r ->
                                                (r.group.reference().production()
                                                                + " "
                                                                + r.group.reference().article()
                                                                + " "
                                                                + r.group.reference().product()
                                                                + " "
                                                                + r.group.reference().line()
                                                                + " "
                                                                + r.group.reference().production())
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(query))
                                .toList()));
    }

    private void selectionChanged() {
        if (bulkSelection) return;
        long count = rows.stream().mapToLong(GroupRow::count).sum();
        selected.setText(count == 1 ? "1 disco selezionato" : count + " dischi selezionati");
        create.setDisable(
                count == 0
                        || furnace.getValue() == null
                        || program.getValue() == null
                        || finalTemperature.getText().isBlank()
                        || lot.getText().isBlank()
                        || date.getValue() == null);
    }

    public List<Quantity> quantities() {
        var values = new ArrayList<Quantity>();
        for (var row : rows) {
            int count = row.count();
            if (count > row.group.count())
                throw new IllegalArgumentException(
                        row.group.reference().article()
                                + ": disponibili "
                                + row.group.count()
                                + " dischi.");
            if (count > 0) values.add(new Quantity(row.group.key(), count));
        }
        return List.copyOf(values);
    }

    public Request request() {
        LocalDate value;
        try {
            value = date.getConverter().fromString(date.getEditor().getText());
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Data del carico: usa gg/mm/aaaa.");
        }
        return new Request(
                furnace.getValue(),
                lot.getText(),
                value,
                notes.getText(),
                quantities(),
                program.getValue(),
                TechnicalUi.decimal(finalTemperature.getText(), "Temperatura finale"));
    }

    public void deselect() {
        bulkSelection = true;
        try {
            rows.forEach(r -> r.quantity.setText("0"));
        } finally {
            bulkSelection = false;
        }
        selectionChanged();
    }

    public boolean dirty() {
        return rows.stream().anyMatch(r -> r.count() > 0)
                || !lot.getText().isBlank()
                || !notes.getText().isBlank()
                || !finalTemperature.getText().isBlank()
                || manualProgram && program.getValue() != null;
    }

    public void resetAssignment() {
        deselect();
        lot.clear();
        notes.clear();
        finalTemperature.clear();
        manualProgram = false;
    }

    public void details(Load load, List<Disc> discs) {
        loaded.getItems().setAll(group(discs));
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
                                + "\nProgramma: "
                                + (load.program() == null ? "non registrato" : load.program())
                                + " · Temperatura finale: "
                                + (load.finalTemperature() == null
                                        ? "non registrata"
                                        : number(load.finalTemperature()) + " °C")
                                + (load.programChanged()
                                        ? "\nCAMBIO PROGRAMMA: da "
                                                + load.previousProgram()
                                                + " a "
                                                + load.program()
                                                + ". Impostare il programma sul forno."
                                        : "")
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

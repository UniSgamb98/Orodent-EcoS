package com.orodent.ecos.misurazioni.jobs.view;

import static com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.*;

import com.orodent.ecos.misurazioni.devices.DeviceConnections;
import com.orodent.ecos.misurazioni.model.Instrument;

import javafx.beans.property.*;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

/** Product navigation and grouped work rows share the same selection, search and progress. */
public final class MeasurementJobsView extends BorderPane {
    public record Row(Job job, String title, int works, long remaining) {
        public boolean group() {
            return job == null;
        }
    }

    public final Button home = button("⌂ Home Orodent-EcoS", "jobs-home"),
            pending = button("Lavori da completare", "jobs-pending"),
            completed = button("Completati", "jobs-completed"),
            tools = button("Strumenti", "jobs-tools"),
            refresh = button("Aggiorna", "jobs-refresh"),
            open = button("Apri misurazioni", "jobs-open"),
            back = button("← Lavori", "jobs-back"),
            retry = button("Riprova salvataggio", "jobs-retry"),
            reload = button("Ricarica lavoro", "jobs-reload");
    public final TextField search = new TextField(), productSearch = new TextField();
    public final TableView<Row> table = new TableView<>();
    public final Label feedback = new Label(), saveState = new Label();
    private final Label breadcrumb = new Label("Misurazioni › Lavori da completare"),
            title = new Label("Lavori da misurare"),
            jobContext = new Label();
    private final HBox summary = new HBox(22);
    private final VBox products = new VBox(7),
            navigation = new VBox(12),
            workPanel = new VBox(12),
            readingsPanel = new VBox(10),
            toolsPanel = new VBox(16);
    private final Map<Instrument, Button> states = new EnumMap<>(Instrument.class),
            deviceActions = new EnumMap<>(Instrument.class);
    private List<Job> jobs = List.of();
    private boolean showCompleted;
    private Runnable onOpen = () -> {};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    public MeasurementJobsView() {
        setId("measurement-jobs-module");
        setMinSize(0, 0);
        getStylesheets().add(getClass().getResource("/misurazioni/jobs.css").toExternalForm());
        home.getStyleClass().add("jobs-primary");
        var hide = button("Chiudi menu", "jobs-menu");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(24, home, breadcrumb, spacer, hide);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("jobs-header");
        setTop(header);
        navigation.getStyleClass().add("jobs-navigation");
        navigation.setPrefWidth(215);
        navigation.setMinWidth(0);
        navigation.setMaxWidth(215);
        navigation
                .getChildren()
                .addAll(label("MISURAZIONI", "jobs-section"), pending, completed, tools);
        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        navigation.getChildren().add(grow);
        for (var instrument : Instrument.values()) {
            var state =
                    button(
                            "● " + instrument.label(),
                            "jobs-state-" + instrument.name().toLowerCase(Locale.ROOT));
            state.setWrapText(true);
            states.put(instrument, state);
            navigation.getChildren().add(state);
        }
        setLeft(navigation);
        hide.setOnAction(
                e -> {
                    boolean visible = !navigation.isVisible();
                    navigation.setVisible(visible);
                    navigation.setManaged(visible);
                    hide.setText(visible ? "Chiudi menu" : "Apri menu");
                });
        title.getStyleClass().add("jobs-title");
        summary.setId("jobs-summary");
        var heading =
                new HBox(
                        20,
                        new VBox(
                                5,
                                title,
                                new Label("Seleziona un lavoro per acquisire le misure.")),
                        spacer(),
                        summary);
        heading.setAlignment(Pos.CENTER_LEFT);
        search.setPromptText("Cerca articolo, lotto o forno…");
        search.setId("jobs-search");
        HBox.setHgrow(search, Priority.ALWAYS);
        var toolbar = new HBox(10, search, refresh);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        productSearch.setPromptText("Cerca prodotto…");
        productSearch.setId("jobs-product-search");
        var productScroll = new ScrollPane(products);
        productScroll.setFitToWidth(true);
        productScroll.setMinSize(0, 0);
        VBox.setVgrow(productScroll, Priority.ALWAYS);
        var productPanel =
                new VBox(12, label("Prodotti", "jobs-panel-title"), productSearch, productScroll);
        productPanel.getStyleClass().add("jobs-products");
        productPanel.setPrefWidth(185);
        productPanel.setMinWidth(155);
        productPanel.setMaxWidth(215);
        table.setId("jobs-table");
        table.setMinSize(0, 100);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("Nessun lavoro disponibile."));
        VBox.setVgrow(table, Priority.ALWAYS);
        column(
                "Articolo",
                220,
                r -> r.group() ? r.title() : r.job().article() + "\n" + r.job().format());
        column(
                "Lotto · Forno",
                230,
                r ->
                        r.group()
                                ? r.works() + (r.works() == 1 ? " lavoro" : " lavori")
                                : r.job().lot() + "\n" + r.job().furnace());
        column(
                "Fine cottura prevista",
                160,
                r ->
                        r.group()
                                ? ""
                                : r.job().finished() == null
                                        ? "—"
                                        : DATE.format(r.job().finished()));
        var progress = new TableColumn<Row, Row>("Misurazioni");
        progress.setCellValueFactory(v -> new SimpleObjectProperty<>(v.getValue()));
        progress.setSortable(false);
        progress.setPrefWidth(190);
        progress.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(Row row, boolean empty) {
                                super.updateItem(row, empty);
                                setText(null);
                                setGraphic(null);
                                if (empty || row == null) return;
                                if (row.group()) {
                                    setText(
                                            row.remaining()
                                                    + (showCompleted
                                                            ? " dischi misurati"
                                                            : " dischi rimanenti"));
                                    return;
                                }
                                Job j = row.job();
                                var bar = new ProgressBar((double) j.done() / j.readings().size());
                                bar.setMaxWidth(Double.MAX_VALUE);
                                setGraphic(
                                        new VBox(
                                                3,
                                                new Label(j.done() + " / " + j.readings().size()),
                                                bar,
                                                new Label(j.remaining() + " da misurare")));
                            }
                        });
        table.getColumns().add(progress);
        var status = new TableColumn<Row, Row>("Stato");
        status.setPrefWidth(135);
        status.setSortable(false);
        status.setCellValueFactory(v -> new SimpleObjectProperty<>(v.getValue()));
        status.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(Row row, boolean empty) {
                                super.updateItem(row, empty);
                                setText(null);
                                setGraphic(null);
                                if (empty || row == null || row.group()) return;
                                var badge = new Label(row.job().status());
                                badge.setWrapText(true);
                                badge.getStyleClass().add("jobs-status");
                                badge.pseudoClassStateChanged(
                                        javafx.css.PseudoClass.getPseudoClass("in-progress"),
                                        row.job().touched() && !row.job().complete());
                                setGraphic(badge);
                            }
                        });
        table.getColumns().add(status);
        table.setRowFactory(
                t -> {
                    var row = new TableRow<Row>();
                    row.itemProperty()
                            .addListener(
                                    (o, a, b) ->
                                            row.pseudoClassStateChanged(
                                                    javafx.css.PseudoClass.getPseudoClass(
                                                            "product-group"),
                                                    b != null && b.group()));
                    row.setOnMouseClicked(
                            e -> {
                                if (e.getButton() == MouseButton.PRIMARY
                                        && e.getClickCount() == 2
                                        && row.getItem() != null
                                        && !row.getItem().group()) onOpen.run();
                            });
                    return row;
                });
        table.setOnKeyPressed(
                e -> {
                    if (e.getCode() == KeyCode.ENTER) {
                        onOpen.run();
                        e.consume();
                    }
                });
        table.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, a, b) -> {
                            open.setDisable(b == null || b.group());
                            open.setText(
                                    b != null && !b.group() && b.job().touched()
                                            ? "Riprendi misurazione"
                                            : "Apri misurazioni");
                        });
        open.getStyleClass().add("jobs-primary");
        open.setOnAction(e -> onOpen.run());
        open.setDisable(true);
        var footer =
                new HBox(
                        15,
                        new Label("Doppio clic su un lavoro per aprire Misurazioni"),
                        spacer(),
                        open);
        footer.setAlignment(Pos.CENTER_LEFT);
        var listPanel = new VBox(12, toolbar, table, footer);
        listPanel.setMinWidth(0);
        HBox.setHgrow(listPanel, Priority.ALWAYS);
        var lists = new HBox(16, productPanel, listPanel);
        lists.getStyleClass().add("jobs-card");
        lists.setMinSize(0, 0);
        VBox.setVgrow(lists, Priority.ALWAYS);
        feedback.setId("jobs-feedback");
        feedback.setWrapText(true);
        feedback.setMinHeight(Region.USE_PREF_SIZE);
        feedback.getStyleClass().add("jobs-feedback");
        workPanel
                .getChildren()
                .addAll(
                        heading,
                        lists,
                        new Label(
                                "Disponibilità dei lavori basata sulla fine prevista della"
                                    + " cottura."),
                        feedback);
        workPanel.getStyleClass().add("jobs-main");
        workPanel.setMinSize(0, 0);
        jobContext.setWrapText(true);
        jobContext.setId("jobs-context");
        jobContext.setMinHeight(Region.USE_PREF_SIZE);
        saveState.setId("jobs-save-state");
        saveState.setWrapText(true);
        saveState.setMinHeight(Region.USE_PREF_SIZE);
        retry.setVisible(false);
        retry.setManaged(false);
        reload.setVisible(false);
        reload.setManaged(false);
        readingsPanel.setMinSize(0, 0);
        readingsPanel.getStyleClass().add("jobs-main");
        search.textProperty().addListener((o, a, b) -> render());
        productSearch.textProperty().addListener((o, a, b) -> renderProducts());
        setCenter(workPanel);
    }

    public void queue(boolean completed) {
        show(jobs, completed);
    }

    public void onOpen(Runnable action) {
        onOpen = action;
    }

    public Job selected() {
        var row = table.getSelectionModel().getSelectedItem();
        return row == null ? null : row.job();
    }

    public void show(List<Job> data, boolean completed) {
        jobs = List.copyOf(data);
        showCompleted = completed;
        title.setText(completed ? "Lavori completati" : "Lavori da misurare");
        breadcrumb.setText("Misurazioni › " + (completed ? "Completati" : "Lavori da completare"));
        setCenter(workPanel);
        pending.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("active-section"), !completed);
        this.completed.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("active-section"), completed);
        render();
    }

    public void showJob(Job job, String rule, Node measurements) {
        breadcrumb.setText("Misurazioni › " + job.article() + " › " + job.lot());
        jobContext.setText(
                job.name()
                        + " · "
                        + job.article()
                        + " · "
                        + job.format()
                        + "\nLotto "
                        + job.lot()
                        + " · "
                        + job.furnace()
                        + " · "
                        + job.production()
                        + "\n"
                        + rule);
        var top = new HBox(12, back, jobContext);
        HBox.setHgrow(jobContext, Priority.ALWAYS);
        var status = new HBox(12, saveState, spacer(), retry, reload);
        status.setAlignment(Pos.CENTER_LEFT);
        readingsPanel.getChildren().setAll(top, measurements, status);
        VBox.setVgrow(measurements, Priority.ALWAYS);
        setCenter(readingsPanel);
    }

    public void tools(String endpoints, Consumer<Instrument> toggle) {
        toolsPanel
                .getChildren()
                .setAll(label("Strumenti di misurazione", "jobs-title"), new Label(endpoints));
        deviceActions.clear();
        for (var instrument : Instrument.values()) {
            var action =
                    button(
                            "Collega / scollega " + instrument.label(),
                            "jobs-device-" + instrument.name().toLowerCase(Locale.ROOT));
            action.setOnAction(e -> toggle.accept(instrument));
            deviceActions.put(instrument, action);
            toolsPanel.getChildren().add(action);
        }
        toolsPanel.getStyleClass().add("jobs-main");
        breadcrumb.setText("Misurazioni › Strumenti");
        setCenter(toolsPanel);
    }

    public void connection(Instrument instrument, DeviceConnections.State state) {
        var control = states.get(instrument);
        control.setText(
                "● "
                        + instrument.label()
                        + (state.busy()
                                ? " · in corso"
                                : state.connected() ? " connesso" : " scollegato"));
        control.setStyle(
                "-fx-text-fill: "
                        + (state.busy() ? "#926b17" : state.connected() ? "#227548" : "#a73d34")
                        + ";");
        control.setTooltip(new Tooltip("Clic per collegare o scollegare"));
        var action = deviceActions.get(instrument);
        if (action != null) action.setDisable(state.busy());
    }

    public Button stateButton(Instrument instrument) {
        return states.get(instrument);
    }

    public void busy(boolean value) {
        table.setDisable(value);
        refresh.setDisable(value);
        open.setDisable(value || selected() == null);
    }

    public void saving(boolean value) {
        back.setDisable(value);
        home.setDisable(value);
        pending.setDisable(value);
        completed.setDisable(value);
        tools.setDisable(value);
        retry.setDisable(value);
        reload.setDisable(value);
    }

    public void saveStatus(String text, boolean error) {
        saveState.setText(text);
        retry.setVisible(error);
        retry.setManaged(error);
        reload.setVisible(error);
        reload.setManaged(error);
    }

    private List<Job> filtered() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        return jobs.stream()
                .filter(j -> j.complete() == showCompleted)
                .filter(
                        j ->
                                (j.product()
                                                + " "
                                                + j.name()
                                                + " "
                                                + j.article()
                                                + " "
                                                + j.lot()
                                                + " "
                                                + j.furnace())
                                        .toLowerCase(Locale.ROOT)
                                        .contains(query))
                .toList();
    }

    private void render() {
        Job selected = selected();
        var groups = new LinkedHashMap<String, List<Job>>();
        for (var j : filtered()) groups.computeIfAbsent(j.product(), k -> new ArrayList<>()).add(j);
        var rows = new ArrayList<Row>();
        groups.forEach(
                (key, values) -> {
                    rows.add(
                            new Row(
                                    null,
                                    values.getFirst().name(),
                                    values.size(),
                                    values.stream()
                                            .mapToLong(
                                                    j -> showCompleted ? j.done() : j.remaining())
                                            .sum()));
                    values.forEach(j -> rows.add(new Row(j, "", 0, 0)));
                });
        table.getItems().setAll(rows);
        if (selected != null)
            table.getItems().stream()
                    .filter(r -> !r.group() && r.job().key().equals(selected.key()))
                    .findFirst()
                    .ifPresent(r -> table.getSelectionModel().select(r));
        summary.getChildren()
                .setAll(
                        counter(
                                showCompleted ? "Dischi misurati" : "Dischi da misurare",
                                filtered().stream()
                                        .mapToLong(j -> showCompleted ? j.done() : j.remaining())
                                        .sum()),
                        counter("Lavori", filtered().size()),
                        counter(
                                "In corso",
                                filtered().stream()
                                        .filter(j -> j.touched() && !j.complete())
                                        .count()));
        renderProducts();
    }

    private void renderProducts() {
        products.getChildren().clear();
        String query = productSearch.getText().strip().toLowerCase(Locale.ROOT);
        String line = "";
        var seen = new HashSet<String>();
        for (var job : filtered())
            if (seen.add(job.product())
                    && (job.product() + " " + job.name())
                            .toLowerCase(Locale.ROOT)
                            .contains(query)) {
                if (!line.equals(job.line())) {
                    line = job.line();
                    products.getChildren()
                            .add(label(line.toUpperCase(Locale.ROOT), "jobs-section"));
                }
                var button = button(job.name(), "jobs-product-" + job.product());
                button.setWrapText(true);
                button.setMaxWidth(Double.MAX_VALUE);
                button.setMinWidth(0);
                button.setTooltip(new Tooltip(job.product()));
                button.setOnAction(
                        e -> {
                            for (int i = 0; i < table.getItems().size(); i++) {
                                var row = table.getItems().get(i);
                                if (!row.group() && row.job().product().equals(job.product())) {
                                    table.getSelectionModel().select(i);
                                    table.scrollTo(Math.max(0, i - 1));
                                    break;
                                }
                            }
                        });
                products.getChildren().add(button);
            }
    }

    private VBox counter(String text, long value) {
        return new VBox(3, new Label(text), label("" + value, "jobs-total"));
    }

    private void column(String name, double width, Function<Row, String> text) {
        var c = new TableColumn<Row, String>(name);
        c.setPrefWidth(width);
        c.setSortable(false);
        c.setCellValueFactory(v -> new SimpleStringProperty(text.apply(v.getValue())));
        table.getColumns().add(c);
    }

    private static Region spacer() {
        var r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static Label label(String text, String style) {
        var l = new Label(text);
        l.getStyleClass().add(style);
        return l;
    }

    private static Button button(String text, String id) {
        var b = new Button(text);
        b.setId(id);
        return b;
    }
}

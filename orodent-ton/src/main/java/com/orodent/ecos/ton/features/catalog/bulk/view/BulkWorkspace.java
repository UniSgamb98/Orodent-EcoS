package com.orodent.ecos.ton.features.catalog.bulk.view;

import static com.orodent.ecos.ton.features.catalog.bulk.model.BulkCatalogModels.*;
import static com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import com.orodent.ecos.ton.features.catalog.bulk.model.CodeComposition;
import com.orodent.ecos.ton.features.catalog.bulk.model.CodeComposition.Values;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;

import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.*;

/** The two cards in the approved prototype. All editable fields keep their row identity. */
public final class BulkWorkspace extends GridPane {
    public static final class Row {
        final int key;
        final Parent parent;
        final StringProperty code, color, diameter, height;
        final BooleanProperty selected = new SimpleBooleanProperty(true);
        final ObjectProperty<State> state = new SimpleObjectProperty<>(State.EXCLUDED);
        final StringProperty message = new SimpleStringProperty("Da verificare");
        final String initialCode, initialDiameter, initialHeight;
        final Combination combination;

        Row(int key, Parent parent, String code, String color, String diameter, String height) {
            this.key = key;
            this.parent = parent;
            this.code = new SimpleStringProperty(code);
            this.color = new SimpleStringProperty(color);
            this.diameter = new SimpleStringProperty(diameter);
            this.height = new SimpleStringProperty(height);
            initialCode = code;
            initialDiameter = diameter;
            initialHeight = height;
            combination = new Combination(parent.id(), color, diameter, height);
        }

        public Seed seed() {
            return new Seed(
                    key,
                    parent,
                    code.get(),
                    color.get(),
                    diameter.get(),
                    height.get(),
                    selected.get());
        }

        String fingerprint() {
            return seed().toString();
        }

        void preserveEdits(Row previous) {
            if (!previous.code.get().equals(previous.initialCode)) code.set(previous.code.get());
            if (!previous.diameter.get().equals(previous.initialDiameter))
                diameter.set(previous.diameter.get());
            if (!previous.height.get().equals(previous.initialHeight))
                height.set(previous.height.get());
            selected.set(previous.selected.get());
        }
    }

    private record Combination(long parentId, String color, String diameter, String height) {}

    private final CatalogKind kind;
    private final String id;
    private final VBox left = new VBox(12), right = new VBox(12);
    private final BulkParentPicker parents, lineFilter;
    private final LinkedHashSet<String> colors = new LinkedHashSet<>();
    private final FlowPane palette = new FlowPane(6, 6);
    private final LinkedHashMap<String, CheckBox> colorChecks = new LinkedHashMap<>();
    private final TextField extraColor = new TextField(),
            diameters = new TextField("98"),
            heights = new TextField("12; 14; 16; 18; 20");
    private final TextArea notes = new TextArea();
    private final Label notice = new Label(), feedback = new Label();
    private final Label totalValue = new Label("0"),
            newValue = new Label("0"),
            existingValue = new Label("0"),
            invalidValue = new Label("0");
    private final VBox invalidMetric;
    private final Button compose = new Button("Personalizza composizione…"),
            save,
            openList = new Button();
    private final TableView<Row> table = new TableView<>();
    private final ObjectProperty<CodeComposition> rule;
    private Runnable inputs = () -> {}, changed = () -> {};
    private boolean updating, wide;
    private Preview preview;
    private List<String> baseline = List.of(), generatedInputs = List.of();

    public BulkWorkspace(CatalogKind kind) {
        this.kind = kind;
        id = "ton-bulk-" + kind.pluralId();
        setId(id);
        setMinWidth(0);
        setHgap(18);
        setVgap(18);
        getStyleClass().add("bulk-workspace");
        rule = new SimpleObjectProperty<>(CodeComposition.defaults(kind));
        parents =
                new BulkParentPicker(kind.parent(), kind == CatalogKind.ARTICLES, id + "-parents");
        lineFilter =
                kind == CatalogKind.ARTICLES
                        ? new BulkParentPicker(CatalogKind.LINES, false, id + "-line-filter")
                        : null;
        left.getStyleClass().add("bulk-card");
        right.getStyleClass().add("bulk-card");
        left.setMinWidth(0);
        right.setMinWidth(0);
        left.setMaxWidth(Double.MAX_VALUE);
        right.setMaxWidth(Double.MAX_VALUE);
        if (kind == CatalogKind.PRODUCTS) {
            Label lineLabel = label("Linea", "bulk-field-label");
            left.getChildren()
                    .addAll(
                            lineLabel,
                            parents,
                            label("Colori commerciali", "bulk-field-label"),
                            hint("Un prodotto per ogni colore selezionato."),
                            palette);
            for (String color :
                    List.of(
                            "A0", "A1", "A2", "A3", "A3.5", "A4", "B1", "B2", "B3", "C1", "C2",
                            "C3", "D2", "D3")) addColorChoice(color);
            extraColor.setPromptText("Altro colore");
            extraColor.setId(id + "-extra-color");
            Button add = new Button("+");
            add.setAccessibleText("Aggiungi colore commerciale");
            add.setId(id + "-add-color");
            add.setOnAction(e -> addExtraColor());
            extraColor.setOnAction(e -> addExtraColor());
            HBox extra = new HBox(6, extraColor, add);
            HBox.setHgrow(extraColor, Priority.ALWAYS);
            Button all = link("Seleziona tutti", id + "-all-colors"),
                    clear = link("Svuota selezione", id + "-clear-colors");
            all.setOnAction(
                    e -> {
                        colors.clear();
                        colors.addAll(colorChecks.keySet());
                        colorChecks.forEach((c, b) -> b.setSelected(true));
                        selectionChanged();
                    });
            clear.setOnAction(
                    e -> {
                        colors.clear();
                        colorChecks.values().forEach(b -> b.setSelected(false));
                        selectionChanged();
                    });
            left.getChildren().addAll(extra, new FlowPane(6, 6, all, clear));
        } else {
            left.getChildren()
                    .addAll(
                            label("Linea dei prodotti", "bulk-field-label"),
                            lineFilter,
                            label("Prodotti a cui aggiungere articoli", "bulk-field-label"),
                            parents);
            diameters.setId(id + "-diameters");
            heights.setId(id + "-heights");
            diameters.setAccessibleText("Diametri in millimetri");
            heights.setAccessibleText("Altezze in millimetri");
            VBox ds = field("Diametri · mm", diameters), hs = field("Altezze · mm", heights);
            HBox dimensions = new HBox(10, ds, hs);
            HBox.setHgrow(ds, Priority.ALWAYS);
            HBox.setHgrow(hs, Priority.ALWAYS);
            ds.setMinWidth(0);
            hs.setMinWidth(0);
            left.getChildren()
                    .addAll(
                            dimensions,
                            hint(
                                    "Separa le misure con “;”. Gli stessi formati verranno creati"
                                            + " per tutti i prodotti selezionati."));
        }
        compose.setId(id + "-rule");
        compose.setMaxWidth(Double.MAX_VALUE);
        compose.setOnAction(
                e ->
                        CodeCompositionDialog.show(this, kind, rule.get(), sample())
                                .ifPresent(rule::set));
        left.getChildren().add(compose);
        notes.setId(id + "-notes");
        notes.setWrapText(true);
        notes.setPrefRowCount(3);
        notes.setPromptText("Applicate ai nuovi elementi");
        left.getChildren().add(field("Note comuni · facoltative", notes));
        if (kind == CatalogKind.ARTICLES) {
            right.getChildren()
                    .addAll(
                            label("Controlla l'anteprima", "bulk-section-title"),
                            hint("Codici e misure possono essere corretti riga per riga."));
        }
        VBox total = metric(totalValue, "Righe in anteprima"),
                fresh =
                        metric(
                                newValue,
                                kind == CatalogKind.PRODUCTS
                                        ? "Prodotti da creare"
                                        : "Articoli da creare"),
                existing = metric(existingValue, "Già presenti · esclusi");
        existing.getStyleClass().add("bulk-metric-existing");
        invalidMetric = metric(invalidValue, "Da correggere");
        invalidMetric.getStyleClass().add("bulk-metric-invalid");
        visible(invalidMetric, false);
        totalValue.setId(id + "-count-total");
        newValue.setId(id + "-count-new");
        existingValue.setId(id + "-count-existing");
        invalidValue.setId(id + "-count-invalid");
        HBox metrics = new HBox(8, total, fresh, existing, invalidMetric);
        for (Node n : metrics.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        notice.getStyleClass().add("bulk-notice");
        notice.setWrapText(true);
        notice.setMinHeight(Region.USE_PREF_SIZE);
        notice.setId(id + "-notice");
        table.setId(id + "-preview");
        table.setMinWidth(0);
        table.setPrefHeight(180);
        table.setFixedCellSize(43);
        table.setMinHeight(Region.USE_PREF_SIZE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(hint("Seleziona i dati: l'anteprima si aggiorna automaticamente."));
        buildColumns();
        save = new Button();
        save.setId(id + "-create");
        save.setMinWidth(Region.USE_PREF_SIZE);
        save.getStyleClass().add("bulk-primary");
        save.setDisable(true);
        Label saveHint =
                hint(
                        kind == CatalogKind.PRODUCTS
                                ? "Saranno creati solo i nuovi prodotti selezionati; ricette,"
                                        + " modelli disco e approvazioni si gestiscono"
                                        + " successivamente."
                                : "Ogni articolo resta collegato al proprio prodotto; ricette,"
                                        + " modelli disco e approvazioni si gestiscono"
                                        + " successivamente.");
        saveHint.setMinWidth(0);
        saveHint.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(saveHint, Priority.ALWAYS);
        HBox footer = new HBox(12, saveHint, save);
        footer.setAlignment(Pos.CENTER_LEFT);
        feedback.setId(id + "-feedback");
        feedback.setWrapText(true);
        feedback.setMinHeight(Region.USE_PREF_SIZE);
        feedback.getStyleClass().add("bulk-feedback");
        visible(feedback, false);
        openList.setId(id + "-open-list");
        openList.setText("Apri elenco " + plural() + " →");
        openList.getStyleClass().add("bulk-link-button");
        visible(openList, false);
        right.getChildren().addAll(metrics, notice, table, footer, feedback, openList);
        parents.onSelection(this::selectionChanged);
        diameters.textProperty().addListener((o, a, b) -> settingsChanged());
        heights.textProperty().addListener((o, a, b) -> settingsChanged());
        rule.addListener((o, a, b) -> settingsChanged());
        notes.textProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!updating) changed.run();
                        });
        widthProperty().addListener((o, a, b) -> arrange());
        arrange();
        updateCounts();
        markClean();
    }

    private void arrange() {
        boolean next = getWidth() >= 1040;
        if (!getChildren().isEmpty() && next == wide) return;
        wide = next;
        getChildren().clear();
        getColumnConstraints().clear();
        if (wide) {
            ColumnConstraints l = new ColumnConstraints(330);
            l.setHgrow(Priority.NEVER);
            ColumnConstraints r = new ColumnConstraints();
            r.setHgrow(Priority.ALWAYS);
            getColumnConstraints().addAll(l, r);
            add(left, 0, 0);
            add(right, 1, 0);
        } else {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100);
            c.setHgrow(Priority.ALWAYS);
            getColumnConstraints().add(c);
            add(left, 0, 0);
            add(right, 0, 1);
        }
        GridPane.setValignment(left, VPos.TOP);
        GridPane.setValignment(right, VPos.TOP);
        GridPane.setFillHeight(left, false);
        GridPane.setFillHeight(right, false);
        GridPane.setHgrow(right, Priority.ALWAYS);
    }

    private void addColorChoice(String raw) {
        String color = commercialColor(raw);
        if (color == null || colorChecks.containsKey(color)) return;
        CheckBox check = new CheckBox(colorLabelOf(color));
        check.getStyleClass().add("bulk-color-choice");
        check.setId(id + "-color-" + color.replace('.', '_'));
        check.setOnAction(
                e -> {
                    if (check.isSelected()) colors.add(color);
                    else colors.remove(color);
                    selectionChanged();
                });
        colorChecks.put(color, check);
        palette.getChildren().add(check);
    }

    private void addExtraColor() {
        try {
            String color = commercialColor(extraColor.getText());
            if (color == null) return;
            addColorChoice(color);
            colors.add(color);
            colorChecks.get(color).setSelected(true);
            extraColor.clear();
            selectionChanged();
        } catch (IllegalArgumentException invalid) {
            feedback(invalid.getMessage(), true);
        }
    }

    private void selectionChanged() {
        if (!updating) inputs.run();
    }

    private void settingsChanged() {
        if (!updating) inputs.run();
    }

    public void onInputs(Runnable action) {
        inputs = action;
    }

    public void onRows(Runnable action) {
        changed = action;
    }

    private String plural() {
        return kind == CatalogKind.PRODUCTS ? "prodotti" : "articoli";
    }

    public CatalogKind kind() {
        return kind;
    }

    public BulkParentPicker parents() {
        return parents;
    }

    public BulkParentPicker lineFilter() {
        return lineFilter;
    }

    public Button saveButton() {
        return save;
    }

    public Button openListButton() {
        return openList;
    }

    public TableView<Row> table() {
        return table;
    }

    public boolean hasRows() {
        return !table.getItems().isEmpty();
    }

    public Preview preview() {
        return preview;
    }

    private Values sample() {
        Parent p = parents.value();
        BigDecimal d = null, h = null;
        try {
            d = measures(diameters.getText(), "Diametro").getFirst();
            h = measures(heights.getText(), "Altezza").getFirst();
        } catch (RuntimeException ignored) {
        }
        return new Values(
                p == null ? "" : p.material(),
                p == null ? "" : p.lineCode(),
                kind == CatalogKind.PRODUCTS
                        ? (colors.isEmpty() ? "" : colors.iterator().next())
                        : (p == null ? "" : p.color()),
                d,
                h);
    }

    public boolean hasGenerationSelection() {
        return !parents.selected().isEmpty() && (kind == CatalogKind.ARTICLES || !colors.isEmpty());
    }

    public void generate() {
        List<Parent> selected = parents.selected();
        if (!hasGenerationSelection()) {
            table.getItems().clear();
            table.setPrefHeight(180);
            generatedInputs = inputFingerprint();
            preview = null;
            updateCounts();
            visible(openList, false);
            feedback("", false);
            return;
        }
        var rows = new ArrayList<Row>();
        if (kind == CatalogKind.PRODUCTS) {
            Parent p = selected.getFirst();
            for (String color : colors) {
                String code =
                        rule.get().code(new Values(p.material(), p.lineCode(), color, null, null));
                rows.add(new Row(rows.size(), p, code, color, "", ""));
            }
        } else {
            List<BigDecimal> ds = measures(diameters.getText(), "Diametro"),
                    hs = measures(heights.getText(), "Altezza");
            if ((long) selected.size() * ds.size() * hs.size() > MAX_ROWS)
                throw invalid(
                        "Genera al massimo "
                                + MAX_ROWS
                                + " righe per gruppo. Riduci prodotti o misure.");
            for (Parent p : selected)
                for (BigDecimal d : ds)
                    for (BigDecimal h : hs) {
                        String code =
                                rule.get()
                                        .code(
                                                new Values(
                                                        p.material(),
                                                        p.lineCode(),
                                                        p.color(),
                                                        d,
                                                        h));
                        rows.add(new Row(rows.size(), p, code, "", display(d), display(h)));
                    }
        }
        var previous = new HashMap<Combination, Row>();
        table.getItems().forEach(row -> previous.put(row.combination, row));
        for (Row row : rows) {
            Row old = previous.get(row.combination);
            if (old != null) row.preserveEdits(old);
            InvalidationListener listener =
                    o -> {
                        if (!updating) changed.run();
                    };
            row.code.addListener(listener);
            row.diameter.addListener(listener);
            row.height.addListener(listener);
            row.selected.addListener(listener);
        }
        table.getItems().setAll(rows);
        table.setPrefHeight(Math.max(180, Math.min(32 + rows.size() * 43, 850)));
        generatedInputs = inputFingerprint();
        preview = null;
        updateCounts();
        visible(openList, false);
        feedback("", false);
    }

    public Request request() {
        return new Request(
                kind, notes.getText(), table.getItems().stream().map(Row::seed).toList());
    }

    public boolean settingsDirty() {
        return hasRows() && !generatedInputs.equals(inputFingerprint());
    }

    public void pending() {
        preview = null;
        newValue.setText("—");
        save.setDisable(true);
        notice.setText(
                settingsDirty()
                        ? "Aggiornamento automatico dell'anteprima…"
                        : "Verifica delle righe in corso…");
        visible(notice, true);
    }

    public void apply(Preview value) {
        updating = true;
        try {
            preview = value;
            var results = new HashMap<Integer, RowResult>();
            value.rows().forEach(r -> results.put(r.key(), r));
            for (Row row : table.getItems()) {
                RowResult r = results.get(row.key);
                if (r != null) {
                    row.state.set(r.state());
                    row.message.set(r.message());
                }
            }
            updateCounts();
            if (feedback.getStyleClass().contains("bulk-error")) feedback("", false);
        } finally {
            updating = false;
        }
    }

    private void updateCounts() {
        totalValue.setText(Integer.toString(table.getItems().size()));
        long creating = preview == null ? 0 : preview.creating(),
                existing = preview == null ? 0 : preview.existing(),
                invalid = preview == null ? 0 : preview.invalid();
        newValue.setText(Long.toString(creating));
        existingValue.setText(Long.toString(existing));
        invalidValue.setText(Long.toString(invalid));
        visible(invalidMetric, invalid > 0);
        save.setText("Crea " + creating + " " + plural());
        save.setDisable(preview == null || !preview.savable() || settingsDirty());
        String text =
                invalid > 0
                        ? "Correggi o escludi le righe segnalate. "
                                + preview.rows().stream()
                                        .filter(r -> r.state() == State.INVALID)
                                        .findFirst()
                                        .map(RowResult::message)
                                        .orElse("")
                        : existing > 0
                                ? existing
                                        + " elementi già presenti: verranno saltati. I dati"
                                        + " esistenti restano invariati."
                                : "";
        notice.setText(text);
        visible(notice, !text.isBlank());
    }

    public void failed(String text) {
        pending();
        notice.setText(
                "Verifica non completata. Correggi i dati o aggiorna l’anteprima prima di"
                        + " salvare.");
        feedback(text, true);
    }

    public void feedback(String text, boolean error) {
        feedback.setText(text);
        feedback.getStyleClass().remove("bulk-error");
        if (error) feedback.getStyleClass().add("bulk-error");
        visible(feedback, !text.isBlank());
    }

    public void saved(Created result) {
        feedback(
                "Creati "
                        + result.entries().size()
                        + " "
                        + plural()
                        + ". "
                        + result.skipped()
                        + " già presenti esclusi.",
                false);
        visible(openList, true);
        markClean();
    }

    public void markClean() {
        baseline = fingerprint();
    }

    public boolean dirty() {
        return !baseline.equals(fingerprint());
    }

    private List<String> fingerprint() {
        var f = new ArrayList<>(inputFingerprint());
        f.add(notes.getText());
        f.addAll(rowFingerprint());
        return List.copyOf(f);
    }

    private List<String> rowFingerprint() {
        return table.getItems().stream().map(Row::fingerprint).toList();
    }

    private List<String> inputFingerprint() {
        var f = new ArrayList<String>();
        parents.selected()
                .forEach(p -> f.add(p.id() + "@" + p.revision() + "/" + p.lineRevision()));
        f.addAll(colors);
        f.add(diameters.getText());
        f.add(heights.getText());
        f.add(rule.get().toString());
        return List.copyOf(f);
    }

    public static List<BigDecimal> measures(String raw, String label) {
        var result = new LinkedHashSet<BigDecimal>();
        for (String value : raw.split(";", -1)) {
            String text = value.strip();
            if (text.isEmpty()) continue;
            if (!text.matches("[0-9]+([.,][0-9]+)?"))
                throw invalid(
                        label + ": usa misure separate da “;”, con virgola o punto decimale.");
            BigDecimal n = new BigDecimal(text.replace(',', '.')).stripTrailingZeros();
            new ArticleDraft("CHECK", "", 1, n, n, "", true);
            result.add(n);
        }
        if (result.isEmpty())
            throw invalid(
                    "Inserisci almeno una misura per " + label.toLowerCase(Locale.ITALIAN) + ".");
        return List.copyOf(result);
    }

    private void buildColumns() {
        TableColumn<Row, Row> selected = new TableColumn<>("");
        selected.setPrefWidth(35);
        selected.setMinWidth(35);
        selected.setMaxWidth(40);
        selected.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        selected.setCellFactory(c -> new SelectionCell());
        var columns = new ArrayList<TableColumn<Row, ?>>();
        columns.add(selected);
        if (kind == CatalogKind.PRODUCTS) {
            TableColumn<Row, String> color = new TableColumn<>("Colore");
            color.setCellValueFactory(
                    c -> new ReadOnlyStringWrapper(colorLabelOf(c.getValue().color.get())));
            color.setPrefWidth(75);
            columns.add(color);
        }
        columns.add(
                textColumn(
                        kind == CatalogKind.PRODUCTS ? "Codice prodotto" : "Codice articolo",
                        "code",
                        kind == CatalogKind.ARTICLES,
                        170));
        if (kind == CatalogKind.ARTICLES) {
            TableColumn<Row, String> parent = new TableColumn<>("Prodotto collegato");
            parent.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().parent.code()));
            parent.setPrefWidth(125);
            columns.add(parent);
            columns.add(textColumn("Ø mm", "diameter", true, 75));
            columns.add(textColumn("Altezza mm", "height", true, 85));
        } else {
            TableColumn<Row, String> name = new TableColumn<>("Descrizione");
            name.setCellValueFactory(
                    c ->
                            new ReadOnlyStringWrapper(
                                    productDescription(
                                            c.getValue().parent.name(), c.getValue().color.get())));
            name.setPrefWidth(155);
            columns.add(name);
        }
        TableColumn<Row, Row> state = new TableColumn<>("Esito");
        state.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        state.setPrefWidth(135);
        state.setMinWidth(110);
        state.setCellFactory(c -> new StatusCell());
        columns.add(state);
        table.getColumns().setAll(columns);
    }

    private TableColumn<Row, String> textColumn(
            String title, String property, boolean editable, double width) {
        TableColumn<Row, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setMinWidth(property.equals("code") ? 120 : 60);
        c.setCellValueFactory(v -> property(v.getValue(), property));
        if (editable) c.setCellFactory(ignored -> new TextCell(property));
        return c;
    }

    private static StringProperty property(Row row, String property) {
        return switch (property) {
            case "code" -> row.code;
            case "diameter" -> row.diameter;
            case "height" -> row.height;
            default -> throw new IllegalArgumentException();
        };
    }

    private final class TextCell extends TableCell<Row, String> {
        private final TextField field = new TextField();
        private final String property;
        private Row bound;

        TextCell(String property) {
            this.property = property;
            field.getStyleClass().add("bulk-inline-field");
            field.setMinWidth(0);
        }

        @Override
        protected void updateItem(String text, boolean empty) {
            super.updateItem(text, empty);
            Row row = empty || getTableRow() == null ? null : getTableRow().getItem();
            if (row != bound) {
                if (bound != null)
                    field.textProperty().unbindBidirectional(property(bound, property));
                bound = row;
                if (bound != null) {
                    field.setText(property(bound, property).get());
                    field.textProperty().bindBidirectional(property(bound, property));
                }
            }
            if (bound != null) {
                field.setId(id + "-" + property + "-" + bound.key);
                field.setAccessibleText(property + " riga " + (bound.key + 1));
            }
            setText(null);
            setGraphic(bound == null ? null : field);
        }
    }

    private final class SelectionCell extends TableCell<Row, Row> {
        private final CheckBox check = new CheckBox();
        private Row bound;
        private final InvalidationListener sync = o -> sync();

        SelectionCell() {
            setAlignment(Pos.CENTER);
            check.setOnAction(
                    e -> {
                        if (bound != null) bound.selected.set(check.isSelected());
                    });
        }

        private void sync() {
            if (bound != null) {
                check.setSelected(bound.selected.get() && bound.state.get() != State.EXISTING);
                check.setDisable(bound.state.get() == State.EXISTING);
            }
        }

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            if (bound != null) {
                bound.selected.removeListener(sync);
                bound.state.removeListener(sync);
            }
            bound = empty ? null : row;
            if (bound != null) {
                bound.selected.addListener(sync);
                bound.state.addListener(sync);
                check.setId(id + "-include-" + bound.key);
                check.setAccessibleText("Includi riga " + (bound.key + 1));
                sync();
            }
            setGraphic(bound == null ? null : check);
        }
    }

    private final class StatusCell extends TableCell<Row, Row> {
        private final Label label = new Label();
        private Row bound;
        private final InvalidationListener sync = o -> sync();

        private void sync() {
            if (bound == null) return;
            String state =
                    switch (bound.state.get()) {
                        case NEW -> "Nuovo";
                        case EXISTING -> "Già presente";
                        case INVALID -> "Da correggere";
                        case EXCLUDED ->
                                bound.message.get().equals("Da verificare")
                                        ? "Da verificare"
                                        : "Escluso";
                    };
            label.setText(state);
            label.getStyleClass()
                    .setAll(
                            "bulk-status",
                            "bulk-status-" + bound.state.get().name().toLowerCase(Locale.ROOT));
            label.setTooltip(new Tooltip(bound.message.get()));
        }

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            if (bound != null) {
                bound.state.removeListener(sync);
                bound.message.removeListener(sync);
            }
            bound = empty ? null : row;
            if (bound != null) {
                bound.state.addListener(sync);
                bound.message.addListener(sync);
                sync();
            }
            setGraphic(bound == null ? null : label);
        }
    }

    private static String display(BigDecimal n) {
        return n.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static Label label(String text, String css) {
        Label l = new Label(text);
        l.getStyleClass().add(css);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    private static Label hint(String text) {
        Label l = label(text, "bulk-small");
        l.setWrapText(true);
        return l;
    }

    private static VBox field(String title, Node input) {
        return new VBox(5, label(title, "bulk-field-label"), input);
    }

    private static VBox metric(Label value, String title) {
        value.getStyleClass().add("bulk-metric-value");
        VBox box = new VBox(3, value, hint(title));
        box.getStyleClass().add("bulk-metric");
        box.setMinWidth(0);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static Button link(String title, String id) {
        Button button = new Button(title);
        button.setId(id);
        button.getStyleClass().add("bulk-link-button");
        return button;
    }

    private static void visible(Node node, boolean show) {
        node.setVisible(show);
        node.setManaged(show);
    }
}

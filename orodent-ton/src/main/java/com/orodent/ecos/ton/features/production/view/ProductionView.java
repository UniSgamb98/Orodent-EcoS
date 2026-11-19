package com.orodent.ecos.ton.features.production.view;

import static com.orodent.ecos.ton.features.production.model.Production.*;
import static com.orodent.ecos.ton.features.production.service.ProductionDocumentData.number;

import com.orodent.ecos.ton.features.technical.view.*;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.css.PseudoClass;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Production has its own two-column workspace, independent of technical detail editors. */
public final class ProductionView extends BorderPane {
    public static final class QuantityRow {
        public final ArticleChoice article;
        public final IntegerProperty quantity = new SimpleIntegerProperty();

        QuantityRow(ArticleChoice article, int quantity) {
            this.article = article;
            this.quantity.set(quantity);
        }
    }

    public final TonListView<Summary> listing =
            new TonListView<>(
                    "ton-productions",
                    "Prepara i dischi da produrre e riprendi le bozze di lavorazione.",
                    "Cerca numero, prodotto o nota",
                    "Nuova produzione");
    public final ComboBox<State> listState = new ComboBox<>();
    public final Button back = button("← Torna alle produzioni", "back", false),
            refresh = button("Aggiorna dati", "refresh", false),
            clear = button("Azzera quantità", "clear", false),
            save = button("Salva bozza", "save", false),
            prepare = button("Prepara produzione", "prepare", true),
            duplicate = button("Duplica", "duplicate", false),
            print = button("Stampa foglio operativo", "print", true),
            previewPrint = button("Stampa anteprima", "preview-print", false);
    public final ComboBox<LineChoice> line = new ComboBox<>();
    public final ComboBox<BigDecimal> diameter = new ComboBox<>();
    public final ComboBox<TemplateChoice> template = new ComboBox<>();
    public final DatePicker date = new DatePicker(LocalDate.now());
    public final TextArea notes = new TextArea();
    public final TabPane tabs = new TabPane();
    public final TableView<QuantityRow> quantities = new TableView<>();
    public final ProductionDocumentPane document = new ProductionDocumentPane();
    public final BooleanProperty locked = new SimpleBooleanProperty();
    private final VBox editor = new VBox(16), configuration = new VBox(16), right = new VBox();
    private final GridPane workspace = new GridPane();
    private final FlowPane palette = new FlowPane(6, 6);
    private final VBox weighing = new VBox(16);
    private final Label state = label("Bozza · non salvata", "production-state"),
            productLabel = label("Scegli un prodotto/colore.", "muted-label"),
            specification = label("Seleziona prodotto e diametro.", "production-specification"),
            count = label("0", "production-metric-value"),
            formatCount = label("0", "production-metric-value"),
            prepared = label("0,0 g", "production-metric-value"),
            dose = label("0,0 g", "production-field-label"),
            extra = label("0,0 g", "production-field-label"),
            feedback = label("", "muted-label"),
            readiness = label("Seleziona una linea e un prodotto.", "muted-label"),
            documentName = label("Nessun modello selezionato", "muted-label");
    private final ReadOnlyStringWrapper subpage = new ReadOnlyStringWrapper("");
    private ProductChoice selectedProduct;
    private Catalogues catalogues = new Catalogues(List.of(), List.of(), List.of());
    private Consumer<ProductChoice> onProduct = p -> {};
    private java.util.function.LongConsumer onArticle = id -> {};
    private Runnable onInput = () -> {};
    private boolean populating;
    private Plan displayedPlan;

    public ProductionView() {
        setId("ton-production-view");
        setMinSize(0, 0);
        getStyleClass().add("ton-production");
        getStylesheets().add(getClass().getResource("/ton/css/production.css").toExternalForm());
        listState.setId("ton-productions-state");
        listState.getItems().add(null);
        listState.getItems().addAll(State.values());
        listState.setPromptText("Tutti gli stati");
        listState.setConverter(
                new javafx.util.StringConverter<>() {
                    public String toString(State s) {
                        return s == null ? "Tutti gli stati" : s.toString();
                    }

                    public State fromString(String s) {
                        return null;
                    }
                });
        listing.filters("ton-productions-filters", TechnicalUi.field("Stato", listState));
        var table = listing.table();
        TechnicalUi.column(table, "Produzione", 130, Summary::number);
        TechnicalUi.column(table, "Data preparazione", 140, s -> displayDate(s.date()));
        TechnicalUi.column(table, "Prodotto", 220, Summary::product);
        TechnicalUi.column(table, "Formati", 80, s -> "" + s.formats());
        TechnicalUi.column(table, "Dischi richiesti", 100, s -> "" + s.count());
        TechnicalUi.column(table, "Stato", 110, s -> s.state().toString());
        TechnicalUi.column(table, "Foglio operativo", 190, Summary::document);
        line.setId("ton-production-line");
        diameter.setId("ton-production-diameter");
        template.setId("ton-production-template");
        date.setId("ton-production-date");
        date.setConverter(
                new javafx.util.StringConverter<>() {
                    private final DateTimeFormatter format =
                            DateTimeFormatter.ofPattern("dd/MM/yyyy");

                    public String toString(LocalDate value) {
                        return value == null ? "" : format.format(value);
                    }

                    public LocalDate fromString(String value) {
                        return value == null || value.isBlank()
                                ? null
                                : LocalDate.parse(value, format);
                    }
                });
        notes.setId("ton-production-notes");
        notes.setWrapText(true);
        notes.setPrefRowCount(3);
        notes.setPromptText("Indicazioni per il foglio operativo");
        notes.setTextFormatter(
                new TextFormatter<String>(c -> c.getControlNewText().length() <= 1000 ? c : null));
        template.setConverter(
                new javafx.util.StringConverter<>() {
                    public String toString(TemplateChoice t) {
                        return t == null ? "Nessun documento" : t.toString();
                    }

                    public TemplateChoice fromString(String s) {
                        return null;
                    }
                });
        template.setPromptText("Nessun documento");
        diameter.setConverter(
                new javafx.util.StringConverter<>() {
                    public String toString(BigDecimal d) {
                        return d == null ? "" : d.stripTrailingZeros().toPlainString();
                    }

                    public BigDecimal fromString(String s) {
                        return new BigDecimal(s);
                    }
                });
        for (ComboBox<?> box : List.of(line, diameter, template)) box.setMaxWidth(Double.MAX_VALUE);
        date.setMaxWidth(Double.MAX_VALUE);
        var colorField =
                new VBox(
                        8,
                        label("Prodotto · colore commerciale", "production-field-label"),
                        palette,
                        productLabel);
        configuration.getStyleClass().add("production-configuration");
        configuration.setMinWidth(0);
        configuration
                .getChildren()
                .setAll(
                        field("Data di preparazione", date),
                        field("Linea", line),
                        colorField,
                        field("Diametro nominale · mm", diameter),
                        specification,
                        new Separator(),
                        field("Foglio operativo", template),
                        field("Note di lavorazione · facoltative", notes),
                        label(
                                "Una produzione raccoglie un prodotto/colore in più formati.",
                                "muted-label"));
        ScrollPane left = new ScrollPane(configuration);
        left.setFitToWidth(true);
        left.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        left.getStyleClass().add("production-left");
        left.setMinSize(0, 0);
        quantities.setRowFactory(
                t ->
                        new TableRow<>() {
                            private final javafx.beans.value.ChangeListener<Number> inclusion =
                                    (o, a, b) ->
                                            pseudoClassStateChanged(
                                                    PseudoClass.getPseudoClass("included"),
                                                    b.intValue() > 0);

                            {
                                itemProperty()
                                        .addListener(
                                                (o, a, b) -> {
                                                    if (a != null)
                                                        a.quantity.removeListener(inclusion);
                                                    if (b != null)
                                                        b.quantity.addListener(inclusion);
                                                    pseudoClassStateChanged(
                                                            PseudoClass.getPseudoClass("included"),
                                                            b != null && b.quantity.get() > 0);
                                                });
                            }
                        });
        quantities.setId("ton-production-quantities");
        quantities.setMinSize(0, 150);
        quantities.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        quantities.setPlaceholder(
                label("Seleziona una linea, un prodotto e il diametro.", "muted-label"));
        var code = new TableColumn<QuantityRow, QuantityRow>("Articolo");
        code.setPrefWidth(210);
        code.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        code.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(QuantityRow row, boolean empty) {
                                super.updateItem(row, empty);
                                setText(null);
                                if (empty || row == null) {
                                    setGraphic(null);
                                    return;
                                }
                                var content =
                                        new VBox(
                                                3,
                                                label(row.article.code(), "production-field-label"),
                                                label(
                                                        row.article.available()
                                                                ? "Specifica v"
                                                                        + row.article
                                                                                .specificationVersion()
                                                                        + " · approvata"
                                                                : row.article.reason(),
                                                        "production-row-hint"));
                                if (!row.article.available()) {
                                    var link = new Hyperlink("Apri articolo ↗");
                                    link.setOnAction(e -> onArticle.accept(row.article.id()));
                                    content.getChildren().add(link);
                                }
                                setGraphic(content);
                            }
                        });
        quantities.getColumns().add(code);
        rowColumn("Ø · mm", 85, r -> plain(r.article.diameter()));
        rowColumn("H nominale · mm", 110, r -> plain(r.article.height()));
        var qty = new TableColumn<QuantityRow, QuantityRow>("Da produrre · pz");
        qty.setPrefWidth(125);
        qty.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue()));
        qty.setCellFactory(c -> new QuantityCell());
        quantities.getColumns().add(qty);
        rowColumn(
                "Peso per disco · g",
                150,
                r -> r.article.doses().isEmpty() ? "—" : number(r.article.weight()));
        var total = new TableColumn<QuantityRow, String>("Totale da dosare · g");
        total.setPrefWidth(175);
        total.setCellValueFactory(
                v ->
                        Bindings.createStringBinding(
                                () ->
                                        v.getValue().quantity.get() == 0
                                                ? "—"
                                                : number(
                                                        v.getValue()
                                                                .article
                                                                .weight()
                                                                .multiply(
                                                                        BigDecimal.valueOf(
                                                                                v.getValue()
                                                                                        .quantity
                                                                                        .get()))),
                                v.getValue().quantity));
        quantities.getColumns().add(total);
        VBox.setVgrow(quantities, Priority.ALWAYS);
        var metrics =
                new HBox(
                        8,
                        metric(count, "Dischi da produrre", false),
                        metric(formatCount, "Formati selezionati", false),
                        metric(prepared, "Miscela da preparare · incluso +0,5%", true));
        metrics.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));
        var quantitiesHeading =
                new HBox(
                        12,
                        label("Inserisci i pezzi per formato", "production-section-title"),
                        spacer(),
                        clear);
        quantitiesHeading.setAlignment(Pos.CENTER_LEFT);
        clear.getStyleClass().add("production-link");
        var totals =
                new HBox(
                        18,
                        new VBox(5, label("Da dosare nei dischi", "muted-label"), dose),
                        new VBox(
                                5,
                                label("Maggiorazione di preparazione · 0,5%", "muted-label"),
                                extra));
        totals.getStyleClass().add("production-band");
        var quantityHint =
                label(
                        "Le quantità a zero non entrano nella produzione. La maggiorazione si"
                            + " applica solo alle miscele da preparare.",
                        "muted-label");
        VBox quantityContent =
                new VBox(16, metrics, quantitiesHeading, quantities, quantityHint, totals);
        heightProperty()
                .addListener(
                        (o, a, b) -> {
                            boolean compact = b.doubleValue() < 820;
                            pseudoClassStateChanged(PseudoClass.getPseudoClass("compact"), compact);
                            quantityContent.setSpacing(compact ? 10 : 16);
                            quantities.setMinHeight(compact ? 105 : 150);
                            visible(quantityHint, !compact);
                        });
        quantityContent.setMinSize(0, 0);
        quantityContent.getStyleClass().add("production-content");
        ScrollPane weighingScroll = new ScrollPane(weighing);
        weighingScroll.setFitToWidth(true);
        weighingScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        weighing.getStyleClass().add("production-content");
        var docToolbar = new HBox(12, documentName, spacer(), previewPrint);
        docToolbar.setAlignment(Pos.CENTER_LEFT);
        docToolbar.getStyleClass().add("production-document-toolbar");
        BorderPane docContent = new BorderPane(document);
        docContent.setTop(docToolbar);
        tabs.getSelectionModel()
                .selectedIndexProperty()
                .addListener(
                        (o, a, b) -> {
                            if (b.intValue() == 1 && displayedPlan != null) weighing(displayedPlan);
                        });
        tabs.setId("ton-production-tabs");
        tabs.getTabs()
                .setAll(
                        new Tab("Articoli e quantità", quantityContent),
                        new Tab("Pesate e miscele", weighingScroll),
                        new Tab("Documento", docContent));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setMinSize(0, 230);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        readiness.setMinWidth(0);
        HBox.setHgrow(readiness, Priority.ALWAYS);
        var actionbar = new HBox(10, readiness, save, prepare, duplicate, print);
        actionbar.setAlignment(Pos.CENTER_LEFT);
        actionbar.getStyleClass().add("production-actions");
        right.getStyleClass().add("production-right");
        right.setMinSize(0, 0);
        right.getChildren().setAll(tabs, actionbar, feedback);
        feedback.getStyleClass().add("production-feedback");
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        workspace.setHgap(18);
        workspace.setMinSize(0, 0);
        workspace.add(left, 0, 0);
        workspace.add(right, 1, 0);
        GridPane.setHgrow(right, Priority.ALWAYS);
        GridPane.setVgrow(right, Priority.ALWAYS);
        GridPane.setVgrow(left, Priority.ALWAYS);
        var leftColumn = new ColumnConstraints();
        leftColumn.setPrefWidth(340);
        leftColumn.setMinWidth(270);
        leftColumn.setMaxWidth(360);
        var rightColumn = new ColumnConstraints();
        rightColumn.setMinWidth(0);
        rightColumn.setHgrow(Priority.ALWAYS);
        workspace.getColumnConstraints().setAll(leftColumn, rightColumn);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        widthProperty()
                .addListener(
                        (o, a, b) -> leftColumn.setPrefWidth(b.doubleValue() < 1150 ? 295 : 340));
        back.getStyleClass().add("production-link");
        refresh.getStyleClass().add("production-refresh");
        var toolbar = new HBox(12, back, state, spacer(), refresh);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        editor.setMinSize(0, 0);
        editor.getStyleClass().add("production-editor");
        editor.getChildren().setAll(toolbar, workspace);
        for (Node n : List.of(line, diameter, template, date, notes, palette))
            n.disableProperty().bind(locked);
        showList();
    }

    public void onArticle(java.util.function.LongConsumer action) {
        onArticle = action;
    }

    public void callbacks(Consumer<ProductChoice> product, Runnable input) {
        onProduct = product;
        onInput = input;
    }

    public void setCatalogues(Catalogues value) {
        catalogues = value;
        line.getItems().setAll(value.lines());
        template.getItems().clear();
        template.getItems().add(null);
        template.getItems().addAll(value.templates());
    }

    public void showColors(LineChoice selected, ProductChoice product) {
        selectedProduct = product;
        palette.getChildren().clear();
        if (selected == null) return;
        var products =
                new ArrayList<>(
                        catalogues.products().stream()
                                .filter(p -> p.lineId() == selected.id())
                                .toList());
        if (product != null && products.stream().noneMatch(p -> p.id() == product.id()))
            products.add(product);
        for (var p : products) {
            var b = new ToggleButton("SENZA_COLORE".equals(p.color()) ? "Senza colore" : p.color());
            b.setId("ton-production-color-" + p.id());
            b.setMinWidth(48);
            b.setSelected(product != null && p.id() == product.id());
            b.getStyleClass().add("production-color");
            b.setTooltip(new Tooltip(p.toString()));
            b.setOnAction(
                    e -> {
                        if (selectedProduct != null && selectedProduct.id() == p.id())
                            b.setSelected(true);
                        else onProduct.accept(p);
                    });
            palette.getChildren().add(b);
        }
        productLabel.setText(product == null ? "Scegli un prodotto/colore." : product.toString());
    }

    public ProductChoice product() {
        return selectedProduct;
    }

    public void rows(Workspace value, Map<Long, Integer> values) {
        populating = true;
        try {
            quantities
                    .getItems()
                    .setAll(
                            value.articles().stream()
                                    .map(a -> new QuantityRow(a, values.getOrDefault(a.id(), 0)))
                                    .toList());
            for (var row : quantities.getItems())
                row.quantity.addListener(
                        (o, a, b) -> {
                            if (!populating) onInput.run();
                        });
        } finally {
            populating = false;
        }
    }

    public Map<Long, Integer> quantities() {
        var result = new TreeMap<Long, Integer>();
        for (var row : quantities.getItems()) result.put(row.article.id(), row.quantity.get());
        return result;
    }

    public void clearRows() {
        quantities.getItems().clear();
    }

    public void zero() {
        populating = true;
        try {
            quantities.getItems().forEach(r -> r.quantity.set(0));
        } finally {
            populating = false;
        }
        quantities.refresh();
        onInput.run();
    }

    public void plan(Plan p) {
        displayedPlan = p;
        if (p == null) {
            count.setText("0");
            formatCount.setText("0");
            prepared.setText("0,0 g");
            dose.setText("0,0 g");
            extra.setText("0,0 g");
            readiness.setText("Seleziona una linea, un prodotto e le quantità.");
            documentName.setText("Nessun modello selezionato");
            specification.setText("Seleziona prodotto e diametro.");
            weighing.getChildren()
                    .setAll(
                            label(
                                    "Inserisci le quantità per vedere pesate e miscele.",
                                    "muted-label"));
            return;
        }
        count.setText("" + p.count());
        formatCount.setText("" + p.formats().size());
        prepared.setText(number(p.prepared()) + " g");
        dose.setText(number(p.needed()) + " g");
        extra.setText("+" + number(p.prepared().subtract(p.needed())) + " g");
        var a = p.reference();
        specification.setText(
                a == null
                        ? "Seleziona i formati da produrre."
                        : "Specifiche utilizzate\nComposizione "
                                + p.product().code()
                                + " · v"
                                + a.compositionVersion()
                                + "\nModello "
                                + a.modelCode()
                                + " · v"
                                + a.modelVersion()
                                + "\n"
                                + a.doses().size()
                                + " strati · versioni approvate");
        documentName.setText(
                p.template() == null ? "Nessun modello selezionato" : p.template().toString());
        readiness.setText(
                p.count() == 0
                        ? "Inserisci almeno una quantità."
                        : p.formats().size() + " formati · " + p.count() + " dischi da preparare");
        if (tabs.getSelectionModel().getSelectedIndex() == 1) weighing(p);
    }

    private void weighing(Plan p) {
        weighing.getChildren().clear();
        if (p.count() == 0) {
            weighing.getChildren()
                    .add(
                            label(
                                    "Inserisci i pezzi nella scheda Articoli e quantità.",
                                    "muted-label"));
            return;
        }
        var reference = p.reference();
        weighing.getChildren()
                .addAll(
                        label("Quanto pesare per ogni disco", "production-section-title"),
                        label(
                                "Ogni valore è la dose di un singolo strato, per il formato"
                                    + " richiesto.",
                                "muted-label"));
        var parameters =
                label(
                        "Composizione "
                                + p.product().code()
                                + " · v"
                                + reference.compositionVersion()
                                + "    Modello "
                                + reference.modelCode()
                                + " · v"
                                + reference.modelVersion()
                                + "\nPressione sul materiale: "
                                + number(reference.pressure())
                                + " kg/cm²    Diametro pressatura: "
                                + plain(reference.pressDiameter())
                                + " mm    Precisione: 0,1 g",
                        "production-band");
        weighing.getChildren().add(parameters);
        TableView<Format> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TechnicalUi.column(
                table,
                "Formato nominale",
                150,
                f ->
                        "Ø "
                                + plain(f.article().diameter())
                                + " × "
                                + plain(f.article().height())
                                + " mm");
        TechnicalUi.column(table, "Da pressare · mm", 150, f -> plain(f.article().thickness()));
        TechnicalUi.column(table, "Pezzi", 70, f -> "" + f.quantity());
        for (int i = 0; i < reference.doses().size(); i++) {
            final int n = i;
            TechnicalUi.column(
                    table,
                    "Strato " + (i + 1) + " · g",
                    110,
                    f -> number(f.article().doses().get(n).grams()));
        }
        table.getItems().setAll(p.formats());
        table.setMinHeight(130);
        table.setPrefHeight(38 + p.formats().size() * 38);
        weighing.getChildren().add(table);
        weighing.getChildren()
                .add(
                        label(
                                "I grammi per disco non includono la maggiorazione. I totali delle"
                                    + " miscele derivano dai valori analitici prima degli"
                                    + " arrotondamenti.",
                                "muted-label"));
        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        List<VBox> cards = p.mixtures().stream().map(m -> mixture(p, m)).toList();
        Runnable layout =
                () -> {
                    int columns = grid.getWidth() >= 850 ? 2 : 1;
                    if (grid.getColumnConstraints().size() == columns) return;
                    grid.getChildren().clear();
                    grid.getColumnConstraints().clear();
                    for (int i = 0; i < columns; i++) {
                        var c = new ColumnConstraints();
                        c.setPercentWidth(100.0 / columns);
                        grid.getColumnConstraints().add(c);
                    }
                    for (int i = 0; i < cards.size(); i++)
                        grid.add(cards.get(i), i % columns, i / columns);
                };
        grid.widthProperty().addListener((o, a, b) -> layout.run());
        layout.run();
        weighing.getChildren().add(grid);
    }

    private VBox mixture(Plan p, Mixture m) {
        var box = new VBox(10);
        box.setMinWidth(0);
        box.getStyleClass().add("production-mixture");
        box.getChildren()
                .addAll(
                        label(
                                "Strato " + m.number() + " · miscela da preparare",
                                "production-section-title"),
                        label(
                                m.count()
                                        + " pesate · "
                                        + p.product().code()
                                        + " · composizione v"
                                        + p.reference().compositionVersion(),
                                "muted-label"),
                        label(number(m.prepared()) + " g", "production-metric-value"));
        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(9);
        var col = new ColumnConstraints();
        col.setPercentWidth(60);
        var quota = new ColumnConstraints();
        quota.setPercentWidth(15);
        var mass = new ColumnConstraints();
        mass.setPercentWidth(25);
        grid.getColumnConstraints().setAll(col, quota, mass);
        grid.addRow(
                0,
                label("Polvere", "muted-label"),
                label("Quota", "muted-label"),
                label("Da pesare", "muted-label"));
        int n = 1;
        for (var w : m.powders())
            grid.addRow(
                    n++,
                    label(w.code(), "production-powder-code"),
                    label(
                            w.percentage().stripTrailingZeros().toPlainString() + "%",
                            "production-powder-code"),
                    label(number(w.grams()) + " g", "production-field-label"));
        box.getChildren()
                .addAll(
                        grid,
                        label(
                                "Da dosare: " + number(m.needed()) + " g · preparazione +0,5%",
                                "muted-label"));
        return box;
    }

    public void state(Saved saved) {
        boolean frozen = saved != null && saved.state() == State.PREPARATA;
        locked.set(frozen);
        state.setText(
                saved == null ? "Bozza · non salvata" : saved.state() + " · " + saved.number());
        state.pseudoClassStateChanged(PseudoClass.getPseudoClass("prepared"), frozen);
        visible(save, !frozen);
        visible(prepare, !frozen);
        visible(duplicate, frozen);
        visible(print, frozen);
        previewPrint.setText(frozen ? "Stampa foglio operativo" : "Stampa anteprima");
        subpage.set(saved == null ? "Nuova produzione" : saved.number());
    }

    public void showEditor() {
        setCenter(editor);
    }

    public void showList() {
        setCenter(listing);
        subpage.set("");
    }

    public void feedback(String message) {
        feedback(message, true);
    }

    public void feedback(String message, boolean error) {
        feedback.setText(message == null ? "" : message);
        feedback.getStyleClass().remove("ton-error-feedback");
        if (error) feedback.getStyleClass().add("ton-error-feedback");
    }

    public void ready(boolean input, boolean nonempty, boolean busy) {
        save.setDisable(!input || busy);
        prepare.setDisable(!input || !nonempty || busy);
        clear.setDisable(!nonempty || busy || locked.get());
        refresh.setDisable(busy);
        editor.setDisable(busy);
    }

    public void onInput(Runnable value) {
        onInput = value;
    }

    public ReadOnlyStringProperty subpageProperty() {
        return subpage.getReadOnlyProperty();
    }

    public void documentTab() {
        tabs.getSelectionModel().select(2);
    }

    public boolean documentSelected() {
        return tabs.getSelectionModel().getSelectedIndex() == 2;
    }

    public void dispose() {
        document.dispose();
        listing.dispose();
        quantities.getItems().clear();
        setCenter(null);
    }

    private void rowColumn(
            String title, double width, java.util.function.Function<QuantityRow, String> value) {
        TechnicalUi.column(quantities, title, width, value);
    }

    private final class QuantityCell extends TableCell<QuantityRow, QuantityRow> {
        private final TextField field = new TextField();
        private boolean changing;
        private QuantityRow observed;
        private final javafx.beans.value.ChangeListener<Number> sync =
                (o, a, b) -> {
                    String text = String.valueOf(b.intValue());
                    if (!field.getText().equals(text)) {
                        changing = true;
                        try {
                            field.setText(text);
                        } finally {
                            changing = false;
                        }
                    }
                };

        QuantityCell() {
            field.getStyleClass().add("production-quantity");
            field.setMaxWidth(85);
            field.setTextFormatter(
                    new TextFormatter<String>(
                            change ->
                                    change.getControlNewText().matches("[0-9]{0,9}")
                                            ? change
                                            : null));
            field.textProperty()
                    .addListener(
                            (o, a, b) -> {
                                if (!changing && getItem() != null)
                                    getItem().quantity.set(b.isBlank() ? 0 : Integer.parseInt(b));
                            });
        }

        @Override
        protected void updateItem(QuantityRow row, boolean empty) {
            super.updateItem(row, empty);
            if (observed != null) observed.quantity.removeListener(sync);
            observed = row;
            field.disableProperty().unbind();
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            changing = true;
            try {
                field.setText("" + row.quantity.get());
                field.setId("ton-production-qty-" + row.article.id());
                field.setAccessibleText("Quantità da produrre " + row.article.code());
            } finally {
                changing = false;
            }
            row.quantity.addListener(sync);
            field.disableProperty()
                    .bind(
                            locked.or(
                                    Bindings.createBooleanBinding(
                                            () ->
                                                    !row.article.available()
                                                            && row.quantity.get() == 0,
                                            row.quantity)));
            setGraphic(field);
        }
    }

    private static VBox metric(Label value, String text, boolean warm) {
        var b = new VBox(7, value, label(text, "muted-label"));
        b.setMinWidth(0);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add(warm ? "production-metric-warm" : "production-metric");
        return b;
    }

    private static VBox field(String text, Node value) {
        return new VBox(7, label(text, "production-field-label"), value);
    }

    private static Button button(String text, String id, boolean primary) {
        return TechnicalUi.button(text, "ton-production-" + id, primary);
    }

    private static Label label(String text, String style) {
        return TechnicalUi.label(text, style);
    }

    private static Region spacer() {
        var r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static void visible(Node n, boolean v) {
        n.setVisible(v);
        n.setManaged(v);
    }

    private static String plain(BigDecimal n) {
        return n == null ? "—" : n.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String displayDate(String date) {
        return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}

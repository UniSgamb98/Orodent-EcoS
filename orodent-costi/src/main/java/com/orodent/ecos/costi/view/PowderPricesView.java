package com.orodent.ecos.costi.view;

import com.orodent.ecos.costi.model.PowderPrices;
import com.orodent.ecos.costi.model.PowderPrices.*;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;

/** One screen and one editor: mode switching swaps only the editable detail body. */
public final class PowderPricesView extends CostsShell {
    public final Button refresh = new Button("Ricarica"),
            previous = new Button("←"),
            next = new Button("→");
    public final Button save = new Button("Salva"), cancel = new Button("Annulla");
    public final TextField search = new TextField();
    public final ComboBox<Filter> filter = new ComboBox<>();
    public final CheckBox inactive = new CheckBox("Includi archiviate");
    public final TableView<Row> table = new TableView<>();
    public final Button orders = new Button("Ordini e importazione CSV");
    private final VBox form = new VBox(14), simple = new VBox(14), advanced = new VBox(14);
    private final Label code = new Label("Seleziona una polvere"),
            name = new Label("Le anagrafiche arrivano dal catalogo TON."),
            status = new Label(),
            feedback = new Label(),
            count = new Label();
    private final Label totalQuantity = new Label("—"),
            totalValue = new Label("—"),
            averagePrice = new Label("—"),
            totalNote = new Label(),
            unassignedQuantity = new Label();
    private final ToggleButton simpleMode = new ToggleButton("Semplice"),
            advancedMode = new ToggleButton("Avanzata");
    private final ToggleGroup modes = new ToggleGroup();
    private final TextField simplePrice = new TextField(), simpleQuantity = new TextField();
    private final DatePicker date = new DatePicker();
    private final LotsEditor lots = new LotsEditor();
    private final HBox footer = new HBox(10);
    private Runnable changed = () -> {};
    private boolean showing;

    public record FormState(
            String dateText, String price, String quantity, List<LotsEditor.Input> lots) {}

    public PowderPricesView() {
        super("Polveri e prezzi");
        setId("costi-module");
        Label title = label("Polveri e prezzi", "costs-title");
        Label subtitle =
                label("Prezzi e quantità di riferimento per i tuoi calcoli", "costs-muted");
        VBox pageHeading =
                new VBox(
                        5,
                        label("Costi e consumi › Polveri e prezzi", "costs-breadcrumb"),
                        title,
                        subtitle);
        orders.setId("costi-orders");
        orders.getStyleClass().add("secondary-button");
        VBox csvArea = new VBox(3, orders);
        csvArea.setAlignment(Pos.CENTER_RIGHT);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox top = new HBox(10, pageHeading, gap, csvArea);
        top.setAlignment(Pos.CENTER_LEFT);
        search.setId("costi-search");
        search.setPromptText("Cerca polvere per codice o nome");
        search.setMinWidth(100);
        HBox.setHgrow(search, Priority.ALWAYS);
        filter.getItems().setAll(Filter.values());
        filter.setValue(Filter.TUTTE);
        filter.setId("costi-filter");
        filter.setMinWidth(120);
        refresh.setId("costi-refresh");
        refresh.getStyleClass().add("secondary-button");
        HBox tools = new HBox(8, search, filter);
        tools.setAlignment(Pos.CENTER_LEFT);
        HBox options = new HBox(8, inactive, refresh);
        options.setAlignment(Pos.CENTER_LEFT);
        setupTable();
        previous.setId("costi-previous");
        next.setId("costi-next");
        previous.setAccessibleText("Pagina precedente");
        next.setAccessibleText("Pagina successiva");
        HBox paging = new HBox(9, previous, count, next);
        paging.setAlignment(Pos.CENTER_LEFT);
        count.setWrapText(true);
        VBox catalogue =
                new VBox(
                        12,
                        tools,
                        options,
                        table,
                        paging,
                        label(
                                "Anagrafiche da TON · Prezzi legati alla polvere, non alle"
                                        + " revisioni tecniche",
                                "costs-muted"));
        catalogue.getStyleClass().add("costs-card");
        catalogue.setMinWidth(310);
        VBox.setVgrow(table, Priority.ALWAYS);
        code.getStyleClass().add("costs-detail-title");
        code.setWrapText(true);
        name.setWrapText(true);
        name.getStyleClass().add("costs-muted");
        status.getStyleClass().add("costs-muted");
        status.setId("costi-status");
        date.setId("costi-date");
        date.setMaxWidth(Double.MAX_VALUE);
        date.setPromptText("gg/mm/aaaa");
        date.setConverter(
                new StringConverter<>() {
                    private final DateTimeFormatter format =
                            DateTimeFormatter.ofPattern("dd/MM/uuuu")
                                    .withResolverStyle(ResolverStyle.STRICT);

                    @Override
                    public String toString(LocalDate value) {
                        return value == null ? "" : format.format(value);
                    }

                    @Override
                    public LocalDate fromString(String text) {
                        return text == null || text.isBlank()
                                ? null
                                : LocalDate.parse(text.strip(), format);
                    }
                });
        Label dateLabel = new Label("Dati della polvere al");
        dateLabel.setLabelFor(date);
        date.setTooltip(
                new Tooltip(
                        "Data della fotografia della polvere selezionata; ogni polvere conserva il"
                                + " proprio riferimento."));
        simpleMode.setToggleGroup(modes);
        advancedMode.setToggleGroup(modes);
        simpleMode.setId("costi-mode-simple");
        advancedMode.setId("costi-mode-advanced");
        simpleMode.setMaxWidth(Double.MAX_VALUE);
        advancedMode.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(simpleMode, Priority.ALWAYS);
        HBox.setHgrow(advancedMode, Priority.ALWAYS);
        HBox switcher = new HBox(simpleMode, advancedMode);
        switcher.getStyleClass().add("costs-modes");
        simplePrice.setId("costi-simple-price");
        simpleQuantity.setId("costi-simple-quantity");
        simpleQuantity.setPromptText("Non indicata");
        simplePrice.setPromptText("€/kg");
        simple.getChildren()
                .addAll(
                        field("Prezzo di riferimento €/kg", simplePrice),
                        field("Quantità disponibile kg · facoltativa", simpleQuantity),
                        label(
                                "Il costo è calcolabile anche senza quantità. Senza quantità non si"
                                        + " stima la copertura.",
                                "costs-muted"));
        HBox totals =
                new HBox(
                        7,
                        summary("Quantità nei lotti", totalQuantity),
                        summary("Valore dei lotti", totalValue),
                        summary("Media dei soli lotti", averagePrice));
        averagePrice.setId("costi-average-price");
        totalQuantity.setId("costi-total-quantity");
        totalValue.setId("costi-total-value");
        unassignedQuantity.setId("costi-unassigned");
        advanced.getChildren()
                .addAll(
                        label("Quantità e prezzi distinti per lotto di acquisto", "costs-muted"),
                        lots,
                        totals,
                        summary("Quantità non assegnata ai lotti", unassignedQuantity),
                        totalNote);
        totalNote.setWrapText(true);
        totalNote.getStyleClass().add("costs-muted");
        form.getChildren().addAll(new HBox(10, dateLabel, date), switcher, simple, advanced);
        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("costs-detail-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        save.setId("costi-save");
        cancel.setId("costi-cancel");
        save.getStyleClass().add("primary-button");
        cancel.getStyleClass().add("secondary-button");
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getChildren().addAll(cancel, save);
        feedback.setId("costi-feedback");
        feedback.setWrapText(true);
        feedback.setMinHeight(22);
        VBox detail = new VBox(8, code, name, status, scroll, feedback, footer);
        detail.setId("costi-detail");
        detail.setMinWidth(390);
        detail.getStyleClass().add("costs-card");
        SplitPane split = new SplitPane(catalogue, detail);
        split.setDividerPositions(.46);
        split.getStyleClass().add("costs-split");
        VBox.setVgrow(split, Priority.ALWAYS);
        VBox content = new VBox(16, top, split);
        content.setPadding(new Insets(20, 24, 16, 24));
        setCenter(content);
        Label note =
                label(
                        "Quantità aggiornate manualmente · Nessun carico o scarico automatico",
                        "costs-footer");
        setBottom(note);
        for (TextField field : List.of(simplePrice, simpleQuantity, date.getEditor()))
            field.textProperty().addListener((o, b, a) -> changed());
        modes.selectedToggleProperty()
                .addListener(
                        (o, b, a) -> {
                            if (a == null) {
                                if (b != null) b.setSelected(true);
                                return;
                            }
                            renderMode();
                            changed();
                        });
        simpleMode.setOnAction(e -> filter.setValue(Filter.SEMPLICE));
        advancedMode.setOnAction(e -> filter.setValue(Filter.AVANZATA));
        lots.onChanged(this::changed);
        clear();
    }

    private void setupTable() {
        table.getColumns().clear();
        table.setId("costi-powders-table");
        table.setFixedCellSize(64);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(
                label(
                        "Nessuna polvere trovata. Le anagrafiche si creano in TON → Dati tecnici →"
                                + " Polveri.",
                        "costs-muted"));
        TableColumn<Row, Row> powder = new TableColumn<>("Polvere");
        powder.setPrefWidth(165);
        powder.setMinWidth(110);
        powder.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        powder.setCellFactory(
                c ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(Row row, boolean empty) {
                                super.updateItem(row, empty);
                                setText(null);
                                if (empty || row == null) {
                                    setGraphic(null);
                                    return;
                                }
                                Label title = label(row.powder().code(), "costs-code");
                                Label name =
                                        label(
                                                row.powder().name()
                                                        + (row.powder().active()
                                                                ? ""
                                                                : " · Archiviata"),
                                                "costs-muted");
                                name.setWrapText(true);
                                setGraphic(new VBox(3, title, name));
                            }
                        });
        table.getColumns().add(powder);
        column("Totale kg", 110, row -> PriceFormats.quantity(row.quantity()));
        if (mode() == Mode.AVANZATA) {
            column("Nei lotti kg", 100, row -> PriceFormats.quantity(row.allocated()));
            column("Non assegnata", 110, row -> PriceFormats.quantity(row.unassigned()));
            column("Dettaglio", 140, Row::detailStatus);
        } else {
            column("Prezzo rif.", 130, row -> PriceFormats.price(row.unitPrice()));
            column(
                    "Dati base",
                    120,
                    row ->
                            row.quantity() == null || row.unitPrice() == null
                                    ? "Da compilare"
                                    : "Compilati");
        }
        table.getColumns().forEach(c -> c.setSortable(false));
    }

    private void column(String title, double width, java.util.function.Function<Row, String> text) {
        TableColumn<Row, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setMinWidth(title.equals("Modalità") ? 100 : 70);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(text.apply(c.getValue())));
        if (title.equals("Prezzo usato"))
            column.setCellFactory(
                    ignored ->
                            new TableCell<>() {
                                @Override
                                protected void updateItem(String value, boolean empty) {
                                    super.updateItem(value, empty);
                                    setText(empty ? null : value);
                                    getStyleClass().remove("costs-price-missing");
                                    if (!empty && "Da compilare".equals(value))
                                        getStyleClass().add("costs-price-missing");
                                }
                            });
        table.getColumns().add(column);
    }

    private static VBox field(String text, TextField input) {
        Label label = new Label(text);
        label.setLabelFor(input);
        return new VBox(6, label, input);
    }

    private static VBox summary(String text, Label value) {
        Label heading = label(text, "costs-summary-label");
        heading.setWrapText(true);
        value.getStyleClass().add("costs-summary-value");
        value.setWrapText(true);
        VBox box = new VBox(5, heading, value);
        box.getStyleClass().add("costs-summary");
        box.setMinWidth(0);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setPrefWidth(150);
        return box;
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        return label;
    }

    public void onChanged(Runnable action) {
        changed = action;
    }

    private void changed() {
        if (!showing) {
            updateTotals();
            changed.run();
        }
    }

    private void renderMode() {
        boolean isSimple = mode() == Mode.SEMPLICE;
        simple.setVisible(true);
        simple.setManaged(true);
        advanced.setVisible(!isSimple);
        advanced.setManaged(!isSimple);
        setupTable();
    }

    public void browseLevel() {
        boolean before = showing;
        showing = true;
        try {
            if (filter.getValue() == Filter.AVANZATA) advancedMode.setSelected(true);
            else if (filter.getValue() == Filter.SEMPLICE || modes.getSelectedToggle() == null)
                simpleMode.setSelected(true);
            renderMode();
        } finally {
            showing = before;
        }
    }

    private Mode mode() {
        return advancedMode.isSelected() ? Mode.AVANZATA : Mode.SEMPLICE;
    }

    public FormState state() {
        return new FormState(
                date.getEditor().getText(),
                simplePrice.getText(),
                simpleQuantity.getText(),
                lots.inputs());
    }

    public Draft draft() {
        LocalDate reference;
        try {
            reference = date.getConverter().fromString(date.getEditor().getText());
        } catch (java.time.format.DateTimeParseException invalid) {
            throw new IllegalArgumentException("Data: usa il formato gg/mm/aaaa.");
        }
        return new Draft(
                mode(),
                reference,
                PriceFormats.decimal(simplePrice.getText(), "Prezzo semplice"),
                PriceFormats.decimal(simpleQuantity.getText(), "Quantità semplice"),
                lots.values());
    }

    public void show(Detail detail) {
        showing = true;
        try {
            code.setText(detail.powder().code());
            name.setText(
                    detail.powder().name()
                            + (detail.powder().active()
                                    ? " · Anagrafica TON"
                                    : " · Archiviata in TON"));
            status.setText(
                    detail.version() == 0
                            ? "Prezzo da compilare"
                            : "Fotografia salvata · revisione " + detail.version());
            Draft draft = detail.draft();
            date.setValue(draft == null ? LocalDate.now() : draft.referenceDate());
            date.getEditor().setText(date.getConverter().toString(date.getValue()));
            simplePrice.setText(PriceFormats.edit(draft == null ? null : draft.simplePrice()));
            simpleQuantity.setText(
                    PriceFormats.edit(draft == null ? null : draft.simpleQuantity()));
            lots.show(draft == null ? List.of() : draft.lots());
            browseLevel();
            renderMode();
            form.setVisible(true);
            footer.setVisible(true);
            form.setDisable(false);
            updateTotals();
            feedback("", false);
        } finally {
            showing = false;
        }
    }

    public void clear() {
        code.setText("Seleziona una polvere");
        name.setText("Le anagrafiche arrivano dal catalogo TON.");
        status.setText("");
        form.setVisible(false);
        footer.setVisible(false);
        feedback("", false);
    }

    public void controls(boolean busy, boolean dirty, boolean selected) {
        form.setDisable(busy);
        save.setDisable(busy || !dirty || !selected);
        cancel.setDisable(busy || !dirty || !selected);
        home.setDisable(busy);
        table.setDisable(busy);
    }

    public void page(Page page) {
        table.getItems().setAll(page.rows());
        previous.setDisable(page.offset() == 0);
        next.setDisable(!page.hasNext());
        long missing = page.rows().stream().filter(r -> r.unitPrice() == null).count();
        count.setText(
                page.total() == 0
                        ? "0 polveri"
                        : (page.offset() + 1)
                                + "–"
                                + (page.offset() + page.rows().size())
                                + " di "
                                + page.total()
                                + " · "
                                + missing
                                + (missing == 1
                                        ? " prezzo da compilare in pagina"
                                        : " prezzi da compilare in pagina"));
    }

    public void feedback(String message, boolean error) {
        feedback.setText(message);
        feedback.getStyleClass().removeAll("costs-error", "costs-success");
        feedback.getStyleClass().add(error ? "costs-error" : "costs-success");
    }

    private void updateTotals() {
        try {
            var values = lots.values();
            var totals = PowderPrices.aggregate(values);
            totalQuantity.setText(PriceFormats.quantity(totals.quantity()));
            totalValue.setText(PriceFormats.currency(totals.value()));
            averagePrice.setText(PriceFormats.price(totals.unitPrice()));
            var total = PriceFormats.decimal(simpleQuantity.getText(), "Quantità totale");
            var unassigned = total == null ? null : total.subtract(totals.quantity());
            unassignedQuantity.setText(PriceFormats.quantity(unassigned));
            totalNote.setText(
                    total == null
                            ? "Compila la quantità totale nei dati base per calcolare la parte non"
                                    + " assegnata."
                            : unassigned.signum() < 0
                                    ? "Incongruenza: i lotti superano il totale dichiarato di "
                                            + PriceFormats.number(unassigned.abs(), 0, 3)
                                            + " kg."
                                    : "I lotti sono un dettaglio del totale. La media dei soli"
                                            + " lotti non sostituisce il prezzo di riferimento,"
                                            + " soprattutto con quantità non assegnate.");
        } catch (IllegalArgumentException invalid) {
            unassignedQuantity.setText("Da verificare");
            totalQuantity.setText("—");
            totalValue.setText("—");
            averagePrice.setText("Da verificare");
            totalNote.setText("Completa o correggi quantità e prezzi delle righe.");
        }
    }
}

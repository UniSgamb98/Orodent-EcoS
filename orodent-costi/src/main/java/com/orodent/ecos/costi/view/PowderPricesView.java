package com.orodent.ecos.costi.view;

import com.orodent.ecos.costi.model.PowderPrices;
import com.orodent.ecos.costi.model.PowderPrices.*;

import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.*;
import java.util.*;
import java.util.function.BiConsumer;

/** One shared snapshot editor: inline prices and detail fields never hold independent drafts. */
public final class PowderPricesView extends CostsShell {
    public enum InlineField {
        PRICE,
        QUANTITY
    }

    public final Button refresh = new Button("Ricarica");
    public final Button save = new Button("Salva"), cancel = new Button("Annulla");
    public final CheckBox inactive = new CheckBox("Includi archiviate");
    public final TableView<Row> table = new TableView<>();
    public final Button orders = new Button("Ordini in arrivo");

    public record Supplier(String name) {
        @Override
        public String toString() {
            return name == null
                    ? "Tutti i fornitori"
                    : name.isEmpty() ? "Fornitore da specificare" : name;
        }
    }

    public final ComboBox<Supplier> supplier = new ComboBox<>();
    private boolean updatingSupplier;
    public final Map<String, CheckBox> colors = new LinkedHashMap<>();
    private final VBox form = new VBox(8), catalogue = new VBox(12), detail = new VBox(12);
    private final SplitPane split = new SplitPane();
    private HBox detailHeader;
    private final BorderPane workspace = new BorderPane();
    private final TabPane compact = new TabPane();
    private final Tab listTab = new Tab("Polveri"), detailTab = new Tab("Dettaglio e lotti");
    private final Label code = new Label("Seleziona una polvere"),
            name = new Label(),
            status = new Label(),
            feedback = new Label(),
            unknown = new Label("Le polveri senza colore restano visibili nei filtri attivi.");
    private final HBox identity = new HBox(10);
    private final Label available = new Label("—"),
            totalQuantity = new Label("—"),
            totalValue = new Label("—"),
            averagePrice = new Label("—"),
            totalNote = new Label(),
            unassignedQuantity = new Label();
    private final StringProperty referencePrice = new SimpleStringProperty(""),
            availableQuantity = new SimpleStringProperty("");
    private final DatePicker date = new DatePicker();
    private final LotsEditor lots = new LotsEditor();
    private final Label editContext = new Label("Seleziona una polvere");
    private final LongProperty selectedPowder = new SimpleLongProperty();
    private final BooleanProperty locked = new SimpleBooleanProperty();
    private Runnable changed = () -> {}, colorsChanged = () -> {};
    private BiConsumer<Row, InlineField> valueRequested = (row, field) -> {};
    private boolean showing;
    private Mode retainedMode = Mode.AVANZATA;
    private String layoutMode = "";
    private double divider = .34;

    public record FormState(
            String dateText, String price, String quantity, List<LotsEditor.Input> lots) {}

    public PowderPricesView() {
        super("Polveri e prezzi");
        setId("costi-module");
        getStylesheets()
                .add(PowderPricesView.class.getResource("/costi/powders.css").toExternalForm());
        var heading =
                new VBox(
                        4,
                        label("Polveri e prezzi", "costs-title"),
                        label(
                                "Disponibilità, prezzi di riferimento e dettaglio dei lotti",
                                "costs-muted"));
        Region headingGap = new Region();
        HBox.setHgrow(headingGap, Priority.ALWAYS);
        orders.setId("costi-orders");
        HBox top = new HBox(12, heading, headingGap, orders);
        top.setAlignment(Pos.CENTER_LEFT);
        FlowPane colorBar = new FlowPane(12, 8);
        String[] labels = {"Bianche", "Gialle", "Rosa", "Grigie"};
        String[] values = {"BIANCA", "GIALLA", "ROSA", "GRIGIA"};
        for (int i = 0; i < values.length; i++) {
            String color = values[i];
            CheckBox box = new CheckBox(labels[i]);
            box.setSelected(true);
            box.setId("costi-color-" + color.toLowerCase(Locale.ROOT));
            box.getStyleClass().addAll("powder-color-filter", colorStyle(color));
            box.setOnAction(e -> colorsChanged.run());
            colors.put(color, box);
            colorBar.getChildren().add(box);
        }
        refresh.setId("costi-refresh");
        setupTable();
        supplier.setId("costi-supplier-filter");
        supplier.setMaxWidth(Double.MAX_VALUE);
        supplier.setAccessibleText("Filtra le polveri per fornitore");
        supplier.getItems().add(new Supplier(null));
        supplier.getSelectionModel().selectFirst();
        supplier.setOnAction(
                e -> {
                    if (!updatingSupplier) colorsChanged.run();
                });
        unknown.getStyleClass().add("costs-muted");
        unknown.setWrapText(true);
        unknown.setVisible(false);
        unknown.setManaged(false);
        catalogue
                .getChildren()
                .addAll(colorBar, supplier, new FlowPane(10, 8, inactive, refresh), table, unknown);
        catalogue.getStyleClass().add("costs-card");
        catalogue.setMinSize(0, 0);
        VBox.setVgrow(table, Priority.ALWAYS);
        code.getStyleClass().add("powder-detail-code");
        code.setWrapText(true);
        name.setWrapText(true);
        name.getStyleClass().add("costs-muted");
        status.getStyleClass().add("costs-muted");
        status.setId("costi-status");
        date.setId("costi-date");
        date.setPrefWidth(160);
        date.setMaxWidth(180);
        date.setPromptText("gg/mm/aaaa");
        date.setConverter(
                new StringConverter<>() {
                    private final DateTimeFormatter format =
                            DateTimeFormatter.ofPattern("dd/MM/uuuu")
                                    .withResolverStyle(ResolverStyle.STRICT);

                    public String toString(LocalDate value) {
                        return value == null ? "" : format.format(value);
                    }

                    public LocalDate fromString(String text) {
                        return text == null || text.isBlank()
                                ? null
                                : LocalDate.parse(text.strip(), format);
                    }
                });
        date.setTooltip(new Tooltip("Data della fotografia della polvere selezionata."));
        VBox names = new VBox(4, code, name);
        names.setMinWidth(0);
        HBox.setHgrow(names, Priority.ALWAYS);
        identity.getChildren().setAll(names, cancel, save);
        identity.setAlignment(Pos.CENTER_LEFT);
        save.setText("");
        SVGPath disk = new SVGPath();
        disk.setContent(
                "M17 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zM12 19c-1.66"
                    + " 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zM15 9H5V5h10v4z");
        disk.setStyle("-fx-fill: white;");
        save.setGraphic(disk);
        save.setAccessibleText("Salva prezzi e disponibilità della polvere selezionata");
        save.setTooltip(new Tooltip("Salva prezzi, disponibilità e lotti"));
        cancel.setText("↶");
        cancel.setTooltip(new Tooltip("Annulla le modifiche non salvate"));
        cancel.setAccessibleText("Annulla modifiche alla polvere selezionata");
        editContext.getStyleClass().add("costs-muted");
        editContext.setWrapText(true);
        VBox identityGroup = new VBox(5, identity, status, editContext);
        detailHeader = new HBox(14, identityGroup, new VBox(5, new Label("Dati al"), date));
        detailHeader.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(identityGroup, Priority.ALWAYS);
        identityGroup.setMinWidth(0);
        available.setId("costi-available");
        averagePrice.setId("costi-average-price");
        totalQuantity.setId("costi-total-quantity");
        totalValue.setId("costi-total-value");
        unassignedQuantity.setId("costi-unassigned");
        HBox quantities =
                new HBox(
                        8,
                        summary("Totale dichiarato", available),
                        summary("Nei lotti", totalQuantity),
                        summary("Da assegnare", unassignedQuantity));
        FlowPane valuesFooter =
                new FlowPane(
                        18,
                        6,
                        inlineSummary("Valore dei lotti", totalValue),
                        inlineSummary("Media dei soli lotti", averagePrice));
        valuesFooter.getStyleClass().add("powders-value-summary");
        totalNote.setWrapText(true);
        totalNote.getStyleClass().add("costs-muted");
        form.getChildren().addAll(quantities, lots, valuesFooter, totalNote);
        form.setMinHeight(0);
        lots.setMinHeight(0);
        VBox.setVgrow(lots, Priority.ALWAYS);
        VBox.setVgrow(form, Priority.ALWAYS);
        save.setId("costi-save");
        cancel.setId("costi-cancel");
        save.getStyleClass().add("primary-button");
        feedback.setId("costi-feedback");
        feedback.setWrapText(true);
        feedback.setMinHeight(Region.USE_PREF_SIZE);
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        detail.getChildren().addAll(detailHeader, form);
        detail.setId("costi-detail");
        detail.setMinSize(0, 0);
        detail.getStyleClass().add("costs-card");
        split.getStyleClass().add("costs-split");
        split.setMinSize(0, 0);
        listTab.setClosable(false);
        detailTab.setClosable(false);
        compact.getTabs().setAll(listTab, detailTab);
        compact.setMinSize(0, 0);
        compact.setId("costi-compact-tabs");
        workspace.setMinSize(0, 0);
        workspace.widthProperty().addListener((o, b, a) -> layoutWorkspace(false));
        VBox.setVgrow(workspace, Priority.ALWAYS);
        VBox content = new VBox(14, top, workspace, feedback);
        content.setMinSize(0, 0);
        content.setPadding(new Insets(18, 22, 16, 22));
        setCenter(content);
        layoutWorkspace(false);
        for (StringProperty input :
                List.of(referencePrice, availableQuantity, date.getEditor().textProperty()))
            input.addListener((o, b, a) -> changed());
        lots.onChanged(this::changed);
        clear();
    }

    private void setupTable() {
        table.setId("costi-powders-table");
        table.setFixedCellSize(64);
        table.setMinHeight(100);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(
                label(
                        "Nessuna polvere con i colori selezionati. Le anagrafiche si gestiscono in"
                                + " TON.",
                        "costs-muted"));
        TableColumn<Row, Row> powder = new TableColumn<>("Polvere");
        powder.setPrefWidth(230);
        powder.setMinWidth(175);
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
                                Label title = new Label(row.powder().code());
                                title.getStyleClass().add("costs-code");
                                Label text =
                                        new Label(
                                                row.powder().name()
                                                        + (row.powder().active()
                                                                ? ""
                                                                : " · Archiviata"));
                                text.getStyleClass().add("powder-badge-name");
                                title.setMaxWidth(Double.MAX_VALUE);
                                text.setMaxWidth(Double.MAX_VALUE);
                                var box = new VBox(3, title, text);
                                box.getStyleClass()
                                        .addAll("powder-badge", colorStyle(row.powder().color()));
                                box.setMaxWidth(Double.MAX_VALUE);
                                Tooltip.install(
                                        box,
                                        new Tooltip(
                                                row.powder().code() + "\n" + row.powder().name()));
                                setGraphic(box);
                            }
                        });
        TableColumn<Row, Row> quantity = new TableColumn<>("Disponibilità kg");
        quantity.setPrefWidth(125);
        quantity.setMinWidth(115);
        quantity.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        quantity.setCellFactory(
                c ->
                        new PowderValueCell(
                                selectedPowder,
                                locked,
                                availableQuantity,
                                Row::quantity,
                                "quantity",
                                "Disponibilità kg",
                                r -> valueRequested.accept(r, InlineField.QUANTITY)));
        TableColumn<Row, Row> price = new TableColumn<>("Prezzo €/kg");
        price.setPrefWidth(135);
        price.setMinWidth(115);
        price.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        price.setCellFactory(
                c ->
                        new PowderValueCell(
                                selectedPowder,
                                locked,
                                referencePrice,
                                Row::unitPrice,
                                "price",
                                "Prezzo di riferimento €/kg",
                                r -> valueRequested.accept(r, InlineField.PRICE)));
        table.getColumns().setAll(powder, quantity, price);
        table.getColumns().forEach(c -> c.setSortable(false));
    }

    private void layoutWorkspace(boolean selected) {
        String next = workspace.getWidth() >= 1120 ? "split" : "compact";
        if (next.equals(layoutMode)) {
            if (selected && next.equals("compact")) compact.getSelectionModel().select(detailTab);
            return;
        }
        if (layoutMode.equals("split") && split.getDividerPositions().length > 0)
            divider = split.getDividerPositions()[0];
        workspace.setCenter(null);
        workspace.setTop(null);
        detail.getChildren().remove(detailHeader);
        detailHeader.getStyleClass().remove("costs-card");
        split.getItems().clear();
        listTab.setContent(null);
        detailTab.setContent(null);
        layoutMode = next;
        catalogue.setMinWidth(0);
        detail.setMinWidth(0);
        if (next.equals("split")) {
            detail.getChildren().addFirst(detailHeader);
            catalogue.setMinWidth(490);
            detail.setMinWidth(560);
            split.getItems().setAll(catalogue, detail);
            split.setDividerPositions(divider);
            workspace.setCenter(split);
        } else {
            workspace.setTop(detailHeader);
            BorderPane.setMargin(detailHeader, new Insets(0, 0, 10, 0));
            detailHeader.getStyleClass().add("costs-card");
            listTab.setContent(catalogue);
            detailTab.setContent(detail);
            workspace.setCenter(compact);
            compact.getSelectionModel().select(selected ? detailTab : listTab);
        }
    }

    public Set<String> selectedColors() {
        var selected = new LinkedHashSet<String>();
        colors.forEach(
                (color, box) -> {
                    if (box.isSelected()) selected.add(color);
                });
        return Set.copyOf(selected);
    }

    public void selectedColors(Set<String> selected) {
        colors.forEach((color, box) -> box.setSelected(selected.contains(color)));
    }

    public void onColorsChanged(Runnable action) {
        colorsChanged = action;
    }

    public void onValueRequested(BiConsumer<Row, InlineField> action) {
        valueRequested = action;
    }

    public void focusInline(long id, InlineField kind) {
        if (layoutMode.equals("compact")) compact.getSelectionModel().select(listTab);
        table.applyCss();
        table.layout();
        String prefix = kind == InlineField.QUANTITY ? "quantity" : "price";
        var field = table.lookup("#costi-inline-" + prefix + "-" + id);
        if (field instanceof TextField input) {
            input.requestFocus();
            input.selectAll();
        }
    }

    private static HBox inlineSummary(String text, Label value) {
        value.getStyleClass().add("powders-inline-value");
        var box = new HBox(8, label(text, "costs-summary-label"), value);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static VBox summary(String text, Label value) {
        value.getStyleClass().add("costs-summary-value");
        value.setWrapText(true);
        var box = new VBox(5, label(text, "costs-summary-label"), value);
        box.getStyleClass().add("costs-summary");
        box.setMinWidth(0);
        box.setPrefWidth(150);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        return label;
    }

    private static String colorStyle(String color) {
        return switch (Objects.toString(color, "")) {
            case "BIANCA" -> "powder-white";
            case "GIALLA" -> "powder-yellow";
            case "ROSA" -> "powder-pink";
            case "GRIGIA" -> "powder-gray";
            default -> "powder-unknown";
        };
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

    public FormState state() {
        return new FormState(
                date.getEditor().getText(),
                referencePrice.get(),
                availableQuantity.get(),
                lots.inputs());
    }

    public Draft draft() {
        LocalDate reference;
        try {
            reference = date.getConverter().fromString(date.getEditor().getText());
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Data: usa il formato gg/mm/aaaa.");
        }
        return new Draft(
                retainedMode,
                reference,
                PriceFormats.decimal(referencePrice.get(), "Prezzo di riferimento"),
                PriceFormats.decimal(availableQuantity.get(), "Quantità disponibile"),
                lots.values());
    }

    public void show(Detail value) {
        showing = true;
        try {
            selectedPowder.set(0);
            code.setText(value.powder().code());
            name.setText(
                    value.powder().name()
                            + (value.powder().supplier().isBlank()
                                    ? ""
                                    : " · " + value.powder().supplier())
                            + (value.powder().active() ? "" : " · Archiviata in TON"));
            identity.getStyleClass()
                    .setAll("powder-detail-identity", colorStyle(value.powder().color()));
            status.setText(
                    value.version() == 0
                            ? "Prezzo da compilare"
                            : "Fotografia salvata · revisione " + value.version());
            Draft draft = value.draft();
            retainedMode = draft == null ? Mode.AVANZATA : draft.mode();
            date.setValue(draft == null ? LocalDate.now() : draft.referenceDate());
            date.getEditor().setText(date.getConverter().toString(date.getValue()));
            referencePrice.set(PriceFormats.edit(draft == null ? null : draft.simplePrice()));
            availableQuantity.set(PriceFormats.edit(draft == null ? null : draft.simpleQuantity()));
            lots.show(draft == null ? List.of() : draft.lots());
            selectedPowder.set(value.powder().id());
            form.setVisible(true);
            form.setManaged(true);

            form.setDisable(false);
            updateTotals();
            feedback("", false);
        } finally {
            showing = false;
        }
    }

    public void showSelectedDetail() {
        layoutWorkspace(true);
    }

    public void clear() {
        selectedPowder.set(0);
        code.setText("Seleziona una polvere");
        name.setText("Prezzi e lotti si salvano per identità della polvere TON.");
        identity.getStyleClass().setAll("powder-detail-identity");
        status.setText("");
        form.setVisible(false);
        form.setManaged(false);

        feedback("", false);
    }

    public void controls(boolean busy, boolean dirty, boolean selected) {
        locked.set(busy);
        editContext.setText(
                !selected
                        ? "Seleziona una polvere"
                        : code.getText()
                                + (dirty
                                        ? " · Modifiche non salvate"
                                        : " · Nessuna modifica in sospeso"));
        form.setDisable(busy || !selected);
        date.setDisable(busy || !selected);
        save.setDisable(busy || !dirty || !selected);
        cancel.setDisable(busy || !dirty || !selected);
        home.setDisable(busy);
        table.setDisable(busy);
        colors.values().forEach(b -> b.setDisable(busy));
        inactive.setDisable(busy);
        supplier.setDisable(busy);
    }

    public String selectedSupplier() {
        return supplier.getValue() == null ? null : supplier.getValue().name();
    }

    public void selectedSupplier(String name) {
        updatingSupplier = true;
        try {
            supplier.setValue(new Supplier(name));
        } finally {
            updatingSupplier = false;
        }
    }

    public void suppliers(Collection<String> names) {
        String selected = selectedSupplier();
        updatingSupplier = true;
        try {
            var choices = new ArrayList<Supplier>();
            choices.add(new Supplier(null));
            names.stream()
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .map(Supplier::new)
                    .forEach(choices::add);
            supplier.getItems().setAll(choices);
            supplier.setValue(new Supplier(selected));
        } finally {
            updatingSupplier = false;
        }
    }

    public void page(Page page) {
        table.getItems().setAll(page.rows());
        boolean uncolored = page.rows().stream().anyMatch(r -> r.powder().color() == null);
        unknown.setVisible(uncolored);
        unknown.setManaged(uncolored);
    }

    public void feedback(String message, boolean error) {
        feedback.setText(message);
        feedback.getStyleClass().removeAll("costs-error", "costs-success");
        feedback.getStyleClass().add(error ? "costs-error" : "costs-success");
    }

    private void updateTotals() {
        try {
            var totals = PowderPrices.aggregate(lots.values());
            totalQuantity.setText(PriceFormats.quantity(totals.quantity()));
            totalValue.setText(PriceFormats.currency(totals.value()));
            averagePrice.setText(PriceFormats.price(totals.unitPrice()));
            var total = PriceFormats.decimal(availableQuantity.get(), "Quantità totale");
            available.setText(PriceFormats.quantity(total));
            var unassigned = total == null ? null : total.subtract(totals.quantity());
            unassignedQuantity.setText(PriceFormats.quantity(unassigned));
            totalNote.setText(
                    total == null
                            ? "Senza quantità totale non si calcolano scorta e quantità da"
                                    + " assegnare."
                            : unassigned.signum() < 0
                                    ? "Incongruenza: i lotti superano il totale di "
                                            + PriceFormats.number(unassigned.abs(), 0, 3)
                                            + " kg."
                                    : "La media dei soli lotti è distinta dal prezzo di"
                                            + " riferimento; i lotti sono un dettaglio della"
                                            + " disponibilità totale.");
        } catch (IllegalArgumentException invalid) {
            available.setText("Da verificare");
            unassignedQuantity.setText("Da verificare");
            totalQuantity.setText("—");
            totalValue.setText("—");
            averagePrice.setText("Da verificare");
            totalNote.setText("Completa o correggi quantità e prezzi dei lotti.");
        }
    }
}

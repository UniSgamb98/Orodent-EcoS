package com.orodent.ecos.costi.products.view;

import com.orodent.ecos.costi.products.model.NominalMixtureCost;
import com.orodent.ecos.costi.products.model.PowderPricing;
import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.view.CostsShell;
import com.orodent.ecos.costi.view.PriceFormats;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;

import java.util.*;
import java.util.function.Function;

/**
 * Product catalogue and per-article costing, with provenance and editable transformation inputs.
 */
public final class ProductCostsView extends CostsShell {
    public record Line(Long id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public final TextField search = new TextField(),
            commonCost = new TextField(),
            articleCost = new TextField();
    public final ComboBox<Line> line = new ComboBox<>();
    public final CheckBox archived = new CheckBox("Includi archiviate"),
            alternatives = new CheckBox("Mostra tutte le configurazioni approvate"),
            specific = new CheckBox("Trasformazione specifica per questo articolo");
    public final Button refresh = button("Ricarica prezzi e dati", "costs-products-refresh", false),
            previous = button("←", "costs-products-previous", false),
            next = button("→", "costs-products-next", false),
            save = button("Salva parametri", "costs-products-save", true),
            cancel = button("Annulla", "costs-products-cancel", false),
            exportAll = button("Esporta tutti i prezzi", "costs-products-export-all", true);
    public final MenuButton export = new MenuButton("Esporta CSV");
    public final MenuItem exportProduct = new MenuItem("Prodotto selezionato · Articoli e costi"),
            exportDetail = new MenuItem("Prodotto selezionato · Dettaglio polveri"),
            exportFiltered = new MenuItem("Prodotti filtrati · Articoli e costi");
    private Map<Long, NominalMixtureCost.Summary> nominalPrices = Map.of();

    public void nominalPrices(Map<Long, NominalMixtureCost.Summary> values) {
        nominalPrices = Map.copyOf(values);
        products.refresh();
    }

    public final ComboBox<PowderPricing.Mode> pricingMode = new ComboBox<>();
    public final CheckBox includeOrders = new CheckBox("Includi ordini in arrivo");
    public final Button valuationWarning =
            new Button("⚠ Prezzi integrati con il riferimento · Dettagli");

    public PowderPricing.Selection pricingSelection() {
        return new PowderPricing.Selection(pricingMode.getValue(), includeOrders.isSelected());
    }

    private void priceInfo(String title, String message) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        if (getScene() != null) dialog.initOwner(getScene().getWindow());
        TextArea text = new TextArea(message);
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefSize(660, 360);
        dialog.getDialogPane().setContent(text);
        dialog.showAndWait();
    }

    public final TableView<Product> products = new TableView<>();
    public final TableView<Result> articles = new TableView<>();
    public final TableView<Material> materials = new TableView<>();
    private final Label heading = label("Seleziona un prodotto", "costs-detail-title"),
            productInfo =
                    label("Le anagrafiche e le configurazioni arrivano da TON.", "costs-muted"),
            count = label("", "costs-muted"),
            feedback = label("", "costs-muted"),
            provenance = label("", "costs-muted"),
            selected = label("Seleziona un articolo", "costs-code"),
            parameterVersion = label("", "costs-muted"),
            snapshotDate = label("", "costs-muted"),
            issues = label("", "costs-muted");
    private final BorderPane workspace = new BorderPane();
    private final SplitPane split = new SplitPane();
    private final TabPane compact = new TabPane();
    private final Tab catalogueTab = new Tab("Prodotti"), detailTab = new Tab("Articoli e costi");
    private final VBox catalogue = new VBox(10), detail = new VBox(10);
    private final FlowPane overrideEditor = new FlowPane(10, 6);
    private final FlowPane summary = new FlowPane(18, 8);
    private final Label powderAmount = label("—", "products-summary-amount"),
            transformationAmount = label("—", "products-summary-amount"),
            totalAmount = label("—", "products-total"),
            pending = label("Calcolo in aggiornamento…", "costs-muted");
    private Map<Long, String> powderColors = Map.of();
    private boolean compactLayout;
    private double divider = .26;

    public void powderColors(Map<Long, String> colors) {
        powderColors = Map.copyOf(colors);
        materials.refresh();
    }

    public ProductCostsView() {
        super("Costi prodotti");
        setId("costi-products-module");
        getStylesheets()
                .add(ProductCostsView.class.getResource("/costi/products.css").toExternalForm());
        Label title = label("Costi prodotti", "costs-title");
        exportAll.setTooltip(
                new Tooltip(
                        "Intero catalogo, incluse archiviate, con configurazioni predefinite."
                                + " Ignora i filtri di ricerca."));
        exportAll.setAccessibleText("Esporta i costi di tutti i prodotti, anche fuori dai filtri");
        export.setId("costs-products-export");
        export.getStyleClass().add("secondary-button");
        export.getItems()
                .addAll(exportProduct, exportDetail, new SeparatorMenuItem(), exportFiltered);
        Button help = button("ⓘ Criteri", "costs-products-help", false);
        help.setOnAction(
                e ->
                        priceInfo(
                                "Come leggere i costi",
                                "Miscela €/kg: costo nominale della ricetta, senza sovramateriali,"
                                    + " perdite o trasformazione. Può variare fra le configurazioni"
                                    + " predefinite del prodotto.\n\n"
                                    + "Polveri €: costo del materiale effettivo dell'articolo,"
                                    + " inclusi sovramateriali e maggiorazione per perdite (+0,5%,"
                                    + " fattore 1,005 applicato una volta).\n\n"
                                    + "Trasformazione: importo comune al prodotto, sostituibile per"
                                    + " singolo articolo. Un importo zero è valido; un dato"
                                    + " mancante resta da compilare.\n\n"
                                    + "Esporta tutti i prezzi: intero catalogo, incluse anagrafiche"
                                    + " archiviate, con configurazioni predefinite. Le altre"
                                    + " esportazioni seguono il prodotto o i filtri selezionati."
                                    + " Nessun movimento automatico di magazzino."));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        FlowPane actions = new FlowPane(8, 6, help, export, exportAll);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox headingRow = new HBox(14, title, gap, actions);
        headingRow.setAlignment(Pos.CENTER_LEFT);
        search.setId("costs-products-search");
        search.setPromptText("Cerca prodotto, linea o colore");
        line.setId("costs-products-line");
        line.setMaxWidth(Double.MAX_VALUE);
        products.setId("costs-products-list");
        products.setFixedCellSize(46);
        column(products, "Linea", 60, p -> Objects.toString(p.lineCode(), "—"));
        column(products, "Prodotto", 115, p -> p.code() + (p.active() ? "" : " · Archiviato"));
        column(
                products,
                "Miscela €/kg",
                112,
                p -> {
                    var value = nominalPrices.get(p.id());
                    return value != null && value.variable()
                            ? "Più valori"
                            : PriceFormats.number(value == null ? null : value.price(), 2, 4);
                });
        products.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        products.setPlaceholder(
                label("Nessun prodotto. Le anagrafiche si creano in TON.", "costs-muted"));
        HBox paging = new HBox(7, previous, count, next);
        paging.setAlignment(Pos.CENTER_LEFT);
        catalogue.getChildren().addAll(search, line, archived, products, paging);
        catalogue.setMinWidth(326);
        catalogue.setPrefWidth(330);
        catalogue.getStyleClass().add("costs-card");
        VBox.setVgrow(products, Priority.ALWAYS);
        commonCost.setId("costs-products-common");
        commonCost.setPromptText("Da compilare");
        commonCost.setPrefWidth(110);
        commonCost.setMaxWidth(110);
        articleCost.setId("costs-products-override");
        articleCost.setPromptText("€/articolo");
        articleCost.setPrefWidth(110);
        articleCost.setMaxWidth(130);
        specific.setId("costs-products-specific");
        specific.setText("Importo specifico");
        HBox input =
                new HBox(
                        8,
                        label("Trasformazione comune", "costs-code"),
                        commonCost,
                        new Label("€/articolo"));
        input.setAlignment(Pos.CENTER_LEFT);
        alternatives.setId("costs-products-alternatives");
        alternatives.setTooltip(
                new Tooltip(
                        "Le configurazioni alternative rimangono righe separate: non vengono"
                                + " sommate fra loro."));
        CustomMenuItem showAlternatives = new CustomMenuItem(alternatives, false);
        MenuButton options = new MenuButton("Vista articoli");
        options.setId("costs-products-options");
        options.getStyleClass().add("secondary-button");
        options.getItems().add(showAlternatives);
        FlowPane parameters = new FlowPane(12, 6, input, options);
        articles.setId("costs-products-articles");
        articles.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        articles.setFixedCellSize(54);
        articleIdentityColumn();
        column(
                articles,
                "Formato mm",
                102,
                r ->
                        r.article() == null
                                ? "—"
                                : "Ø "
                                        + PriceFormats.number(
                                                r.specification() == null
                                                        ? r.article().diameter()
                                                        : r.specification().diameter(),
                                                0,
                                                2)
                                        + " × "
                                        + PriceFormats.number(
                                                r.specification() == null
                                                        ? r.article().height()
                                                        : r.specification().height(),
                                                0,
                                                2));
        column(articles, "Miscela g", 86, r -> PriceFormats.number(r.grams(), 1, 3));
        column(articles, "Miscela €/kg", 112, r -> PriceFormats.number(r.nominalMixPrice(), 2, 4));
        column(articles, "Polveri €", 92, r -> PriceFormats.number(r.powderCost(), 2, 4));
        transformationColumn();
        column(articles, "Totale €", 100, r -> PriceFormats.number(r.total(), 2, 4));
        articles.getColumns().getLast().getStyleClass().add("products-total-column");
        articles.setMinHeight(80);
        articles.setPlaceholder(
                label("Nessun articolo disponibile per questo prodotto.", "costs-muted"));
        column(materials, "Strato", 60, m -> String.valueOf(m.layer()));
        TableColumn<Material, Material> powderColumn = new TableColumn<>("Polvere");
        powderColumn.setPrefWidth(195);
        powderColumn.setMinWidth(150);
        powderColumn.setSortable(false);
        powderColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        powderColumn.setCellFactory(
                c ->
                        new TableCell<>() {
                            protected void updateItem(Material material, boolean empty) {
                                super.updateItem(material, empty);
                                setText(null);
                                setGraphic(null);
                                setTooltip(null);
                                if (empty || material == null) return;
                                String text =
                                        material.ingredient().code()
                                                + " · v"
                                                + material.ingredient().technicalVersion();
                                Label badge = new Label(text);
                                badge.getStyleClass()
                                        .addAll(
                                                "products-powder-badge",
                                                colorStyle(
                                                        powderColors.get(
                                                                material.ingredient().powderId())));
                                badge.setMaxWidth(Double.MAX_VALUE);
                                setGraphic(badge);
                                setTooltip(new Tooltip(text));
                            }
                        });
        materials.getColumns().add(powderColumn);
        column(materials, "Massa %", 85, m -> PriceFormats.number(m.ingredient().percent(), 0, 4));
        column(materials, "Da pesare g", 110, m -> PriceFormats.number(m.grams(), 1, 4));
        TableColumn<Material, Material> priceColumn = new TableColumn<>("Prezzo €/kg");
        priceColumn.setPrefWidth(150);
        priceColumn.getStyleClass().add("products-number");
        priceColumn.setSortable(false);
        priceColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        priceColumn.setCellFactory(
                c ->
                        new TableCell<>() {
                            protected void updateItem(Material material, boolean empty) {
                                super.updateItem(material, empty);
                                setText(null);
                                setGraphic(null);
                                if (empty || material == null) return;
                                PowderPrice price = material.price();
                                Label amount =
                                        new Label(
                                                price == null
                                                        ? "—"
                                                        : PriceFormats.number(price.price(), 2, 8));
                                HBox row = new HBox(7, amount);
                                row.setAlignment(Pos.CENTER_LEFT);
                                if (price != null && !price.details().isBlank()) {
                                    Button info = new Button(price.estimated() ? "⚠" : "ⓘ");
                                    info.setId(
                                            price.estimated()
                                                    ? "costs-price-warning"
                                                    : "costs-price-info");
                                    if (price.estimated())
                                        info.setStyle(
                                                "-fx-text-fill: #a66a00; -fx-background-color:"
                                                        + " #fff3cd;");
                                    info.setTooltip(
                                            new Tooltip(
                                                    price.estimated()
                                                            ? "Una parte della scorta usa il prezzo"
                                                                  + " di riferimento. Clicca per il"
                                                                  + " calcolo."
                                                            : "Mostra origine e calcolo del"
                                                                    + " prezzo"));
                                    info.setOnAction(
                                            e ->
                                                    priceInfo(
                                                            material.ingredient().code()
                                                                    + " · Valutazione",
                                                            price.details()));
                                    row.getChildren().add(info);
                                }
                                setGraphic(row);
                            }
                        });
        materials.getColumns().add(priceColumn);
        column(materials, "Costo €", 95, m -> PriceFormats.number(m.cost(), 2, 6));
        column(
                materials,
                "Prezzo: revisione e data",
                205,
                m ->
                        m.price() == null
                                ? "Da compilare"
                                : m.price().mode()
                                        + " · v"
                                        + m.price().version()
                                        + " · "
                                        + Objects.toString(m.price().date(), "Non compilata"));
        materials.setId("costs-products-materials");
        materials.setFixedCellSize(44);
        materials.setMinHeight(80);
        materials.setPlaceholder(
                label("Seleziona un articolo nella scheda Articoli e costi.", "costs-muted"));
        TabPane tabs =
                new TabPane(
                        new Tab("Articoli e costi", articles),
                        new Tab("Dettaglio polveri", materials));
        tabs.setId("costs-products-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setMinHeight(100);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        selected.setId("costs-products-selected");
        provenance.setId("costs-products-provenance");
        provenance.setWrapText(false);
        provenance.setMaxWidth(Double.MAX_VALUE);
        provenance.textProperty().addListener((o, b, a) -> provenance.setTooltip(new Tooltip(a)));
        powderAmount.setId("costs-products-summary-powders");
        transformationAmount.setId("costs-products-summary-transformation");
        totalAmount.setId("costs-products-summary-total");
        pending.setId("costs-products-pending");
        visible(pending, false);
        summary.getChildren()
                .addAll(
                        amount("Polveri", powderAmount),
                        label("+", "costs-muted"),
                        amount("Trasformazione", transformationAmount),
                        label("=", "costs-muted"),
                        amount("Totale / articolo", totalAmount),
                        pending);
        summary.setAlignment(Pos.CENTER_LEFT);
        summary.getStyleClass().add("products-summary");
        overrideEditor
                .getChildren()
                .addAll(
                        label("Trasformazione articolo", "costs-code"),
                        specific,
                        articleCost,
                        new Label("€/articolo"));
        overrideEditor.setAlignment(Pos.CENTER_LEFT);
        overrideEditor.setId("costs-products-override-editor");
        visible(overrideEditor, false);
        specific.selectedProperty()
                .addListener(
                        (o, b, a) -> {
                            if (a) visible(overrideEditor, true);
                        });
        issues.setId("costs-products-issues");
        parameterVersion.setId("costs-products-parameter-version");
        // These remain in the scene so errors and revision provenance are always accessible.
        detail.getChildren()
                .addAll(
                        heading,
                        productInfo,
                        parameters,
                        tabs,
                        selected,
                        summary,
                        overrideEditor,
                        provenance,
                        issues);
        detail.getStyleClass().add("costs-card");
        detail.setMinWidth(0);
        catalogueTab.setClosable(false);
        detailTab.setClosable(false);
        compact.getTabs().addAll(catalogueTab, detailTab);
        compact.setId("costs-products-compact-tabs");
        compact.setMinHeight(0);
        split.getStyleClass().add("costs-split");
        split.setMinHeight(0);
        workspace.widthProperty().addListener((o, b, a) -> layoutWorkspace(a.doubleValue()));
        VBox.setVgrow(workspace, Priority.ALWAYS);
        pricingMode.getItems().setAll(PowderPricing.Mode.values());
        pricingMode.setValue(PowderPricing.Mode.SEMPLICE);
        pricingMode.setId("costs-pricing-mode");
        includeOrders.setId("costs-include-orders");
        includeOrders.setTooltip(
                new Tooltip(
                        "Include quantità residue e prezzi degli ordini aperti. Righe escluse,"
                                + " scadute o senza data non partecipano alla media."));
        includeOrders.setDisable(true);
        valuationWarning.setId("costs-valuation-warning");
        valuationWarning.setStyle("-fx-text-fill: #a66a00; -fx-background-color: #fff3cd;");
        valuationWarning.setVisible(false);
        valuationWarning.setManaged(false);
        valuationWarning.setOnAction(
                e -> {
                    var messages = new LinkedHashSet<String>();
                    for (Result r : articles.getItems())
                        for (Material m : r.materials())
                            if (m.price() != null && m.price().estimated())
                                messages.add(m.ingredient().code() + "\n" + m.price().details());
                    priceInfo("Quantità non assegnate ai lotti", String.join("\n\n", messages));
                });
        refresh.setText("Ricarica dati");
        snapshotDate.setWrapText(false);
        snapshotDate.setTooltip(
                new Tooltip(
                        "Prezzi e dati tecnici sono letti insieme. Premi Ricarica dati per"
                                + " aggiornarli."));
        FlowPane pricingControls =
                new FlowPane(
                        12,
                        8,
                        new Label("Prezzi polveri"),
                        pricingMode,
                        includeOrders,
                        refresh,
                        snapshotDate,
                        valuationWarning);
        pricingControls.setAlignment(Pos.CENTER_LEFT);
        pricingControls.getStyleClass().add("products-toolbar");
        Region footerGap = new Region();
        HBox.setHgrow(footerGap, Priority.ALWAYS);
        VBox context = new VBox(3, feedback, parameterVersion);
        context.setMinWidth(0);
        HBox footer = new HBox(12, context, footerGap, cancel, save);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setId("costs-products-footer");
        VBox page = new VBox(12, headingRow, pricingControls, workspace, footer);
        page.setPadding(new Insets(18, 20, 14, 20));
        setCenter(page);
        feedback.setId("costs-products-feedback");
        layoutWorkspace(1200);
        formEnabled(false);
    }

    public void product(Product product, Parameters parameters) {
        heading.setText(product == null ? "Seleziona un prodotto" : product.code());
        productInfo.setText(
                product == null
                        ? "Le anagrafiche e le configurazioni arrivano da TON."
                        : "Linea "
                                + Objects.toString(product.lineCode(), "—")
                                + " · "
                                + Objects.toString(product.lineName(), "")
                                + " · Colore "
                                + Objects.toString(product.color(), "—")
                                + (product.active() ? "" : " · Archiviato"));
        visible(overrideEditor, false);
        commonCost.setText(PriceFormats.edit(parameters.commonCost()));
        parameterVersion.setText(
                "Parametri di trasformazione · "
                        + (parameters.version() == 0
                                ? "non ancora salvati"
                                : "revisione " + parameters.version()));
    }

    public void results(List<Result> results, Long keepArticle, Long keepSpec) {
        articles.getItems().setAll(results);
        boolean estimated =
                results.stream()
                        .flatMap(r -> r.materials().stream())
                        .anyMatch(m -> m.price() != null && m.price().estimated());
        valuationWarning.setVisible(estimated);
        valuationWarning.setManaged(estimated);
        Result keep =
                results.stream()
                        .filter(
                                r ->
                                        r.article() != null
                                                && Objects.equals(keepArticle, r.article().id())
                                                && Objects.equals(
                                                        keepSpec,
                                                        r.specification() == null
                                                                ? null
                                                                : r.specification().id()))
                        .findFirst()
                        .orElse(null);
        if (keep != null) articles.getSelectionModel().select(keep);
        else if (!results.isEmpty()) articles.getSelectionModel().selectFirst();
    }

    public void article(Result row) {
        materials.getItems().setAll(row == null ? List.of() : row.materials());
        powderAmount.setText(euros(row == null ? null : row.powderCost()));
        transformationAmount.setText(euros(row == null ? null : row.transformation()));
        totalAmount.setText(euros(row == null ? null : row.total()));
        selected.setText(
                row == null || row.article() == null
                        ? "Nessun articolo selezionato"
                        : row.article().code() + (row.article().active() ? "" : " · Archiviato"));
        provenance.setText(
                row == null
                                || row.specification() == null
                                || row.recipe() == null
                                || row.model() == null
                        ? "Configurazione predefinita approvata da impostare in TON."
                        : "Configurazione v"
                                + row.specification().version()
                                + " · Composizione v"
                                + row.recipe().version()
                                + " · "
                                + row.model().code()
                                + " v"
                                + row.model().version()
                                + (row.specification().articleRevision() == null
                                        ? " · Misure correnti (configurazione precedente allo"
                                                + " storico)"
                                        : " · Misure da revisione articolo "
                                                + row.specification().articleRevision()));
        issues.setText(row == null ? "" : row.status());
        issues.getStyleClass().removeAll("costs-error", "costs-success");
        issues.getStyleClass()
                .add(row != null && row.problems().isEmpty() ? "costs-success" : "costs-error");
    }

    public void count(int offset, int size, int total) {
        count.setText(
                total == 0
                        ? "Nessun risultato"
                        : (offset + 1) + "–" + (offset + size) + " di " + total);
        previous.setDisable(offset == 0);
        next.setDisable(offset + size >= total);
    }

    public void captured(String text) {
        snapshotDate.setText(text.replace("Prezzi e dati letti il ", "Aggiornati "));
    }

    public void costsPending(boolean pending) {
        articles.setOpacity(pending ? .55 : 1);
        materials.setOpacity(pending ? .55 : 1);
        summary.setOpacity(pending ? .55 : 1);
        visible(this.pending, pending);
    }

    public void revision(String text) {
        parameterVersion.setText(text);
    }

    public void feedback(String text, boolean error) {
        feedback.setText(text);
        feedback.getStyleClass().removeAll("costs-error", "costs-success");
        feedback.getStyleClass().add(error ? "costs-error" : "costs-success");
    }

    public void formEnabled(boolean value) {
        for (Control c : List.of(commonCost, articleCost, specific, alternatives, articles))
            c.setDisable(!value);
    }

    private void layoutWorkspace(double width) {
        boolean narrow = width < 1100;
        if (workspace.getCenter() != null && narrow == compactLayout) return;
        if (workspace.getCenter() == split && !split.getDividers().isEmpty())
            divider = split.getDividerPositions()[0];
        split.getItems().clear();
        catalogueTab.setContent(null);
        detailTab.setContent(null);
        workspace.setCenter(null);
        compactLayout = narrow;
        if (narrow) {
            catalogue.setMinWidth(0);
            catalogueTab.setContent(catalogue);
            detailTab.setContent(detail);
            compact.getSelectionModel()
                    .select(
                            products.getSelectionModel().getSelectedItem() == null
                                    ? catalogueTab
                                    : detailTab);
            workspace.setCenter(compact);
        } else {
            catalogue.setMinWidth(326);
            split.getItems().setAll(catalogue, detail);
            split.setDividerPositions(divider);
            workspace.setCenter(split);
        }
    }

    private void articleIdentityColumn() {
        TableColumn<Result, Result> column = new TableColumn<>("Articolo");
        column.setMinWidth(170);
        column.setPrefWidth(180);
        column.setSortable(false);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        column.setCellFactory(
                c ->
                        new TableCell<>() {
                            protected void updateItem(Result row, boolean empty) {
                                super.updateItem(row, empty);
                                setText(null);
                                setGraphic(null);
                                setTooltip(null);
                                if (empty || row == null) return;
                                Label code =
                                        label(
                                                row.article() == null ? "—" : row.article().code(),
                                                "costs-code");
                                code.setWrapText(false);
                                String config =
                                        row.specification() == null
                                                ? "Configurazione assente"
                                                : "v"
                                                        + row.specification().version()
                                                        + (Objects.equals(
                                                                        row.article().preferredId(),
                                                                        row.specification().id())
                                                                ? " · predefinita"
                                                                : " · alternativa");
                                Label version = label(config, "products-caption");
                                HBox identity = new HBox(6, new VBox(2, code, version));
                                identity.setAlignment(Pos.CENTER_LEFT);
                                if (!row.problems().isEmpty()) {
                                    Button warning =
                                            button("⚠", "costs-products-row-warning", false);
                                    SVGPath icon = new SVGPath();
                                    icon.setContent("M11 1 L21 19 H1 Z M11 6 V12 M11 15 V16");
                                    icon.setStyle(
                                            "-fx-fill: transparent; -fx-stroke: #9b6400;"
                                                + " -fx-stroke-width: 1.8px;");
                                    warning.setText("");
                                    warning.setGraphic(icon);
                                    warning.setAccessibleText(
                                            "Dati da completare per " + code.getText());
                                    warning.getStyleClass().add("products-warning");
                                    warning.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                                    warning.setTooltip(new Tooltip(row.status()));
                                    warning.setOnAction(
                                            e -> priceInfo("Dati da completare", row.status()));
                                    identity.getChildren().add(warning);
                                }
                                setGraphic(identity);
                                setTooltip(
                                        new Tooltip(
                                                code.getText()
                                                        + "\n"
                                                        + config
                                                        + "\n"
                                                        + row.status()));
                            }
                        });
        articles.getColumns().add(column);
    }

    private void transformationColumn() {
        TableColumn<Result, Result> column = new TableColumn<>("Trasformazione €");
        column.setMinWidth(140);
        column.setPrefWidth(145);
        column.setSortable(false);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        column.setCellFactory(
                c ->
                        new TableCell<>() {
                            protected void updateItem(Result row, boolean empty) {
                                super.updateItem(row, empty);
                                setText(null);
                                setGraphic(null);
                                if (empty || row == null || row.article() == null) return;
                                boolean custom =
                                        row.parameters()
                                                .overrides()
                                                .containsKey(row.article().id());
                                Button edit =
                                        button(
                                                PriceFormats.number(row.transformation(), 2, 4)
                                                        + "  ✎",
                                                "costs-products-edit-transformation",
                                                false);
                                edit.getStyleClass().add("products-transformation-edit");
                                edit.setTooltip(
                                        new Tooltip(
                                                (custom
                                                                ? "Importo specifico"
                                                                : "Importo comune al prodotto")
                                                        + " · Clicca per modificare"));
                                edit.setAccessibleText(
                                        "Modifica trasformazione di " + row.article().code());
                                edit.setOnAction(
                                        e -> {
                                            articles.getSelectionModel().select(row);
                                            visible(overrideEditor, true);
                                            if (specific.isSelected()) articleCost.requestFocus();
                                            else specific.requestFocus();
                                        });
                                Label origin =
                                        label(custom ? "specifica" : "comune", "products-caption");
                                VBox content = new VBox(0, edit, origin);
                                content.setAlignment(Pos.CENTER_RIGHT);
                                setGraphic(content);
                            }
                        });
        articles.getColumns().add(column);
    }

    private static VBox amount(String name, Label value) {
        return new VBox(3, label(name, "products-caption"), value);
    }

    private static String euros(java.math.BigDecimal value) {
        return value == null ? "—" : PriceFormats.number(value, 2, 4) + " €";
    }

    private static void visible(Node node, boolean value) {
        node.setVisible(value);
        node.setManaged(value);
    }

    private static String colorStyle(String color) {
        return switch (Objects.toString(color, "")) {
            case "BIANCA" -> "products-white";
            case "GIALLA" -> "products-yellow";
            case "ROSA" -> "products-pink";
            case "GRIGIA" -> "products-gray";
            default -> "products-unknown";
        };
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(style);
        return label;
    }

    private static Button button(String text, String id, boolean primary) {
        Button button = new Button(text);
        button.setId(id);
        button.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        return button;
    }

    private static <T> void column(
            TableView<T> table, String title, double width, Function<T, String> text) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setMinWidth(width);
        if (title.contains("€") || title.endsWith(" g") || title.equals("Massa %"))
            column.getStyleClass().add("products-number");
        column.setSortable(false);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(text.apply(c.getValue())));
        column.setCellFactory(
                c ->
                        new TableCell<>() {
                            protected void updateItem(String value, boolean empty) {
                                super.updateItem(value, empty);
                                setText(empty ? null : value);
                                setTooltip(empty || value == null ? null : new Tooltip(value));
                            }
                        });
        table.getColumns().add(column);
    }
}

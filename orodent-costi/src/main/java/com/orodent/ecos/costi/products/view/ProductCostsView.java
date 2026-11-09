package com.orodent.ecos.costi.products.view;

import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.view.CostsShell;
import com.orodent.ecos.costi.view.PriceFormats;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

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
            exportNote = label("", "costs-muted"),
            issues = label("", "costs-muted");
    private final VBox details;

    public ProductCostsView() {
        super("Costi prodotti");
        setId("costi-products-module");
        Label title = label("Costi prodotti", "costs-title");
        VBox titleBox =
                new VBox(
                        4,
                        label("Costi e consumi › Costi prodotti", "costs-breadcrumb"),
                        title,
                        label(
                                "Costo di produzione di ogni articolo, con il dettaglio delle"
                                        + " polveri.",
                                "costs-muted"));
        exportAll.setTooltip(
                new Tooltip(
                        "Intero catalogo, incluse anagrafiche archiviate, con le configurazioni"
                                + " predefinite. Ignora i filtri di ricerca."));
        exportAll.setAccessibleText("Esporta i costi di tutti i prodotti, anche fuori dai filtri");
        export.setId("costs-products-export");
        export.getStyleClass().add("secondary-button");
        export.getItems()
                .addAll(exportProduct, exportDetail, new SeparatorMenuItem(), exportFiltered);
        HBox actions = new HBox(8, export, exportAll);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Region headingGap = new Region();
        HBox.setHgrow(headingGap, Priority.ALWAYS);
        HBox headingRow = new HBox(14, titleBox, headingGap, actions);
        headingRow.setAlignment(Pos.CENTER_LEFT);
        VBox top = new VBox(8, headingRow, exportNote);
        search.setId("costs-products-search");
        search.setPromptText("Cerca prodotto, linea o colore");
        line.setId("costs-products-line");
        line.setMaxWidth(Double.MAX_VALUE);
        products.setId("costs-products-list");
        products.setFixedCellSize(64);
        column(products, "Prodotto", 165, p -> p.code() + (p.active() ? "" : " · Archiviato"));
        column(products, "Linea", 100, p -> Objects.toString(p.lineCode(), "—"));
        products.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        products.setPlaceholder(
                label("Nessun prodotto. Le anagrafiche si creano in TON.", "costs-muted"));
        HBox paging = new HBox(7, previous, count, next);
        paging.setAlignment(Pos.CENTER_LEFT);
        VBox catalogue = new VBox(11, search, line, archived, products, paging);
        catalogue.setMinWidth(245);
        catalogue.setPrefWidth(285);
        catalogue.getStyleClass().add("costs-card");
        VBox.setVgrow(products, Priority.ALWAYS);
        commonCost.setId("costs-products-common");
        commonCost.setPromptText("Da compilare");
        commonCost.setPrefColumnCount(8);
        commonCost.setMaxWidth(160);
        articleCost.setId("costs-products-override");
        articleCost.setPromptText("€/articolo");
        articleCost.setMaxWidth(150);
        specific.setId("costs-products-specific");
        specific.setWrapText(true);
        HBox input =
                new HBox(
                        9,
                        label("Trasformazione comune", "costs-code"),
                        commonCost,
                        new Label("€/articolo"));
        input.setAlignment(Pos.CENTER_LEFT);
        FlowPane parameters = new FlowPane(10, 8, input, save, cancel);
        alternatives.setId("costs-products-alternatives");
        alternatives.setWrapText(true);
        alternatives.setTooltip(
                new Tooltip(
                        "Le configurazioni alternative rimangono righe separate: non vengono"
                                + " sommate fra loro."));
        articles.setId("costs-products-articles");
        articles.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        articles.setFixedCellSize(42);
        column(articles, "Articolo", 130, r -> r.article() == null ? "—" : r.article().code());
        column(
                articles,
                "Formato mm",
                100,
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
        column(
                articles,
                "Config.",
                92,
                r ->
                        r.specification() == null
                                ? "Assente"
                                : "v"
                                        + r.specification().version()
                                        + (Objects.equals(
                                                        r.article().preferredId(),
                                                        r.specification().id())
                                                ? " · predef."
                                                : ""));
        column(articles, "Miscela g", 92, r -> PriceFormats.number(r.grams(), 1, 3));
        column(articles, "Polveri €", 90, r -> PriceFormats.number(r.powderCost(), 2, 4));
        column(articles, "Trasform. €", 98, r -> PriceFormats.number(r.transformation(), 2, 4));
        column(articles, "Totale €", 95, r -> PriceFormats.number(r.total(), 2, 4));
        column(articles, "Stato", 105, r -> r.problems().isEmpty() ? "Completo" : "Incompleto");
        articles.setPrefHeight(190);
        articles.setMinHeight(130);
        column(materials, "Strato", 65, m -> String.valueOf(m.layer()));
        column(
                materials,
                "Polvere",
                145,
                m -> m.ingredient().code() + " · v" + m.ingredient().technicalVersion());
        column(materials, "Massa %", 85, m -> PriceFormats.number(m.ingredient().percent(), 0, 4));
        column(materials, "Da pesare g", 105, m -> PriceFormats.number(m.grams(), 1, 4));
        column(
                materials,
                "Prezzo €/kg",
                110,
                m -> m.price() == null ? "—" : PriceFormats.number(m.price().price(), 2, 8));
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
                                        + m.price().date());
        materials.setId("costs-products-materials");
        materials.setFixedCellSize(38);
        materials.setPrefHeight(210);
        materials.setMinHeight(150);
        materials.setPlaceholder(
                label("Nessun dettaglio disponibile per l'articolo selezionato.", "costs-muted"));
        details = new VBox(9, selected, provenance, new HBox(10, specific, articleCost), issues);
        details.setPadding(new Insets(10, 0, 0, 0));
        TabPane tabs = new TabPane();
        tabs.setId("costs-products-tabs");
        VBox rows = new VBox(10, articles);
        VBox.setVgrow(articles, Priority.ALWAYS);
        VBox breakdown =
                new VBox(
                        9,
                        label(
                                "Polveri dell'articolo selezionato · quantità inclusa"
                                        + " maggiorazione",
                                "costs-muted"),
                        materials);
        VBox.setVgrow(materials, Priority.ALWAYS);
        for (Tab tab :
                List.of(
                        new Tab("Articoli e costi", rows),
                        new Tab("Dettaglio polveri", breakdown))) {
            tab.setClosable(false);
            tabs.getTabs().add(tab);
        }
        tabs.setMinHeight(180);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        VBox detail =
                new VBox(
                        8,
                        heading,
                        productInfo,
                        parameters,
                        parameterVersion,
                        label(
                                "Perdite di lavorazione: +0,5% · Fattore 1,005, applicato una sola"
                                        + " volta",
                                "costs-muted"),
                        alternatives,
                        tabs,
                        details);
        detail.getStyleClass().add("costs-card");
        detail.setMinWidth(400);
        ScrollPane scroll = new ScrollPane(detail);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setMinWidth(420);
        scroll.getStyleClass().add("costs-detail-scroll");
        SplitPane split = new SplitPane(catalogue, scroll);
        split.setDividerPositions(.28);
        split.getStyleClass().add("costs-split");
        VBox.setVgrow(split, Priority.ALWAYS);
        HBox toolbar = new HBox(12, refresh, snapshotDate);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        VBox page = new VBox(12, top, toolbar, split, feedback);
        page.setPadding(new Insets(18, 24, 14, 24));
        setCenter(page);
        setBottom(
                label(
                        "Valori per un articolo · Configurazioni TON approvate · Nessun movimento"
                                + " di magazzino",
                        "costs-footer"));
        feedback.setId("costs-products-feedback");
        provenance.setId("costs-products-provenance");
        issues.setId("costs-products-issues");
        parameterVersion.setId("costs-products-parameter-version");
        exportNote.setText(
                "Esporta tutti i prezzi: intero catalogo, incluse archiviate, con configurazioni"
                        + " predefinite.");
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
        commonCost.setText(PriceFormats.edit(parameters.commonCost()));
        parameterVersion.setText(
                "Parametri di trasformazione · "
                        + (parameters.version() == 0
                                ? "non ancora salvati"
                                : "revisione " + parameters.version()));
    }

    public void results(List<Result> results, Long keepArticle, Long keepSpec) {
        articles.getItems().setAll(results);
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
        snapshotDate.setText(text);
    }

    public void costsPending(boolean pending) {
        articles.setOpacity(pending ? .55 : 1);
        materials.setOpacity(pending ? .55 : 1);
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
        column.setMinWidth(60);
        column.setSortable(false);
        column.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(text.apply(c.getValue())));
        table.getColumns().add(column);
    }
}

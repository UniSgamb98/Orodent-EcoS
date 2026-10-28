package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

public final class CatalogListView extends VBox {
    private final TextField search, diameter, height;
    private final CheckBox archived = new CheckBox("Mostra anche archiviati");
    private final CatalogTable table;
    private final Button create, open, refresh, previous, next;
    private final Label result = new Label(), feedback;
    private final CatalogLookup line, product;

    public CatalogListView(CatalogKind kind) {
        super(12);
        setMinWidth(0);
        getStyleClass().add("ton-catalogue-page");
        setId("ton-" + kind.pluralId() + "-list");
        String base = "ton-" + kind.pluralId();
        search = CatalogUi.field(base + "-search", "Cerca codice o descrizione");
        diameter = CatalogUi.field(base + "-diameter", "Tutti");
        height = CatalogUi.field(base + "-height", "Tutte");
        create =
                CatalogUi.button(
                        "Nuov" + (kind == CatalogKind.LINES ? "a linea" : "o " + kind.singular()),
                        "ton-" + kind.id() + "-new",
                        true);
        open = CatalogUi.button("Apri scheda", "ton-" + kind.id() + "-edit", true);
        open.setDisable(true);
        refresh = CatalogUi.button("Aggiorna", base + "-refresh", false);
        previous = CatalogUi.button("←", base + "-previous", false);
        next = CatalogUi.button("→", base + "-next", false);
        previous.setAccessibleText("Pagina precedente");
        next.setAccessibleText("Pagina successiva");
        Label heading = new Label(kind.title());
        heading.getStyleClass().add("ton-page-heading");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox title = new HBox(12, heading, gap, create);
        title.setAlignment(Pos.CENTER_LEFT);
        Label intro =
                CatalogUi.hint(
                        switch (kind) {
                            case LINES -> "Tecnologie del laboratorio e prodotti collegati.";
                            case PRODUCTS -> "Una linea e un colore commerciale per ogni prodotto.";
                            case ARTICLES ->
                                    "I formati degli articoli, con linea e colore ereditati dal"
                                        + " prodotto.";
                        });
        HBox query = new HBox(10, search, refresh);
        HBox.setHgrow(search, Priority.ALWAYS);
        line =
                kind == CatalogKind.LINES
                        ? null
                        : new CatalogLookup(CatalogKind.LINES, base + "-line", true);
        product =
                kind == CatalogKind.ARTICLES
                        ? new CatalogLookup(CatalogKind.PRODUCTS, base + "-product", true)
                        : null;
        var filterFields = new ArrayList<javafx.scene.Node>();
        if (line != null) filterFields.add(CatalogUi.field("Linea", line));
        if (product != null) {
            filterFields.add(CatalogUi.field("Prodotto", product));
            filterFields.add(CatalogUi.field("Diametro · mm", diameter));
            filterFields.add(CatalogUi.field("Altezza · mm", height));
        }
        archived.setId(base + "-include-inactive");
        table = new CatalogTable(kind, base + "-table");
        VBox.setVgrow(table, Priority.ALWAYS);
        feedback = CatalogUi.feedback(base + "-load-error");
        feedback.getStyleClass().add("ton-error-feedback");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox pages = new HBox(10, result, spacer, previous, next);
        pages.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(title, intro, query);
        if (!filterFields.isEmpty()) {
            TitledPane filters =
                    new TitledPane(
                            "Filtri",
                            CatalogUi.fields(filterFields.toArray(javafx.scene.Node[]::new)));
            filters.setExpanded(false);
            filters.setId(base + "-filters");
            getChildren().add(filters);
        }
        getChildren().addAll(archived, table, pages, open, feedback);
    }

    public Filter filter() {
        return new Filter(
                search.getText(),
                archived.isSelected(),
                line == null ? null : line.id(),
                product == null ? null : product.id(),
                CatalogUi.decimal(diameter.getText(), "Diametro"),
                CatalogUi.decimal(height.getText(), "Altezza"));
    }

    public void loading() {
        table.setDisable(true);
        open.setDisable(true);
        refresh.setDisable(true);
        previous.setDisable(true);
        next.setDisable(true);
        result.setText("Caricamento…");
        feedback.setText("");
    }

    public void show(Page page) {
        table.getItems().setAll(page.rows());
        table.setDisable(false);
        refresh.setDisable(false);
        previous.setDisable(page.offset() == 0);
        next.setDisable(!page.hasNext());
        open.setDisable(table.getSelectionModel().getSelectedItem() == null);
        feedback.setText("");
        result.setText(
                page.total() == 0
                        ? "0 risultati"
                        : (page.offset() + 1)
                                + "–"
                                + (page.offset() + page.rows().size())
                                + " di "
                                + page.total());
    }

    public void error(String text) {
        feedback.setText(text);
        table.setDisable(false);
        refresh.setDisable(false);
        open.setDisable(table.getSelectionModel().getSelectedItem() == null);
        result.setText("Elenco non aggiornato");
    }

    public CatalogLookup line() {
        return line;
    }

    public CatalogLookup product() {
        return product;
    }

    public TextField search() {
        return search;
    }

    public TextField diameter() {
        return diameter;
    }

    public TextField height() {
        return height;
    }

    public CheckBox archived() {
        return archived;
    }

    public CatalogTable table() {
        return table;
    }

    public Button create() {
        return create;
    }

    public Button open() {
        return open;
    }

    public Button refresh() {
        return refresh;
    }

    public Button previous() {
        return previous;
    }

    public Button next() {
        return next;
    }
}

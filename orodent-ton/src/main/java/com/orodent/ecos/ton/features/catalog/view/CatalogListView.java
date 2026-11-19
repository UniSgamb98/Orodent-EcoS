package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;
import com.orodent.ecos.ton.features.technical.view.TonListView;

import javafx.scene.control.*;

import java.util.*;

public final class CatalogListView extends TonListView<Entry> {
    private final TextField diameter, height;
    private final CheckBox archived = new CheckBox("Mostra anche archiviati");
    private final Button bulk;
    private final CatalogLookup line, product;

    public CatalogListView(CatalogKind kind) {
        super(
                "ton-" + kind.pluralId(),
                switch (kind) {
                    case LINES -> "Tecnologie del laboratorio e prodotti collegati.";
                    case PRODUCTS -> "Una linea e un colore commerciale per ogni prodotto.";
                    case ARTICLES ->
                            "I formati degli articoli, con linea e colore ereditati dal prodotto.";
                },
                "Cerca codice o descrizione",
                "Nuov" + (kind == CatalogKind.LINES ? "a linea" : "o " + kind.singular()),
                new CatalogTable(kind, "ton-" + kind.pluralId() + "-table"));
        String base = "ton-" + kind.pluralId();
        create().setId("ton-" + kind.id() + "-new");
        feedback().setId(base + "-load-error");
        diameter = CatalogUi.field(base + "-diameter", "Tutti");
        height = CatalogUi.field(base + "-height", "Tutte");
        bulk =
                kind == CatalogKind.LINES
                        ? null
                        : CatalogUi.button("Inserimento multiplo…", base + "-bulk", false);
        if (bulk != null) addAction(bulk);
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
        if (!filterFields.isEmpty()) {
            filters(
                    base + "-filters",
                    CatalogUi.fields(filterFields.toArray(javafx.scene.Node[]::new)));
        }
        options(archived);
    }

    public Filter filter() {
        return new Filter(
                search().getText(),
                archived.isSelected(),
                line == null ? null : line.id(),
                product == null ? null : product.id(),
                CatalogUi.decimal(diameter.getText(), "Diametro"),
                CatalogUi.decimal(height.getText(), "Altezza"));
    }

    public void show(Page page) {
        showRows(page.rows());
    }

    public CatalogLookup line() {
        return line;
    }

    public CatalogLookup product() {
        return product;
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
        return (CatalogTable) super.table();
    }

    public Button bulk() {
        return bulk;
    }
}

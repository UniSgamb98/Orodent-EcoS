package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;

import java.util.function.Function;

public final class CatalogTable extends TableView<Entry> {
    public CatalogTable(CatalogKind kind, String id) {
        setId(id);
        setMinHeight(180);
        setPrefHeight(330);
        setMinWidth(0);
        column("Codice", Entry::code, 150);
        switch (kind) {
            case LINES -> {
                column("Nome / tecnologia", e -> e.name(), 210);
                column("Materia prima", e -> ((Line) e).material(), 100);
                column("Prodotti", e -> Integer.toString(((Line) e).products()), 80);
            }
            case PRODUCTS -> {
                column(
                        "Linea",
                        e -> {
                            var l = ((Product) e).line();
                            return l == null ? "Da specificare" : l.code();
                        },
                        140);
                column("Colore", e -> ((Product) e).colorLabel(), 100);
                column("Descrizione", Entry::name, 180);
                column("Articoli", e -> Integer.toString(((Product) e).articles()), 80);
            }
            case ARTICLES -> {
                column(
                        "Linea",
                        e ->
                                ((Article) e).product().lineCode() == null
                                        ? "Da specificare"
                                        : ((Article) e).product().lineCode(),
                        120);
                column(
                        "Colore",
                        e ->
                                com.orodent.ecos.ton.features.catalog.model.CatalogModels
                                        .colorLabelOf(((Article) e).product().color()),
                        90);
                column("Ø · mm", e -> CatalogUi.number(((Article) e).diameter()), 75);
                column("H · mm", e -> CatalogUi.number(((Article) e).height()), 75);
                column("Produzione", e -> ((Article) e).specificationLabel(), 195);
            }
        }
        column("Stato", Entry::status, 135);
        setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        setPlaceholder(new Label("Nessun risultato."));
    }

    private void column(String title, Function<Entry, String> value, int width) {
        TableColumn<Entry, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        getColumns().add(column);
    }
}

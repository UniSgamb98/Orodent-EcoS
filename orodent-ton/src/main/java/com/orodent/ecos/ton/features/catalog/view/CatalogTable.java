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
        column("Codice", Entry::code);
        switch (kind) {
            case LINES -> {
                column("Nome / tecnologia", e -> e.name());
                column("Materia prima", e -> ((Line) e).material());
                column("Prodotti", e -> Integer.toString(((Line) e).products()));
            }
            case PRODUCTS -> {
                column(
                        "Linea",
                        e -> {
                            var l = ((Product) e).line();
                            return l == null ? "Da specificare" : l.code();
                        });
                column("Colore", e -> ((Product) e).colorLabel());
                column("Descrizione", Entry::name);
                column("Articoli", e -> Integer.toString(((Product) e).articles()));
            }
            case ARTICLES -> {
                column(
                        "Linea",
                        e ->
                                ((Article) e).product().lineCode() == null
                                        ? "Da specificare"
                                        : ((Article) e).product().lineCode());
                column(
                        "Colore",
                        e ->
                                com.orodent.ecos.ton.features.catalog.model.CatalogModels
                                        .colorLabelOf(((Article) e).product().color()));
                column("Ø · mm", e -> CatalogUi.number(((Article) e).diameter()));
                column("H · mm", e -> CatalogUi.number(((Article) e).height()));
                column("Produzione", e -> ((Article) e).specificationLabel());
            }
        }
        column("Stato", Entry::status);
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        setPlaceholder(new Label("Nessun risultato."));
    }

    private void column(String title, Function<Entry, String> value) {
        TableColumn<Entry, String> column = new TableColumn<>(title);
        column.setPrefWidth(150);
        column.setMinWidth(60);
        column.setSortable(false);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        getColumns().add(column);
    }
}

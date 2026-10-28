package com.orodent.ecos.ton.features.compositions.view;

import com.orodent.ecos.ton.features.compositions.model.Compositions.*;
import com.orodent.ecos.ton.features.technical.view.*;

import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class CompositionListView extends VBox {
    private final TextField search =
            TechnicalUi.input("ton-composition-search", "Cerca prodotto, linea o colore");
    private final ComboBox<Filter> filter = new ComboBox<>();
    private final CheckBox archived = new CheckBox("Mostra anche prodotti archiviati");
    private final Button create =
            TechnicalUi.button("+ Nuova composizione", "ton-composition-new", true);
    private final TechnicalTablePage<Summary> results =
            new TechnicalTablePage<>("ton-compositions", "Apri scheda");

    CompositionListView() {
        super(16);
        setId("ton-composition-list");
        setMinWidth(0);
        getStyleClass().add("ton-comp-page");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var heading =
                new HBox(15, TechnicalUi.label("Composizioni", "ton-page-heading"), spacer, create);
        heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        filter.setId("ton-composition-filter");
        filter.getItems().setAll(Filter.values());
        filter.setValue(Filter.ALL);
        filter.setAccessibleText("Filtra stato della composizione");
        archived.setId("ton-composition-archived");
        var filters = new HBox(12, search, filter);
        HBox.setHgrow(search, Priority.ALWAYS);
        TechnicalUi.column(results.table(), "Prodotto", 180, Summary::code);
        TechnicalUi.column(results.table(), "Descrizione", 180, Summary::name);
        TechnicalUi.column(results.table(), "Linea", 95, Summary::lineCode);
        TechnicalUi.column(
                results.table(),
                "Colore",
                90,
                r ->
                        r.color() == null
                                ? "Da specificare"
                                : "SENZA_COLORE".equals(r.color()) ? "Senza colore" : r.color());
        TechnicalUi.column(results.table(), "Strati", 70, r -> "" + r.layers());
        TechnicalUi.column(
                results.table(),
                "Approvata",
                100,
                r -> r.approvedVersion() == null ? "—" : "v" + r.approvedVersion());
        TechnicalUi.column(
                results.table(),
                "Bozza",
                80,
                r -> r.draftVersion() == null ? "—" : "v" + r.draftVersion());
        VBox.setVgrow(results, Priority.ALWAYS);
        getChildren()
                .addAll(
                        heading,
                        TechnicalUi.label(
                                "Una ricetta per prodotto, con revisioni precise delle polveri in"
                                        + " ogni strato.",
                                "muted-label"),
                        filters,
                        archived,
                        results);
    }

    public TextField search() {
        return search;
    }

    public ComboBox<Filter> filter() {
        return filter;
    }

    public CheckBox archived() {
        return archived;
    }

    public Button create() {
        return create;
    }

    public TechnicalTablePage<Summary> results() {
        return results;
    }
}

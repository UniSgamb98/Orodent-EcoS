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
            TechnicalTablePage.catalogue("ton-compositions", "Apri scheda");

    CompositionListView() {
        super(16);
        setId("ton-composition-list");
        setMinWidth(0);
        getStyleClass().addAll("ton-comp-page", "ton-catalogue-list");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var intro =
                TechnicalUi.label(
                        "Una ricetta per prodotto, con revisioni precise delle polveri in ogni"
                                + " strato.",
                        "muted-label");
        HBox.setHgrow(intro, Priority.ALWAYS);
        var heading = new HBox(15, intro, spacer, create);
        heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        filter.setId("ton-composition-filter");
        filter.getItems().setAll(Filter.values());
        filter.setValue(Filter.ALL);
        filter.setAccessibleText("Filtra stato della composizione");
        archived.setId("ton-composition-archived");
        search.setMinWidth(0);
        filter.setMinWidth(Region.USE_PREF_SIZE);
        results.refresh().setMinWidth(Region.USE_PREF_SIZE);
        create.setMinWidth(Region.USE_PREF_SIZE);
        var filters = new HBox(12, search, filter, results.refresh());
        filters.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
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
        results.table()
                .getColumns()
                .forEach(
                        column -> {
                            column.setPrefWidth(150);
                            column.setMinWidth(60);
                        });
        VBox.setVgrow(results, Priority.ALWAYS);
        VBox card = new VBox(12, filters, archived, results);
        card.getStyleClass().add("ton-list-card");
        card.setMinSize(0, 0);
        VBox.setVgrow(card, Priority.ALWAYS);
        getChildren().addAll(heading, card);
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

package com.orodent.ecos.ton.features.compositions.view;

import com.orodent.ecos.ton.features.compositions.model.Compositions.*;
import com.orodent.ecos.ton.features.technical.view.*;

import javafx.scene.control.*;

public final class CompositionListView extends TonListView<Summary> {
    private final ComboBox<Filter> filter = new ComboBox<>();
    private final CheckBox archived = new CheckBox("Mostra anche prodotti archiviati");

    CompositionListView() {
        super(
                "ton-compositions",
                "Una ricetta per prodotto, con revisioni precise delle polveri in ogni strato.",
                "Cerca prodotto, linea o colore",
                "+ Nuova composizione");
        setId("ton-composition-list");
        search().setId("ton-composition-search");
        create().setId("ton-composition-new");
        feedback().setId("ton-compositions-status");
        filter.setId("ton-composition-filter");
        filter.getItems().setAll(Filter.values());
        filter.setValue(Filter.ALL);
        filter.setAccessibleText("Filtra stato della composizione");
        filters("ton-composition-filters", TechnicalUi.field("Stato", filter));
        archived.setId("ton-composition-archived");
        options(archived);
        TechnicalUi.column(table(), "Prodotto", 180, Summary::code);
        TechnicalUi.column(table(), "Descrizione", 180, Summary::name);
        TechnicalUi.column(table(), "Linea", 95, Summary::lineCode);
        TechnicalUi.column(
                table(),
                "Colore",
                90,
                r ->
                        r.color() == null
                                ? "Da specificare"
                                : "SENZA_COLORE".equals(r.color()) ? "Senza colore" : r.color());
        TechnicalUi.column(table(), "Strati", 70, r -> "" + r.layers());
        TechnicalUi.column(
                table(),
                "Approvata",
                100,
                r -> r.approvedVersion() == null ? "—" : "v" + r.approvedVersion());
        TechnicalUi.column(
                table(), "Bozza", 80, r -> r.draftVersion() == null ? "—" : "v" + r.draftVersion());
    }

    public ComboBox<Filter> filter() {
        return filter;
    }

    public CheckBox archived() {
        return archived;
    }
}

package com.orodent.ecos.ton.features.diskmodels.view;

import com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;
import com.orodent.ecos.ton.features.technical.view.TonListView;

import javafx.scene.control.*;

public final class DiskModelListView extends TonListView<Summary> {
    private final ComboBox<Filter> filter = new ComboBox<>();

    DiskModelListView() {
        super(
                "ton-models",
                "Geometria, strati e parametri di pressatura. Una riga per modello, con tutte le"
                        + " versioni nella scheda.",
                "Cerca per codice modello",
                "+ Nuovo modello");
        setId("ton-model-list");
        search().setId("ton-model-search");
        create().setId("ton-model-new");
        feedback().setId("ton-models-status");
        filter.setId("ton-model-filter");
        filter.getItems().setAll(Filter.values());
        filter.setValue(Filter.ALL);
        filter.setAccessibleText("Filtra stato del modello");
        filters("ton-model-filters", TechnicalUi.field("Stato", filter));
        TechnicalUi.column(table(), "Modello", 180, Summary::code);
        TechnicalUi.column(
                table(),
                "Diametri articolo",
                160,
                r ->
                        r.articleDiameters().isEmpty()
                                ? "Da associare · stampo "
                                        + TechnicalUi.number(r.diameter())
                                        + " mm"
                                : r.articleDiameters().stream()
                                                .map(TechnicalUi::number)
                                                .collect(java.util.stream.Collectors.joining(" / "))
                                        + " mm");
        TechnicalUi.column(table(), "Strati", 70, r -> "" + r.layers());
        TechnicalUi.column(
                table(),
                "Versione approvata",
                170,
                r -> r.approvedVersion() == null ? "—" : "v" + r.approvedVersion());
        TechnicalUi.column(
                table(),
                "Bozza",
                100,
                r -> r.draftVersion() == null ? "—" : "v" + r.draftVersion());
    }

    public ComboBox<Filter> filter() {
        return filter;
    }
}

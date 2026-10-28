package com.orodent.ecos.ton.features.diskmodels.view;

import com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;
import com.orodent.ecos.ton.features.technical.view.TechnicalTablePage;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class DiskModelListView extends VBox {
    private final TextField search =
            TechnicalUi.input("ton-model-search", "Cerca per codice modello");
    private final ComboBox<Filter> filter = new ComboBox<>();
    private final TechnicalTablePage<Summary> results =
            new TechnicalTablePage<>("ton-models", "Apri scheda");
    private final Button create = TechnicalUi.button("+ Nuovo modello", "ton-model-new", true);

    DiskModelListView() {
        super(18);
        setId("ton-model-list");
        getStyleClass().add("ton-model-page");
        filter.setId("ton-model-filter");
        filter.getItems().setAll(Filter.values());
        filter.setValue(Filter.ALL);
        filter.setAccessibleText("Filtra stato del modello");
        var heading = new HBox(15, TechnicalUi.label("Modelli disco", "ton-page-heading"), create);
        heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        var filters = new HBox(12, search, filter);
        HBox.setHgrow(search, Priority.ALWAYS);
        TechnicalUi.column(results.table(), "Modello", 180, Summary::code);
        TechnicalUi.column(
                results.table(),
                "Diametro pressatura",
                160,
                r -> TechnicalUi.number(r.diameter()) + " mm");
        TechnicalUi.column(results.table(), "Strati", 70, r -> "" + r.layers());
        TechnicalUi.column(
                results.table(),
                "Versione approvata",
                170,
                r -> r.approvedVersion() == null ? "—" : "v" + r.approvedVersion());
        TechnicalUi.column(
                results.table(),
                "Bozza",
                100,
                r -> r.draftVersion() == null ? "—" : "v" + r.draftVersion());
        VBox.setVgrow(results, Priority.ALWAYS);
        getChildren()
                .addAll(
                        heading,
                        TechnicalUi.label(
                                "Geometria, strati e parametri di pressatura. Una riga per modello,"
                                        + " con tutte le versioni nella scheda.",
                                "muted-label"),
                        filters,
                        results);
    }

    public TextField search() {
        return search;
    }

    public ComboBox<Filter> filter() {
        return filter;
    }

    public TechnicalTablePage<Summary> results() {
        return results;
    }

    public Button create() {
        return create;
    }
}

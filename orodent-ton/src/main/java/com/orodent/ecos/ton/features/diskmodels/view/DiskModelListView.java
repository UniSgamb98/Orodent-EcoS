package com.orodent.ecos.ton.features.diskmodels.view;

import com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class DiskModelListView extends VBox {
    private final TextField search = DiskUi.input("ton-model-search", "Cerca per codice modello");
    private final ComboBox<Filter> filter = new ComboBox<>();
    private final ModelTablePage<Summary> results =
            new ModelTablePage<>("ton-models", "Apri scheda");
    private final Button create = DiskUi.button("+ Nuovo modello", "ton-model-new", true);

    DiskModelListView() {
        super(18);
        setId("ton-model-list");
        getStyleClass().add("ton-model-page");
        filter.setId("ton-model-filter");
        filter.getItems().setAll(Filter.values());
        filter.setValue(Filter.ALL);
        filter.setAccessibleText("Filtra stato del modello");
        var heading = new HBox(15, DiskUi.label("Modelli disco", "ton-page-heading"), create);
        heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        var filters = new HBox(12, search, filter);
        HBox.setHgrow(search, Priority.ALWAYS);
        DiskUi.column(results.table(), "Modello", 180, Summary::code);
        DiskUi.column(
                results.table(),
                "Diametro pressatura",
                160,
                r -> DiskUi.number(r.diameter()) + " mm");
        DiskUi.column(results.table(), "Strati", 70, r -> "" + r.layers());
        DiskUi.column(
                results.table(),
                "Versione approvata",
                170,
                r -> r.approvedVersion() == null ? "—" : "v" + r.approvedVersion());
        DiskUi.column(
                results.table(),
                "Bozza",
                100,
                r -> r.draftVersion() == null ? "—" : "v" + r.draftVersion());
        VBox.setVgrow(results, Priority.ALWAYS);
        getChildren()
                .addAll(
                        heading,
                        DiskUi.label(
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

    public ModelTablePage<Summary> results() {
        return results;
    }

    public Button create() {
        return create;
    }
}

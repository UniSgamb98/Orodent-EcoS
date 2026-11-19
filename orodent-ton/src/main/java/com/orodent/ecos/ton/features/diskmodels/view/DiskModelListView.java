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
            TechnicalTablePage.catalogue("ton-models", "Apri scheda");
    private final Button create = TechnicalUi.button("+ Nuovo modello", "ton-model-new", true);

    DiskModelListView() {
        super(18);
        setId("ton-model-list");
        getStyleClass().addAll("ton-model-page", "ton-catalogue-list");
        filter.setId("ton-model-filter");
        filter.getItems().setAll(Filter.values());
        filter.setValue(Filter.ALL);
        filter.setAccessibleText("Filtra stato del modello");
        var intro =
                TechnicalUi.label(
                        "Geometria, strati e parametri di pressatura. Una riga per modello, con"
                                + " tutte le versioni nella scheda.",
                        "muted-label");
        HBox.setHgrow(intro, Priority.ALWAYS);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var heading = new HBox(15, intro, spacer, create);
        heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        search.setMinWidth(0);
        filter.setMinWidth(Region.USE_PREF_SIZE);
        results.refresh().setMinWidth(Region.USE_PREF_SIZE);
        create.setMinWidth(Region.USE_PREF_SIZE);
        var filters = new HBox(12, search, filter, results.refresh());
        filters.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
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
        results.table()
                .getColumns()
                .forEach(
                        column -> {
                            column.setPrefWidth(150);
                            column.setMinWidth(60);
                        });
        VBox.setVgrow(results, Priority.ALWAYS);
        VBox card = new VBox(12, filters, results);
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

    public TechnicalTablePage<Summary> results() {
        return results;
    }

    public Button create() {
        return create;
    }
}

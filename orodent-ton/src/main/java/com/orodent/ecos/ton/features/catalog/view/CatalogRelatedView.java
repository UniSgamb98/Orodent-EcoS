package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class CatalogRelatedView extends VBox {
    private final CatalogTable table;
    private final Button create, bulk;
    private final Label feedback;

    public CatalogRelatedView(CatalogKind owner) {
        super(12);
        getStyleClass().add("ton-catalogue-tab");
        CatalogKind child = owner.child();
        String base = "ton-" + owner.id() + "-children";
        table = new CatalogTable(child, base + "-table");
        VBox.setVgrow(table, Priority.ALWAYS);
        create = CatalogUi.button("+ Nuovo " + child.singular(), base + "-new", true);
        bulk =
                CatalogUi.button(
                        child == CatalogKind.PRODUCTS ? "Crea più prodotti…" : "Crea più articoli…",
                        base + "-bulk",
                        false);
        feedback = CatalogUi.feedback(base + "-feedback");
        feedback.getStyleClass().add("ton-error-feedback");
        getChildren().setAll(new FlowPane(10, 8, create, bulk), table, feedback);
    }

    public void reset(boolean saved, boolean available) {
        create.setDisable(!saved || !available);
        bulk.setDisable(!saved || !available);
        table.getItems().clear();
        feedback.setText("");
    }

    public void loading() {
        table.setDisable(true);
    }

    public void show(Page p) {
        table.getItems().setAll(p.rows());
        table.setDisable(false);
        feedback.setText("");
    }

    public void error(String message) {
        feedback.setText(message);
        table.setDisable(false);
    }

    public CatalogTable table() {
        return table;
    }

    public Button bulk() {
        return bulk;
    }

    public Button create() {
        return create;
    }
}

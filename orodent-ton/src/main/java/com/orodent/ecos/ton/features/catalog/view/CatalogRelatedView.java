package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class CatalogRelatedView extends VBox {
    private final CatalogTable table;
    private final Button create, open, previous, next;
    private final Label result = new Label(), feedback;

    public CatalogRelatedView(CatalogKind owner) {
        super(12);
        getStyleClass().add("ton-catalogue-tab");
        CatalogKind child = owner.child();
        String base = "ton-" + owner.id() + "-children";
        table = new CatalogTable(child, base + "-table");
        VBox.setVgrow(table, Priority.ALWAYS);
        create = CatalogUi.button("+ Nuovo " + child.singular(), base + "-new", true);
        open = CatalogUi.button("Apri scheda selezionata", base + "-open", false);
        open.setDisable(true);
        previous = CatalogUi.button("←", base + "-previous", false);
        next = CatalogUi.button("→", base + "-next", false);
        previous.setAccessibleText("Collegamenti precedenti");
        next.setAccessibleText("Collegamenti successivi");
        feedback = CatalogUi.feedback(base + "-feedback");
        feedback.getStyleClass().add("ton-error-feedback");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox pages = new HBox(10, result, spacer, previous, next);
        pages.setAlignment(Pos.CENTER_LEFT);
        getChildren()
                .setAll(
                        CatalogUi.hint(
                                "Gli elementi vengono creati con il collegamento a questa scheda"
                                    + " già selezionato."),
                        create,
                        table,
                        pages,
                        open,
                        feedback);
        table.getSelectionModel()
                .selectedItemProperty()
                .addListener((o, a, b) -> open.setDisable(b == null));
    }

    public void reset(boolean saved, boolean available) {
        create.setDisable(!saved || !available);
        table.getItems().clear();
        previous.setDisable(true);
        next.setDisable(true);
        result.setText(saved ? "" : "Salva prima l'anagrafica.");
        feedback.setText("");
    }

    public void loading() {
        table.setDisable(true);
        open.setDisable(true);
        result.setText("Caricamento…");
    }

    public void show(Page p) {
        table.getItems().setAll(p.rows());
        table.setDisable(false);
        open.setDisable(table.getSelectionModel().getSelectedItem() == null);
        previous.setDisable(p.offset() == 0);
        next.setDisable(!p.hasNext());
        result.setText(
                p.total() == 0
                        ? "Nessun collegamento"
                        : (p.offset() + 1)
                                + "–"
                                + (p.offset() + p.rows().size())
                                + " di "
                                + p.total());
        feedback.setText("");
    }

    public void error(String message) {
        feedback.setText(message);
        table.setDisable(false);
    }

    public CatalogTable table() {
        return table;
    }

    public Button create() {
        return create;
    }

    public Button open() {
        return open;
    }

    public Button previous() {
        return previous;
    }

    public Button next() {
        return next;
    }
}

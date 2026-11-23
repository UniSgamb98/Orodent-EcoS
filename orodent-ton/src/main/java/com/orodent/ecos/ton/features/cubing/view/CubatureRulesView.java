package com.orodent.ecos.ton.features.cubing.view;

import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Entry page for cubature rules; formulas and their parameters are defined in the next step. */
public final class CubatureRulesView extends VBox {
    public CubatureRulesView() {
        super(18);
        setId("ton-cubature-rules-page");
        setMinSize(0, 0);
        setPadding(new Insets(22, 26, 28, 26));
        getStyleClass().add("ton-landing-page");

        var empty =
                new VBox(
                        12,
                        TechnicalUi.label(
                                "Nessuna regola di cubatura configurata.", "ton-card-title"),
                        TechnicalUi.label(
                                "Le regole definiranno come elaborare le letture del micrometro e"
                                    + " della bilancia.",
                                "ton-description"),
                        TechnicalUi.label(
                                "Il metodo di calcolo sarà richiamato dalle Specifiche di"
                                    + " produzione del prodotto.",
                                "ton-description"));
        empty.setId("ton-cubature-rules-empty");
        empty.setAlignment(Pos.CENTER);
        empty.getStyleClass().add("ton-feature-card");
        VBox.setVgrow(empty, Priority.ALWAYS);

        getChildren().addAll(TechnicalUi.label("Regole di cubatura", "ton-page-heading"), empty);
    }
}

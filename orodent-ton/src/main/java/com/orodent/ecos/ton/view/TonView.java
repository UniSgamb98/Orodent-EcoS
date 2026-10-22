package com.orodent.ecos.ton.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/** Entry page for the Ton component, embedded in the ecosystem's main window. */
public final class TonView extends BorderPane {
    private final Button homeButton = new Button("Torna alla Home");

    public TonView() {
        setId("ton-view");
        getStyleClass().add("page");
        Label title = new Label("Ton");
        title.getStyleClass().add("startup-title");
        Label description = new Label("Le funzionalità Ton saranno disponibili in questa sezione.");
        description.setWrapText(true);
        description.getStyleClass().add("muted-label");
        homeButton.setId("ton-home-button");
        homeButton.getStyleClass().add("nav-button");
        VBox content = new VBox(18, title, description, homeButton);
        content.setPadding(new Insets(28, 34, 36, 34));
        setCenter(content);
    }

    public Button homeButton() { return homeButton; }
}

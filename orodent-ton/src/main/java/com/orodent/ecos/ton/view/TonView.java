package com.orodent.ecos.ton.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** The module owns its pages, while EcoS owns the window and shared services. */
public final class TonView extends BorderPane {
    private final Button homeButton = new Button("Home Orodent-EcoS");
    private final Button overviewButton = new Button("Panoramica TON");
    private final Button productsButton = new Button("Apri catalogo prodotti");
    private final Label title = new Label("TON · Laboratorio");
    private final VBox overview;

    public TonView() {
        getStylesheets().add(java.util.Objects.requireNonNull(getClass().getResource("/ton/css/catalog.css")).toExternalForm());
        setId("ton-view"); getStyleClass().add("page"); getStyleClass().add("ton-module");
        homeButton.setId("ton-home-button"); homeButton.getStyleClass().add("primary-button");
        overviewButton.setId("ton-overview-button"); overviewButton.getStyleClass().add("nav-button");
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(18, homeButton, title, spacer, overviewButton);
        header.setAlignment(Pos.CENTER_LEFT); header.getStyleClass().add("app-header");
        setTop(header);

        Label welcome = new Label("Laboratorio TON"); welcome.getStyleClass().add("startup-title");
        Label introduction = new Label("Dati tecnici e strumenti per il laboratorio e la produzione.");
        introduction.getStyleClass().add("muted-label"); introduction.setWrapText(true);
        Label productTitle = new Label("Prodotti tecnici"); productTitle.getStyleClass().add("card-title");
        Label description = new Label("Gestisci le famiglie e i materiali di riferimento del laboratorio, come ZR A2. "
                + "Questi codici sono distinti dagli articoli di vendita.");
        description.setWrapText(true); description.getStyleClass().add("muted-label");
        productsButton.setId("ton-products-button"); productsButton.getStyleClass().add("primary-button");
        VBox card = new VBox(16, productTitle, description, productsButton);
        card.getStyleClass().add("info-card"); card.setMaxWidth(540);
        overview = new VBox(18, welcome, introduction, card);
        overview.setPadding(new Insets(28, 34, 36, 34));
        showOverview();
    }

    public void showOverview() {
        title.setText("TON · Laboratorio"); overviewButton.setVisible(false); overviewButton.setManaged(false);
        setCenter(overview);
    }

    public void showProducts(Parent products) {
        title.setText("Prodotti tecnici"); overviewButton.setVisible(true); overviewButton.setManaged(true);
        setCenter(products);
    }

    public Button homeButton() { return homeButton; }
    public Button overviewButton() { return overviewButton; }
    public Button productsButton() { return productsButton; }
}

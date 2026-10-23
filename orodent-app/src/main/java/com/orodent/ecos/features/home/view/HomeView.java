package com.orodent.ecos.features.home.view;

import com.orodent.ecos.database.DatabaseMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class HomeView extends BorderPane {
    private final Button tonButton = new Button("Apri Ton");
    private final Button homeButton = new Button("Home");

    public HomeView(DatabaseMode mode) {
        getStyleClass().add("page");
        Label brand = new Label("ORODENT-ECOS");
        brand.getStyleClass().add("brand-label");
        Label title = new Label("Home");
        title.getStyleClass().add("page-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        homeButton.getStyleClass().add("nav-button");
        HBox header = new HBox(18, brand, title, spacer, homeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");
        setTop(header);

        Label welcome = new Label("Benvenuto in Orodent-EcoS");
        welcome.getStyleClass().add("startup-title");
        Label databaseStatus = new Label("Database connesso · Modalità " + mode);
        databaseStatus.setId("database-status");
        databaseStatus.getStyleClass().add("muted-label");
        tonButton.setId("open-ton-button");
        tonButton.getStyleClass().add("primary-button");
        Label version = new Label("Versione " + System.getProperty("orodent.ecos.version", "sviluppo"));
        version.setId("app-version");
        version.getStyleClass().add("muted-label");
        VBox content = new VBox(18, welcome, databaseStatus, version, tonButton);
        content.setPadding(new Insets(28, 34, 36, 34));
        setCenter(content);
    }

    public Button tonButton() { return tonButton; }

    public Button homeButton() { return homeButton; }
}

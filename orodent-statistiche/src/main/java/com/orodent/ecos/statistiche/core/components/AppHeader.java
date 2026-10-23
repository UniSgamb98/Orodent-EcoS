package com.orodent.ecos.statistiche.core.components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class AppHeader extends VBox {

    private final Button homeButton = navigationButton("Dashboard");
    private final Button ecosHomeButton = navigationButton("Orodent-EcoS");
    private final MenuButton analysisMenu = navigationMenu("Analisi");
    private final MenuItem customersItem = new MenuItem("Clienti");
    private final MenuItem productsItem = new MenuItem("Prodotti");
    private final MenuItem discountsItem = new MenuItem("Sconti");
    private final MenuItem returnsItem = new MenuItem("Resi e note di credito");
    private final MenuItem archiveItem = new MenuItem("Archivio vendite");
    private final MenuButton importMenu = navigationMenu("Importazioni");
    private final MenuItem salesImportItem = new MenuItem("Vendite");
    private final MenuItem customersImportItem = new MenuItem("Anagrafica clienti");

    public AppHeader(String pageTitle, String pageDescription) {
        getStyleClass().add("app-header");
        ecosHomeButton.setId("ecosystem-home-button");
        homeButton.setId("stats-dashboard-button");
        customersItem.setId("stats-customers");
        productsItem.setId("stats-products");
        discountsItem.setId("stats-discounts");
        returnsItem.setId("stats-returns");
        archiveItem.setId("stats-archive");
        salesImportItem.setId("stats-import-sales");
        customersImportItem.setId("stats-import-customers");
        setSpacing(12);

        Label brand = new Label("STATISTICHE");
        brand.getStyleClass().add("brand-label");
        brand.setMinWidth(Region.USE_PREF_SIZE);

        VBox pageInfo = new VBox(2);
        pageInfo.setMinWidth(0);
        HBox.setHgrow(pageInfo, Priority.ALWAYS);
        Label title = new Label(pageTitle);
        title.getStyleClass().add("page-title");
        title.setMinWidth(0);
        Label description = new Label(pageDescription);
        description.getStyleClass().add("page-description");
        description.setMinWidth(0);
        pageInfo.getChildren().addAll(title, description);

        HBox identity = new HBox(18, brand, pageInfo);
        identity.setAlignment(Pos.CENTER_LEFT);
        analysisMenu.getItems().addAll(customersItem, productsItem, discountsItem, returnsItem, archiveItem);
        importMenu.getItems().addAll(salesImportItem, customersImportItem);
        HBox navigation = new HBox(12, ecosHomeButton, homeButton, analysisMenu, importMenu);
        navigation.setAlignment(Pos.CENTER_RIGHT);
        getChildren().addAll(identity, navigation);
    }

    public Button ecosHomeButton() { return ecosHomeButton; }

    public MenuItem returnsItem() { return returnsItem; }

    public Button homeButton() {
        return homeButton;
    }

    public MenuItem customersItem() { return customersItem; }
    public MenuItem productsItem() { return productsItem; }
    public MenuItem discountsItem() { return discountsItem; }
    public MenuItem archiveItem() { return archiveItem; }
    public MenuItem salesImportItem() { return salesImportItem; }
    public MenuItem customersImportItem() { return customersImportItem; }

    private Button navigationButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-button");
        button.setMinWidth(Region.USE_PREF_SIZE);
        return button;
    }

    private MenuButton navigationMenu(String text) {
        MenuButton menu = new MenuButton(text);
        menu.getStyleClass().addAll("nav-button", "nav-menu");
        menu.setMinWidth(Region.USE_PREF_SIZE);
        return menu;
    }
}

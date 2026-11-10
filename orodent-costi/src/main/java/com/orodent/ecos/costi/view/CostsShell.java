package com.orodent.ecos.costi.view;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;

import java.util.List;

/**
 * Common navigation chrome for cost sections. Controllers own actions and unsaved-change guards.
 */
public class CostsShell extends BorderPane {
    public final Button home = new Button("Home Orodent-EcoS"),
            productsNav = new Button("Costi prodotti"),
            powdersNav = new Button("Polveri e prezzi"),
            consumptionNav = new Button("Consumi e fabbisogni"),
            ordersNav = new Button("Ordini in arrivo"), scenariosNav=new Button("Scenari");
    private final VBox sidebar = new VBox(7);

    protected CostsShell(String current) {
        getStyleClass().addAll("page", "costs-module");
        getStylesheets().add(CostsShell.class.getResource("/costi/costi.css").toExternalForm());
        SVGPath icon = new SVGPath();
        icon.setContent("M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z");
        icon.getStyleClass().add("costs-home-icon");
        home.setGraphic(icon);
        home.setId("costi-home");
        home.getStyleClass().add("primary-button");
        Label title = new Label("Costi e consumi");
        title.getStyleClass().add("page-title");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        Button menu = new Button("☰ Menu");
        menu.getStyleClass().add("secondary-button");
        menu.setOnAction(e -> sidebarVisible(!sidebar.isVisible()));
        HBox header = new HBox(18, home, title, gap, menu);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");
        setTop(header);
        sidebar.getStyleClass().add("costs-sidebar");
        Label label = new Label("COSTI E CONSUMI");
        label.getStyleClass().add("costs-eyebrow");
        sidebar.getChildren().add(label);
        productsNav.setId("costi-nav-products");
        powdersNav.setId("costi-nav-powders");
        consumptionNav.setId("costi-nav-consumption");
        ordersNav.setId("costi-nav-orders");
        scenariosNav.setId("costi-nav-scenarios");
        for (String section :
                List.of(
                        "Panoramica",
                        "Costi prodotti",
                        "Scenari",
                        "Consumi e fabbisogni",
                        "Polveri e prezzi",
                        "Ordini in arrivo")) {
            Button item =
                    section.equals("Costi prodotti")
                            ? productsNav
                            : section.equals("Polveri e prezzi")
                                    ? powdersNav
                                    : section.equals("Consumi e fabbisogni")
                                            ? consumptionNav
                                            : section.equals("Ordini in arrivo")
                                                    ? ordersNav
                                                    : section.equals("Scenari")?scenariosNav:new Button(section);
            item.setMaxWidth(Double.MAX_VALUE);
            item.getStyleClass().add("costs-nav");
            item.setDisable(
                    item != productsNav
                            && item != powdersNav
                            && item != consumptionNav
                            && item != ordersNav && item != scenariosNav);
            if (section.equals(current)) item.getStyleClass().add("costs-nav-selected");
            sidebar.getChildren().add(item);
        }
        Label planned = new Label("Panoramica in preparazione.");
        planned.getStyleClass().add("costs-muted");
        planned.setWrapText(true);
        sidebar.getChildren().add(planned);
        setLeft(sidebar);
        widthProperty()
                .addListener(
                        (o, b, a) -> {
                            if (a.doubleValue() > 0
                                    && (b.doubleValue() == 0
                                            || (b.doubleValue() >= 1200)
                                                    != (a.doubleValue() >= 1200)))
                                sidebarVisible(a.doubleValue() >= 1200);
                        });
    }

    private void sidebarVisible(boolean value) {
        sidebar.setVisible(value);
        sidebar.setManaged(value);
    }
}

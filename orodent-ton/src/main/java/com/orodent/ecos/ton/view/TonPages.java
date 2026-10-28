package com.orodent.ecos.ton.view;

import com.orodent.ecos.ton.navigation.TonFeature;
import com.orodent.ecos.ton.navigation.TonSection;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Landing pages built only when requested, without loading or inventing business data. */
final class TonPages {
    private TonPages() { }

    static VBox overview(Consumer<TonSection> navigate, Consumer<TonFeature> openFeature) {
        VBox page = page("ton-overview-page", "Panoramica TON", "Produzione, schede tecniche, cotture e tracciabilità.");
        VBox ready = card(); ready.getStyleClass().add("ton-ready-card");
        ready.getChildren().addAll(badge("Disponibile", true), label("Catalogo prodotti tecnici", "ton-card-title"),
                label("Gestisci i prodotti del laboratorio, cerca un codice o aggiorna una scheda.", "ton-description"),
                action("Apri catalogo prodotti", "ton-products-button", () -> openFeature.accept(TonFeature.PRODUCTS), true));
        List<Node> areas = new ArrayList<>(); areas.add(ready);
        Arrays.stream(TonSection.values()).filter(section -> section != TonSection.OVERVIEW)
                .map(section -> areaCard(section, navigate)).forEach(areas::add);
        page.getChildren().add(grid(areas));
        return page;
    }

    static VBox section(TonSection section, Consumer<TonFeature> openFeature) {
        VBox page = page("ton-" + section.id() + "-page", section.title(), section.description());
        page.getChildren().add(label(section == TonSection.TECHNICAL
                ? "Il catalogo prodotti è operativo. Le altre funzioni saranno attivate progressivamente."
                : "Area in preparazione. Qui trovi le funzioni previste; le operazioni non sono ancora disponibili.", "ton-area-status"));
        List<Node> cards = TonFeature.in(section).stream().map(feature -> (Node) featureCard(feature, openFeature)).toList();
        page.getChildren().add(grid(cards));
        return page;
    }

    private static VBox areaCard(TonSection section, Consumer<TonSection> navigate) {
        VBox card = card();
        card.getChildren().addAll(badge(section == TonSection.TECHNICAL ? "Catalogo disponibile" : "In preparazione", section == TonSection.TECHNICAL),
                label(section.title(), "ton-card-title"), label(section.description(), "ton-description"));
        Region space = new Region(); VBox.setVgrow(space, Priority.ALWAYS);
        card.getChildren().addAll(space, action("Esplora area →", "ton-explore-" + section.id(), () -> navigate.accept(section), false));
        return card;
    }

    private static VBox featureCard(TonFeature feature, Consumer<TonFeature> openFeature) {
        VBox card = card();
        card.getChildren().addAll(badge(feature.available() ? "Disponibile" : "In preparazione", feature.available()),
                label(feature.title(), "ton-card-title"), label(feature.description(), "ton-description"));
        if (feature.available()) card.getChildren().add(action("Apri catalogo prodotti", "ton-tech-products-button",
                () -> openFeature.accept(feature), true));
        return card;
    }

    private static VBox page(String id, String title, String description) {
        VBox page = new VBox(18, label(title, "ton-page-heading"), label(description, "ton-description"));
        page.setId(id); page.getStyleClass().add("ton-landing-page");
        page.setPadding(new Insets(22, 26, 28, 26)); page.setMinWidth(0);
        return page;
    }

    private static VBox card() {
        VBox card = new VBox(12);
        card.getStyleClass().add("ton-feature-card"); card.setMinWidth(0); card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static Label badge(String text, boolean available) {
        Label badge = label(text, available ? "ton-badge-ready" : "ton-badge-planned");
        badge.setMaxWidth(Region.USE_PREF_SIZE); return badge;
    }

    private static Button action(String title, String id, Runnable onAction, boolean primary) {
        Button button = new Button(title); button.setId(id);
        button.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        button.setWrapText(true); button.setOnAction(event -> onAction.run()); return button;
    }

    private static Label label(String text, String style) {
        Label label = new Label(text); label.getStyleClass().add(style);
        label.setWrapText(true); label.setMinWidth(0); label.setMinHeight(Region.USE_PREF_SIZE); return label;
    }

    private static GridPane grid(List<Node> cards) {
        GridPane grid = new GridPane(); grid.setHgap(16); grid.setVgap(16); grid.setMinWidth(0);
        grid.widthProperty().addListener((observable, before, width) -> arrange(grid, cards, width.doubleValue()));
        arrange(grid, cards, 0); return grid;
    }

    private static void arrange(GridPane grid, List<Node> cards, double width) {
        int columns = width >= 960 ? 3 : width >= 620 ? 2 : 1;
        if (grid.getColumnConstraints().size() == columns) return;
        grid.getChildren().clear(); grid.getColumnConstraints().clear();
        for (int index = 0; index < columns; index++) {
            ColumnConstraints column = new ColumnConstraints(); column.setPercentWidth(100.0 / columns);
            column.setHgrow(Priority.ALWAYS); grid.getColumnConstraints().add(column);
        }
        for (int index = 0; index < cards.size(); index++) grid.add(cards.get(index), index % columns, index / columns);
    }
}

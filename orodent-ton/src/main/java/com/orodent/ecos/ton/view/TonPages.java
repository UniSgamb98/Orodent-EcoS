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
        ready.getChildren().addAll(badge("Disponibile", true), label("Cataloghi del laboratorio", "ton-card-title"),
                label("Linee, prodotti, articoli, composizioni, modelli disco e schede delle polveri. Naviga tra i cataloghi e consulta lo storico.", "ton-description"),
                new javafx.scene.layout.FlowPane(10, 8,
                        action("Apri articoli", "ton-articles-button", () -> openFeature.accept(TonFeature.ARTICLES), true),
                        action("Apri linee", "ton-lines-button", () -> openFeature.accept(TonFeature.LINES), false),
                        action("Apri catalogo prodotti", "ton-products-button", () -> openFeature.accept(TonFeature.PRODUCTS), true),
                        action("Apri polveri", "ton-powders-button", () -> openFeature.accept(TonFeature.POWDERS), true),
                        action("Apri modelli disco", "ton-models-button", () -> openFeature.accept(TonFeature.MODELS), false),
                        action("Apri composizioni", "ton-compositions-button", () -> openFeature.accept(TonFeature.COMPOSITIONS), true)));
        List<Node> areas = new ArrayList<>(); areas.add(ready);
        Arrays.stream(TonSection.values()).filter(section -> section != TonSection.OVERVIEW)
                .map(section -> areaCard(section, navigate)).forEach(areas::add);
        page.getChildren().add(grid(areas));
        return page;
    }

    static VBox section(TonSection section, Consumer<TonFeature> openFeature) {
        VBox page = page("ton-" + section.id() + "-page", section.title(), section.description());
        page.getChildren().add(label(section == TonSection.TECHNICAL
                ? "I sei cataloghi tecnici sono operativi. Le funzioni di produzione saranno attivate progressivamente."
                : "Area in preparazione. Qui trovi le funzioni previste; le operazioni non sono ancora disponibili.", "ton-area-status"));
        List<Node> cards = TonFeature.in(section).stream().map(feature -> (Node) featureCard(feature, openFeature)).toList();
        page.getChildren().add(grid(cards, section == TonSection.TECHNICAL ? 2 : 3));
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
        if (feature.available()) {
            String title = switch (feature) {
                case LINES -> "Apri linee";
                case ARTICLES -> "Apri articoli";
                case POWDERS -> "Apri polveri";
                case MODELS -> "Apri modelli disco";
                case COMPOSITIONS -> "Apri composizioni";
                default -> "Apri catalogo prodotti";
            };
            card.getChildren().add(action(title, "ton-tech-" + feature.name().toLowerCase(java.util.Locale.ROOT) + "-button",
                    () -> openFeature.accept(feature), true));
        }
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
        return grid(cards, 3);
    }

    private static GridPane grid(List<Node> cards, int maximumColumns) {
        GridPane grid = new GridPane(); grid.setHgap(16); grid.setVgap(16); grid.setMinWidth(0);
        grid.widthProperty().addListener((observable, before, width) -> arrange(grid, cards, width.doubleValue(), maximumColumns));
        arrange(grid, cards, 0, maximumColumns); return grid;
    }

    private static void arrange(GridPane grid, List<Node> cards, double width, int maximumColumns) {
        int columns = Math.min(maximumColumns, width >= 960 ? 3 : width >= 620 ? 2 : 1);
        if (grid.getColumnConstraints().size() == columns) return;
        grid.getChildren().clear(); grid.getColumnConstraints().clear();
        for (int index = 0; index < columns; index++) {
            ColumnConstraints column = new ColumnConstraints(); column.setPercentWidth(100.0 / columns);
            column.setHgrow(Priority.ALWAYS); grid.getColumnConstraints().add(column);
        }
        for (int index = 0; index < cards.size(); index++) grid.add(cards.get(index), index % columns, index / columns);
    }
}

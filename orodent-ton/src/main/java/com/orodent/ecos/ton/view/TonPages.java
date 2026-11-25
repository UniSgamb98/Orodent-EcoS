package com.orodent.ecos.ton.view;

import com.orodent.ecos.ton.navigation.TonFeature;
import com.orodent.ecos.ton.navigation.TonSection;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Landing pages built only when requested, without loading or inventing business data. */
final class TonPages {
    private TonPages() {}

    static VBox overview(Consumer<TonSection> navigate, Consumer<TonFeature> openFeature) {
        VBox page =
                page("ton-overview-page", "Produzione, schede tecniche, cotture e tracciabilità.");
        VBox ready = card();
        ready.getStyleClass().add("ton-ready-card");
        ready.getChildren()
                .addAll(
                        badge("Disponibile", true),
                        label("Cataloghi del laboratorio", "ton-card-title"),
                        label(
                                "Linee, prodotti, articoli, composizioni, modelli disco e schede"
                                        + " delle polveri. Naviga tra i cataloghi e consulta lo"
                                        + " storico.",
                                "ton-description"),
                        new javafx.scene.layout.FlowPane(
                                10,
                                8,
                                action(
                                        "Disponibilità prodotti",
                                        "ton-availability-button",
                                        () -> openFeature.accept(TonFeature.AVAILABILITY),
                                        true),
                                action(
                                        "Apri articoli",
                                        "ton-articles-button",
                                        () -> openFeature.accept(TonFeature.ARTICLES),
                                        true),
                                action(
                                        "Apri linee",
                                        "ton-lines-button",
                                        () -> openFeature.accept(TonFeature.LINES),
                                        false),
                                action(
                                        "Apri catalogo prodotti",
                                        "ton-products-button",
                                        () -> openFeature.accept(TonFeature.PRODUCTS),
                                        true),
                                action(
                                        "Apri polveri",
                                        "ton-powders-button",
                                        () -> openFeature.accept(TonFeature.POWDERS),
                                        true),
                                action(
                                        "Apri fornitori",
                                        "ton-suppliers-button",
                                        () -> openFeature.accept(TonFeature.SUPPLIERS),
                                        false),
                                action(
                                        "Apri modelli disco",
                                        "ton-models-button",
                                        () -> openFeature.accept(TonFeature.MODELS),
                                        false),
                                action(
                                        "Apri composizioni",
                                        "ton-compositions-button",
                                        () -> openFeature.accept(TonFeature.COMPOSITIONS),
                                        true)));
        List<Node> areas = new ArrayList<>();
        areas.add(ready);
        Arrays.stream(TonSection.values())
                .filter(section -> section != TonSection.OVERVIEW)
                .map(section -> areaCard(section, navigate))
                .forEach(areas::add);
        page.getChildren().add(grid(areas));
        return page;
    }

    static VBox section(TonSection section, Consumer<TonFeature> openFeature) {
        VBox page = page("ton-" + section.id() + "-page", section.description());
        page.getChildren()
                .add(
                        label(
                                section == TonSection.EQUIPMENT
                                        ? "Le anagrafiche di presse, stampi, forni e fornitori sono"
                                                + " disponibili."
                                        : section == TonSection.TECHNICAL
                                                ? "I cataloghi tecnici sono operativi."
                                                : section == TonSection.PRODUCTION
                                                        ? "La preparazione delle produzioni e le"
                                                              + " bozze sono disponibili. Scarti ed"
                                                              + " esecuzione fisica saranno"
                                                              + " collegati successivamente."
                                                        : section == TonSection.FIRING
                                                                ? "I dischi prodotti, i carichi e i"
                                                                      + " programmi di cottura sono"
                                                                      + " disponibili. Gli esiti"
                                                                      + " saranno collegati"
                                                                      + " successivamente."
                                                                : section == TonSection.DOCUMENTS
                                                                        ? "L’editor dei modelli è"
                                                                              + " disponibile."
                                                                              + " L’archivio dei"
                                                                              + " documenti emessi"
                                                                              + " sarà collegato"
                                                                              + " alle produzioni."
                                                                        : section
                                                                                        == TonSection
                                                                                                .CUBING
                                                                                ? "Le regole"
                                                                                      + " saranno"
                                                                                      + " richiamate"
                                                                                      + " dalle"
                                                                                      + " Specifiche"
                                                                                      + " di produzione"
                                                                                      + " dei prodotti."
                                                                                : "Area in"
                                                                                      + " preparazione."
                                                                                      + " Qui trovi"
                                                                                      + " le funzioni"
                                                                                      + " previste;"
                                                                                      + " le operazioni"
                                                                                      + " non sono"
                                                                                      + " ancora"
                                                                                      + " disponibili.",
                                "ton-area-status"));
        List<Node> cards =
                TonFeature.in(section).stream()
                        .map(feature -> (Node) featureCard(feature, openFeature))
                        .toList();
        page.getChildren().add(grid(cards, section == TonSection.TECHNICAL ? 2 : 3));
        return page;
    }

    private static VBox areaCard(TonSection section, Consumer<TonSection> navigate) {
        VBox card = card();
        card.getChildren()
                .addAll(
                        badge(
                                section == TonSection.TECHNICAL || section == TonSection.EQUIPMENT
                                        ? "Catalogo disponibile"
                                        : section == TonSection.DOCUMENTS
                                                ? "Editor disponibile"
                                                : section == TonSection.PRODUCTION
                                                        ? "Preparazione disponibile"
                                                        : section == TonSection.FIRING
                                                                ? "Carichi disponibili"
                                                                : section == TonSection.CUBING
                                                                        ? "Pagina disponibile"
                                                                        : "In preparazione",
                                section == TonSection.TECHNICAL
                                        || section == TonSection.CUBING
                                        || section == TonSection.EQUIPMENT
                                        || section == TonSection.DOCUMENTS
                                        || section == TonSection.PRODUCTION
                                        || section == TonSection.FIRING),
                        label(section.title(), "ton-card-title"),
                        label(section.description(), "ton-description"));
        Region space = new Region();
        VBox.setVgrow(space, Priority.ALWAYS);
        card.getChildren()
                .addAll(
                        space,
                        action(
                                "Esplora area →",
                                "ton-explore-" + section.id(),
                                () -> navigate.accept(section),
                                false));
        return card;
    }

    private static VBox featureCard(TonFeature feature, Consumer<TonFeature> openFeature) {
        VBox card = card();
        card.getChildren()
                .addAll(
                        badge(
                                feature.available() ? "Disponibile" : "In preparazione",
                                feature.available()),
                        label(feature.title(), "ton-card-title"),
                        label(feature.description(), "ton-description"));
        if (feature.available()) {
            String title =
                    switch (feature) {
                        case AVAILABILITY -> "Apri disponibilità";
                        case PRODUCTIONS -> "Apri produzioni";
                        case PLANNING -> "Apri dischi da caricare";
                        case PROGRAMS -> "Apri programmi di cottura";
                        case CUBATURE_RULES -> "Apri regole di cubatura";
                        case PRESSES -> "Apri presse";
                        case MOULDS -> "Apri stampi";
                        case FURNACES -> "Apri forni";
                        case LINES -> "Apri linee";
                        case ARTICLES -> "Apri articoli";
                        case POWDERS -> "Apri polveri";
                        case SUPPLIERS -> "Apri fornitori";
                        case MODELS -> "Apri modelli disco";
                        case COMPOSITIONS -> "Apri composizioni";
                        case DOCUMENT_TEMPLATES -> "Apri modelli documento";
                        case DISK_MARKING -> "Apri marcatura dischi";
                        default -> "Apri catalogo prodotti";
                    };
            card.getChildren()
                    .add(
                            action(
                                    title,
                                    "ton-tech-"
                                            + feature.name().toLowerCase(java.util.Locale.ROOT)
                                            + "-button",
                                    () -> openFeature.accept(feature),
                                    true));
        }
        return card;
    }

    private static VBox page(String id, String description) {
        VBox page = new VBox(18, label(description, "ton-description"));
        page.setId(id);
        page.getStyleClass().add("ton-landing-page");
        page.setPadding(new Insets(22, 26, 28, 26));
        page.setMinWidth(0);
        return page;
    }

    private static VBox card() {
        VBox card = new VBox(12);
        card.getStyleClass().add("ton-feature-card");
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static Label badge(String text, boolean available) {
        Label badge = label(text, available ? "ton-badge-ready" : "ton-badge-planned");
        badge.setMaxWidth(Region.USE_PREF_SIZE);
        return badge;
    }

    private static Button action(String title, String id, Runnable onAction, boolean primary) {
        Button button = new Button(title);
        button.setId(id);
        button.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        button.setWrapText(true);
        button.setOnAction(event -> onAction.run());
        return button;
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static GridPane grid(List<Node> cards) {
        return grid(cards, 3);
    }

    private static GridPane grid(List<Node> cards, int maximumColumns) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setMinWidth(0);
        // Wrapped card heights must be measured after the restored window width has been laid out.
        boolean[] queued = {false};
        grid.widthProperty()
                .addListener(
                        (observable, before, width) -> {
                            arrange(grid, cards, width.doubleValue(), maximumColumns);
                            if (queued[0]) return;
                            queued[0] = true;
                            Platform.runLater(
                                    () -> {
                                        queued[0] = false;
                                        if (grid.getScene() == null) return;
                                        for (Node card : cards) {
                                            if (card instanceof Parent parent)
                                                parent.requestLayout();
                                        }
                                        grid.requestLayout();
                                    });
                        });
        arrange(grid, cards, 0, maximumColumns);
        return grid;
    }

    private static void arrange(GridPane grid, List<Node> cards, double width, int maximumColumns) {
        int columns = Math.min(maximumColumns, width >= 960 ? 3 : width >= 620 ? 2 : 1);
        if (grid.getColumnConstraints().size() == columns) return;
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        for (int index = 0; index < columns; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / columns);
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }
        for (int index = 0; index < cards.size(); index++)
            grid.add(cards.get(index), index % columns, index / columns);
    }
}

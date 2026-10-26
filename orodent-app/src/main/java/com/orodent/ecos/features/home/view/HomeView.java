package com.orodent.ecos.features.home.view;

import com.orodent.ecos.database.DatabaseMode;
import com.orodent.ecos.module.ModuleDescriptor;
import com.orodent.ecos.ui.AppIcon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.List;
import java.util.function.Consumer;

public final class HomeView extends BorderPane {
    private final Button homeButton = new Button("Home");

    public HomeView(DatabaseMode mode, List<ModuleDescriptor> modules, Consumer<String> openModule) {
        getStyleClass().add("page");
        Label brand = new Label("ORODENT-ECOS");
        brand.getStyleClass().add("brand-label");
        ImageView logo = new ImageView(AppIcon.image());
        logo.setId("ecos-application-icon");
        logo.setFitWidth(40);
        logo.setFitHeight(40);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        logo.setMouseTransparent(true);
        HBox identity = new HBox(10, logo, brand);
        identity.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Home");
        title.getStyleClass().add("page-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        homeButton.getStyleClass().add("nav-button");
        HBox header = new HBox(18, identity, title, spacer, homeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");
        setTop(header);

        Label welcome = new Label("Le tue applicazioni");
        welcome.getStyleClass().add("startup-title");
        Label introduction = new Label("Un unico spazio per gli strumenti del tuo ufficio.");
        introduction.getStyleClass().add("muted-label");
        introduction.setWrapText(true);
        Label databaseStatus = new Label("Database connesso · Modalità " + mode);
        databaseStatus.setId("database-status");
        databaseStatus.getStyleClass().add("muted-label");
        Label version = new Label("Versione " + System.getProperty("orodent.ecos.version", "sviluppo"));
        version.setId("app-version");
        version.getStyleClass().add("muted-label");
        FlowPane catalog = new FlowPane(20, 20);
        catalog.setId("module-catalog");
        for (ModuleDescriptor module : modules) {
            catalog.getChildren().add(moduleCard(module, openModule));
        }
        VBox content = new VBox(18, welcome, introduction, databaseStatus, version, catalog);
        if (modules.isEmpty()) content.getChildren().add(new Label("Nessuna applicazione disponibile."));
        content.setPadding(new Insets(28, 34, 36, 34));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("home-scroll");
        setCenter(scroll);
    }

    private VBox moduleCard(ModuleDescriptor module, Consumer<String> openModule) {
        SVGPath icon = new SVGPath();
        icon.setContent(module.iconSvgPath());
        icon.getStyleClass().add("module-icon");
        StackPane iconBox = new StackPane(icon);
        iconBox.setMinSize(48, 48);
        iconBox.setPrefSize(48, 48);
        iconBox.setMaxSize(48, 48);
        iconBox.getStyleClass().add("module-icon-box");
        Label name = new Label(module.name());
        name.setWrapText(true);
        name.setMinWidth(0);
        HBox.setHgrow(name, Priority.ALWAYS);
        name.getStyleClass().add("card-title");
        HBox heading = new HBox(14, iconBox, name);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label description = new Label(module.description());
        description.setWrapText(true);
        description.setMinHeight(54);
        description.getStyleClass().add("muted-label");
        Button open = new Button("Apri " + module.name());
        open.setId("open-" + module.id() + "-button");
        open.getStyleClass().add("primary-button");
        open.setOnAction(event -> openModule.accept(module.id()));
        VBox card = new VBox(18, heading, description, open);
        card.setId("module-" + module.id());
        card.getStyleClass().add("info-card");
        card.setPrefWidth(330);
        return card;
    }

    public Button homeButton() { return homeButton; }
}

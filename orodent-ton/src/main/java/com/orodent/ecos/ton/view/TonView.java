package com.orodent.ecos.ton.view;

import com.orodent.ecos.ton.navigation.TonSection;
import com.orodent.ecos.ton.navigation.TonFeature;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Persistent shell. EcoS keeps ownership of the window, database and background workers. */
public final class TonView extends BorderPane {
    private static final double COMPACT_WIDTH = 1020;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private final Button home = new Button("Home Orodent-EcoS");
    private final Button menu = new Button("☰  Menu TON");
    private final Label title = new Label("TON · Panoramica");
    private final Label breadcrumb = new Label();
    private final VBox sidebar = new VBox(6);
    private final BorderPane body = new BorderPane();
    private final ScrollPane viewport = new ScrollPane();
    private final Map<TonSection, Button> buttons = new EnumMap<>(TonSection.class);
    private final BooleanProperty navigationBlocked = new SimpleBooleanProperty();
    private final ChangeListener<Number> resizeListener = (observable, previous, width) -> resize(width.doubleValue());
    private Consumer<TonSection> navigate = ignored -> { };
    private Consumer<TonFeature> openFeature = ignored -> { };
    private boolean compact;
    private boolean menuOpen = true;

    public TonView() {
        getStylesheets().add(java.util.Objects.requireNonNull(getClass().getResource("/ton/css/catalog.css")).toExternalForm());
        getStylesheets().add(java.util.Objects.requireNonNull(getClass().getResource("/ton/css/navigation.css")).toExternalForm());
        setId("ton-view"); getStyleClass().addAll("page", "ton-module");
        home.setId("ton-home-button"); home.getStyleClass().add("primary-button");
        home.setGraphic(icon("M3 11L12 3l9 8v10h-6v-7H9v7H3z", "ton-home-icon"));
        home.disableProperty().bind(navigationBlocked);
        menu.setId("ton-menu-button"); menu.getStyleClass().add("nav-button");
        menu.setAccessibleText("Mostra o nascondi il menu di TON");
        menu.setOnAction(event -> showMenu(!menuOpen));
        title.setMinWidth(0); title.setMaxWidth(Double.MAX_VALUE); title.getStyleClass().add("page-title");
        HBox.setHgrow(title, Priority.ALWAYS);
        HBox header = new HBox(16, home, title, menu); header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header"); setTop(header);

        sidebar.setId("ton-sidebar"); sidebar.getStyleClass().add("ton-sidebar");
        sidebar.disableProperty().bind(navigationBlocked);
        sidebar.getChildren().addAll(groupLabel("SPAZIO TON"), navigation(TonSection.OVERVIEW), groupLabel("LAVORO"),
                navigation(TonSection.PRODUCTION), navigation(TonSection.FIRING), groupLabel("PREPARAZIONE"),
                navigation(TonSection.TECHNICAL), groupLabel("CONSULTAZIONE"),
                navigation(TonSection.TRACEABILITY), navigation(TonSection.DOCUMENTS));
        breadcrumb.setId("ton-breadcrumb"); breadcrumb.getStyleClass().add("ton-breadcrumb");
        breadcrumb.setWrapText(true); breadcrumb.setMaxWidth(Double.MAX_VALUE);
        viewport.setId("ton-page-scroll"); viewport.setFitToWidth(true); viewport.setFitToHeight(true);
        viewport.getStyleClass().add("ton-page-scroll");
        BorderPane workspace = new BorderPane(viewport); workspace.setTop(breadcrumb); workspace.setMinWidth(0);
        body.setCenter(workspace); setCenter(body); showMenu(true);
        widthProperty().addListener(resizeListener);
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && compact && menuOpen) { showMenu(false); menu.requestFocus(); event.consume(); }
        });
        showSection(TonSection.OVERVIEW);
    }

    public void onSection(Consumer<TonSection> action) { navigate = java.util.Objects.requireNonNull(action); }
    public void onFeature(Consumer<TonFeature> action) { openFeature = java.util.Objects.requireNonNull(action); }
    public Button homeButton() { return home; }
    public BooleanProperty navigationBlockedProperty() { return navigationBlocked; }

    public void showSection(TonSection section) {
        viewport.setContent(section == TonSection.OVERVIEW
                ? TonPages.overview(this::requestSection, this::requestFeature)
                : TonPages.section(section, this::requestFeature));
        select(section, "TON › " + section.title());
    }

    public void showProducts(Parent page) {
        viewport.setContent(page);
        select(TonSection.TECHNICAL, "TON › Dati tecnici › Prodotti tecnici");
    }

    private Button navigation(TonSection section) {
        Button button = new Button(section.title(), icon(section.icon(), "ton-nav-icon"));
        button.setId(section == TonSection.OVERVIEW ? "ton-overview-button" : "ton-nav-" + section.id());
        button.getStyleClass().add("ton-nav-item"); button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT); button.setWrapText(true);
        button.setOnAction(event -> requestSection(section)); buttons.put(section, button);
        return button;
    }

    private void requestSection(TonSection section) { if (!navigationBlocked.get()) navigate.accept(section); }
    private void requestFeature(TonFeature feature) {
        if (!navigationBlocked.get() && feature.available()) openFeature.accept(feature);
    }

    private void select(TonSection selected, String location) {
        title.setText("TON · " + selected.title()); breadcrumb.setText(location);
        buttons.forEach((section, button) -> {
            button.pseudoClassStateChanged(SELECTED, section == selected);
            button.setAccessibleText(section.title() + (section == selected ? ", area corrente" : ""));
        });
        viewport.setVvalue(0); viewport.setHvalue(0);
        if (compact) showMenu(false);
    }

    private void resize(double width) {
        if (width <= 0) return;
        boolean next = width < COMPACT_WIDTH;
        if (next != compact) { compact = next; showMenu(!compact); }
    }

    private void showMenu(boolean open) {
        menuOpen = open; body.setLeft(open ? sidebar : null);
        menu.setText(open ? "☰  Chiudi menu" : "☰  Menu TON");
    }

    private static Label groupLabel(String text) {
        Label label = new Label(text); label.getStyleClass().add("ton-nav-group"); return label;
    }

    private static Region icon(String content, String style) {
        SVGPath path = new SVGPath(); path.setContent(content); path.getStyleClass().add(style);
        path.setScaleX(.7); path.setScaleY(.7);
        StackPane frame = new StackPane(path); frame.setMinSize(20, 20); frame.setPrefSize(20, 20); frame.setMaxSize(20, 20);
        return frame;
    }

    public void dispose() {
        widthProperty().removeListener(resizeListener); setOnKeyPressed(null);
        navigate = ignored -> { }; openFeature = ignored -> { };
        home.setOnAction(null); menu.setOnAction(null);
        buttons.values().forEach(button -> button.setOnAction(null));
        viewport.setContent(null);
    }
}

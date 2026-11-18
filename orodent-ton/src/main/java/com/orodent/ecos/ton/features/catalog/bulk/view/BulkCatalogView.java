package com.orodent.ecos.ton.features.catalog.bulk.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;

import javafx.beans.property.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/** A single page scroll; the cards stack naturally in compact windows. */
public final class BulkCatalogView extends ScrollPane {
    private final ObjectProperty<CatalogKind> mode = new SimpleObjectProperty<>();
    private final EnumMap<CatalogKind, BulkWorkspace> workspaces = new EnumMap<>(CatalogKind.class);
    private final VBox content = new VBox(18), workspaceHost = new VBox();
    private final Button back = new Button("← Torna al catalogo"),
            refresh = new Button("Aggiorna dati");

    public BulkCatalogView(CatalogKind initial) {
        setId("ton-bulk-view");
        getStyleClass().add("ton-bulk");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setMinWidth(0);
        getStylesheets().add(getClass().getResource("/ton/css/catalog-bulk.css").toExternalForm());
        content.getStyleClass().add("bulk-page");
        content.setMinWidth(0);
        setContent(content);
        workspaceHost.setMinWidth(0);
        back.setId("ton-bulk-back");
        back.getStyleClass().add("bulk-link-button");
        refresh.setId("ton-bulk-refresh");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox commands = new HBox(12, back, gap, refresh);
        commands.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Inserimento multiplo");
        title.getStyleClass().add("bulk-page-title");
        ToggleGroup toggles = new ToggleGroup();
        ToggleButton products = new ToggleButton("Più prodotti di una linea"),
                articles = new ToggleButton("Articoli per più prodotti");
        products.setId("ton-bulk-mode-products");
        articles.setId("ton-bulk-mode-articles");
        products.setToggleGroup(toggles);
        articles.setToggleGroup(toggles);
        HBox tabs = new HBox(4, products, articles);
        tabs.getStyleClass().add("bulk-mode-tabs");
        workspaces.put(CatalogKind.PRODUCTS, new BulkWorkspace(CatalogKind.PRODUCTS));
        workspaces.put(CatalogKind.ARTICLES, new BulkWorkspace(CatalogKind.ARTICLES));
        products.setOnAction(e -> mode.set(CatalogKind.PRODUCTS));
        articles.setOnAction(e -> mode.set(CatalogKind.ARTICLES));
        mode.addListener(
                (o, a, b) -> {
                    products.setSelected(b == CatalogKind.PRODUCTS);
                    articles.setSelected(b == CatalogKind.ARTICLES);
                    workspaceHost.getChildren().setAll(workspaces.get(b));
                    setVvalue(0);
                });
        content.getChildren().setAll(commands, title, tabs, workspaceHost);
        mode.set(initial);
    }

    public ObjectProperty<CatalogKind> modeProperty() {
        return mode;
    }

    public CatalogKind mode() {
        return mode.get();
    }

    public BulkWorkspace workspace() {
        return workspaces.get(mode());
    }

    public BulkWorkspace workspace(CatalogKind kind) {
        return workspaces.get(kind);
    }

    public List<BulkWorkspace> workspaces() {
        return List.copyOf(workspaces.values());
    }

    public Button back() {
        return back;
    }

    public Button refresh() {
        return refresh;
    }

    public boolean dirty() {
        return workspaces.values().stream().anyMatch(BulkWorkspace::dirty);
    }
}

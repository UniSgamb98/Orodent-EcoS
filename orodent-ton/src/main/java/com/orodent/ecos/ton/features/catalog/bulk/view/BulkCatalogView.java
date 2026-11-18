package com.orodent.ecos.ton.features.catalog.bulk.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;

import javafx.beans.property.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/** Fills the window in two columns; compact windows use one natural page scroll. */
public final class BulkCatalogView extends ScrollPane {
    private final ObjectProperty<CatalogKind> mode = new SimpleObjectProperty<>();
    private final EnumMap<CatalogKind, BulkWorkspace> workspaces = new EnumMap<>(CatalogKind.class);
    private final VBox content = new VBox(18), workspaceHost = new VBox();
    private final HBox heading = new HBox(12);
    private final HBox tabs = new HBox(4);
    private final Button back = new Button("← Torna al catalogo"),
            refresh = new Button("Aggiorna dati");

    public BulkCatalogView(CatalogKind initial) {
        setId("ton-bulk-view");
        getStyleClass().add("ton-bulk");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setMinWidth(0);
        setMinHeight(0);
        getStylesheets().add(getClass().getResource("/ton/css/catalog-bulk.css").toExternalForm());
        content.getStyleClass().add("bulk-page");
        content.setMinWidth(0);
        content.setMinHeight(0);
        VBox.setVgrow(workspaceHost, Priority.ALWAYS);
        setContent(content);
        workspaceHost.setMinWidth(0);
        workspaceHost.setMinHeight(0);
        back.setId("ton-bulk-back");
        back.getStyleClass().add("bulk-link-button");
        refresh.setId("ton-bulk-refresh");
        refresh.getStyleClass().add("bulk-refresh-button");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        heading.getChildren().setAll(back, gap, refresh);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.setMinHeight(Region.USE_PREF_SIZE);
        back.setMinWidth(Region.USE_PREF_SIZE);
        refresh.setMinWidth(Region.USE_PREF_SIZE);
        ToggleGroup toggles = new ToggleGroup();
        ToggleButton products = new ToggleButton("Più prodotti di una linea"),
                articles = new ToggleButton("Articoli per più prodotti");
        products.setId("ton-bulk-mode-products");
        articles.setId("ton-bulk-mode-articles");
        products.setToggleGroup(toggles);
        articles.setToggleGroup(toggles);
        tabs.getChildren().setAll(products, articles);
        tabs.setMinHeight(Region.USE_PREF_SIZE);
        tabs.getStyleClass().add("bulk-mode-tabs");
        tabs.setMaxWidth(Region.USE_PREF_SIZE);
        workspaces.put(CatalogKind.PRODUCTS, new BulkWorkspace(CatalogKind.PRODUCTS));
        workspaces.put(CatalogKind.ARTICLES, new BulkWorkspace(CatalogKind.ARTICLES));
        products.setOnAction(e -> mode.set(CatalogKind.PRODUCTS));
        articles.setOnAction(e -> mode.set(CatalogKind.ARTICLES));
        mode.addListener(
                (o, a, b) -> {
                    products.setSelected(b == CatalogKind.PRODUCTS);
                    articles.setSelected(b == CatalogKind.ARTICLES);
                    BulkWorkspace workspace = workspaces.get(b);
                    workspaceHost.getChildren().setAll(workspace);
                    VBox.setVgrow(workspace, Priority.ALWAYS);
                    requestLayout();
                    setVvalue(0);
                });
        content.getChildren().setAll(heading, tabs, workspaceHost);
        mode.set(initial);
    }

    @Override
    protected void layoutChildren() {
        double width =
                Math.max(
                        0,
                        getWidth()
                                - content.getInsets().getLeft()
                                - content.getInsets().getRight());
        double available =
                getHeight()
                        - content.getInsets().getTop()
                        - content.getInsets().getBottom()
                        - heading.prefHeight(width)
                        - tabs.prefHeight(width)
                        - 2 * content.getSpacing();
        BulkWorkspace workspace = workspace();
        boolean wide = width >= 1040 && available >= workspace.minimumWideHeight(width);
        workspace.setWideLayout(wide);
        if (isFitToHeight() != wide) {
            setFitToHeight(wide);
            setVbarPolicy(wide ? ScrollBarPolicy.NEVER : ScrollBarPolicy.AS_NEEDED);
            setVvalue(0);
        }
        super.layoutChildren();
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

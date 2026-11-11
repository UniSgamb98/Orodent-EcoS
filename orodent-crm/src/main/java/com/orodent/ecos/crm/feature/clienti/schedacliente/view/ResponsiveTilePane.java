package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;

/** TilePane with a fixed two-column layout for the client profile. */
class ResponsiveTilePane extends TilePane {

    private static final int FIXED_COLUMNS = 2;
    private static final double FIXED_TILE_WIDTH = 260;

    ResponsiveTilePane(double gap, double twoColumnBreakpoint) {
        super(gap, gap);
        getStyleClass().add("client-profile-responsive-tile-pane");
        setMaxWidth(Double.MAX_VALUE);
        setPrefColumns(FIXED_COLUMNS);
        setPrefTileWidth(FIXED_TILE_WIDTH);
    }

    void addStretchingTile(Node node) {
        if (node instanceof Region region) {
            region.setMinWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
        }
        getChildren().add(node);
    }
}

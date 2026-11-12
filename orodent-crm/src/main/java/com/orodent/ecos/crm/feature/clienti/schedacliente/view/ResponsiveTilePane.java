package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** Responsive grid with independent row heights, so short sections don't leave empty tiles. */
class ResponsiveTilePane extends GridPane {
    private final double breakpoint;
    private int columns;

    ResponsiveTilePane(double gap, double twoColumnBreakpoint) {
        breakpoint = twoColumnBreakpoint;
        getStyleClass().add("client-profile-responsive-tile-pane");
        setHgap(gap);
        setVgap(gap);
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        widthProperty().addListener((o, before, width) -> updateColumns());
        updateColumns();
    }

    private void updateColumns() {
        int count = getWidth() >= breakpoint ? 2 : 1;
        if (columns != count) {
            columns = count;
            getColumnConstraints().clear();
            for (int i = 0; i < columns; i++) {
                ColumnConstraints column = new ColumnConstraints();
                column.setPercentWidth(100.0 / columns);
                column.setFillWidth(true);
                getColumnConstraints().add(column);
            }
        }
        for (int i = 0; i < getChildren().size(); i++) {
            Node child = getChildren().get(i);
            setColumnIndex(child, i % columns);
            setRowIndex(child, i / columns);
        }
    }

    void addStretchingTile(Node node) {
        if (node instanceof Region region) {
            region.setMinWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
        }
        setHgrow(node, Priority.ALWAYS);
        getChildren().add(node);
        updateColumns();
    }
}

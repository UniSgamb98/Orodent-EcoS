package com.orodent.ecos.statistiche.core.components;

import javafx.scene.Node;
import javafx.geometry.Orientation;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;

/** Keeps the selected tab at its preferred height when used inside a scrolling container. */
public final class ContentSizedTabPane extends TabPane {
    public ContentSizedTabPane() {
        getSelectionModel().selectedItemProperty().addListener((observable, previous, current) -> requestLayout());
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computePrefHeight(double width) {
        Tab selected = getSelectionModel().getSelectedItem();
        if (selected == null || !(selected.getContent() instanceof Region content)) {
            return super.computePrefHeight(width);
        }
        double contentWidth = width < 0 ? -1 : Math.max(0, width - snappedLeftInset() - snappedRightInset());
        Node header = lookup(".tab-header-area");
        double headerHeight = header instanceof Region region ? region.prefHeight(contentWidth) : 40;
        return snappedTopInset() + headerHeight + content.prefHeight(contentWidth) + snappedBottomInset();
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }
}

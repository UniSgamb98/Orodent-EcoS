package com.orodent.ecos.statistiche.features.sales.customers.view;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;

/** Keeps transient scroll state within the view and restores it after layout. */
final class CustomerDetailScrollState {
    private final ScrollPane scroll;
    private Double savedPosition;
    private Scene pendingScene;
    private Runnable pendingRestore;

    CustomerDetailScrollState(ScrollPane scroll) {
        this.scroll = scroll;
    }

    void capture() {
        cancelPendingRestore();
        if (savedPosition == null) savedPosition = scroll.getVvalue();
    }

    void reset() {
        cancelPendingRestore();
        savedPosition = null;
        scroll.setVvalue(scroll.getVmin());
    }

    void restoreAfterLayout() {
        cancelPendingRestore();
        if (savedPosition == null) return;
        Scene scene = scroll.getScene();
        if (scene == null) {
            scroll.setVvalue(savedPosition);
            savedPosition = null;
            return;
        }
        pendingScene = scene;
        pendingRestore = () -> {
            double position = savedPosition;
            cancelPendingRestore();
            savedPosition = null;
            scroll.setVvalue(position);
        };
        scene.addPostLayoutPulseListener(pendingRestore);
        Platform.requestNextPulse();
    }

    void dispose() {
        cancelPendingRestore();
        savedPosition = null;
    }

    private void cancelPendingRestore() {
        if (pendingRestore != null) {
            pendingScene.removePostLayoutPulseListener(pendingRestore);
            pendingScene = null;
            pendingRestore = null;
        }
    }
}

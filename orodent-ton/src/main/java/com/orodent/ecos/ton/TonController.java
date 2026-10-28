package com.orodent.ecos.ton;

import com.orodent.ecos.module.ModuleContext;
import com.orodent.ecos.ton.features.powders.controller.PowdersController;
import com.orodent.ecos.ton.features.powders.service.PowderService;
import com.orodent.ecos.ton.features.powders.view.PowdersView;
import com.orodent.ecos.ton.features.products.controller.ProductsController;
import com.orodent.ecos.ton.features.products.service.ProductService;
import com.orodent.ecos.ton.features.products.view.ProductsView;
import com.orodent.ecos.ton.navigation.FeatureController;
import com.orodent.ecos.ton.navigation.TonFeature;
import com.orodent.ecos.ton.navigation.TonSection;
import com.orodent.ecos.ton.view.TonView;

import javafx.scene.Parent;

final class TonController {
    private final TonView view = new TonView();
    private final ProductService products;
    private final PowderService powders;
    private FeatureController active;
    private TonFeature activeFeature;
    private TonSection section = TonSection.OVERVIEW;

    TonController(ModuleContext context) {
        products = new ProductService(context.connections(), context.workers());
        powders = new PowderService(context.connections(), context.workers());
        view.homeButton()
                .setOnAction(
                        event -> {
                            if (canLeave()) context.showHome().run();
                        });
        view.onSection(this::showSection);
        view.onFeature(this::showFeature);
    }

    TonView root() {
        return view;
    }

    private boolean canLeave() {
        return active == null || active.canLeave();
    }

    private void showSection(TonSection next) {
        if ((active == null && section == next) || !canLeave()) return;
        closePage();
        section = next;
        view.showSection(next);
    }

    private void showFeature(TonFeature feature) {
        if (activeFeature == feature || !canLeave()) return;
        closePage();
        switch (feature) {
            case PRODUCTS -> {
                var page = new ProductsView();
                var controller = new ProductsController(page, products);
                install(feature, page, controller, controller::loadInitialData);
            }
            case POWDERS -> {
                var page = new PowdersView();
                var controller = new PowdersController(page, powders);
                install(feature, page, controller, controller::loadInitialData);
            }
            default ->
                    throw new IllegalArgumentException(
                            "Funzione TON non ancora disponibile: " + feature);
        }
    }

    private void install(
            TonFeature feature, Parent page, FeatureController controller, Runnable initialize) {
        active = controller;
        activeFeature = feature;
        section = feature.section();
        view.navigationBlockedProperty().bind(active.savingProperty());
        view.showFeature(page, feature);
        initialize.run();
    }

    private void closePage() {
        view.navigationBlockedProperty().unbind();
        view.navigationBlockedProperty().set(false);
        if (active != null) {
            active.dispose();
            active = null;
        }
        activeFeature = null;
    }

    void dispose() {
        closePage();
        view.dispose();
    }
}

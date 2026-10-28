package com.orodent.ecos.ton;

import com.orodent.ecos.module.ModuleContext;
import com.orodent.ecos.ton.features.products.controller.ProductsController;
import com.orodent.ecos.ton.features.products.service.ProductService;
import com.orodent.ecos.ton.features.products.view.ProductsView;
import com.orodent.ecos.ton.view.TonView;
import com.orodent.ecos.ton.navigation.TonSection;
import com.orodent.ecos.ton.navigation.TonFeature;

final class TonController {
    private final TonView view = new TonView();
    private final ProductService products;
    private ProductsController active;
    private TonSection section = TonSection.OVERVIEW;

    TonController(ModuleContext context) {
        products = new ProductService(context.connections(), context.workers());
        view.homeButton().setOnAction(event -> { if (canLeave()) context.showHome().run(); });
        view.onSection(this::showSection);
        view.onFeature(feature -> {
            switch (feature) {
                case PRODUCTS -> showProducts();
                default -> throw new IllegalArgumentException("Funzione TON non ancora disponibile: " + feature);
            }
        });
    }

    TonView root() { return view; }

    private boolean canLeave() { return active == null || active.canLeave(); }

    private void showSection(TonSection next) {
        if ((active == null && section == next) || !canLeave()) return;
        closePage(); section = next; view.showSection(next);
    }

    private void showProducts() {
        if (active != null || !canLeave()) return;
        closePage();
        ProductsView page = new ProductsView();
        active = new ProductsController(page, products);
        section = TonSection.TECHNICAL;
        view.navigationBlockedProperty().bind(active.savingProperty());
        view.showProducts(page);
        active.loadInitialData();
    }

    private void closePage() {
        view.navigationBlockedProperty().unbind(); view.navigationBlockedProperty().set(false);
        if (active != null) { active.dispose(); active = null; }
    }

    void dispose() {
        closePage();
        view.dispose();
    }
}

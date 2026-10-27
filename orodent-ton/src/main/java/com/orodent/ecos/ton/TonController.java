package com.orodent.ecos.ton;

import com.orodent.ecos.module.ModuleContext;
import com.orodent.ecos.ton.features.products.controller.ProductsController;
import com.orodent.ecos.ton.features.products.service.ProductService;
import com.orodent.ecos.ton.features.products.view.ProductsView;
import com.orodent.ecos.ton.view.TonView;

final class TonController {
    private final TonView view = new TonView();
    private final ProductService products;
    private ProductsController active;

    TonController(ModuleContext context) {
        products = new ProductService(context.connections(), context.workers());
        view.homeButton().setOnAction(event -> { if (canLeave()) context.showHome().run(); });
        view.overviewButton().setOnAction(event -> { if (canLeave()) { closePage(); view.showOverview(); } });
        view.productsButton().setOnAction(event -> showProducts());
    }

    TonView root() { return view; }

    private boolean canLeave() { return active == null || active.canLeave(); }

    private void showProducts() {
        if (!canLeave()) return;
        closePage();
        ProductsView page = new ProductsView();
        active = new ProductsController(page, products);
        view.homeButton().disableProperty().bind(active.savingProperty());
        view.overviewButton().disableProperty().bind(active.savingProperty());
        view.showProducts(page);
        active.loadInitialData();
    }

    private void closePage() {
        view.homeButton().disableProperty().unbind(); view.homeButton().setDisable(false);
        view.overviewButton().disableProperty().unbind(); view.overviewButton().setDisable(false);
        if (active != null) { active.dispose(); active = null; }
    }

    void dispose() {
        closePage();
        view.homeButton().setOnAction(null); view.overviewButton().setOnAction(null); view.productsButton().setOnAction(null);
    }
}

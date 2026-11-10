package com.orodent.ecos.costi;

import com.orodent.ecos.costi.consumption.*;
import com.orodent.ecos.costi.scenarios.*;
import com.orodent.ecos.costi.controller.PowderPricesController;
import com.orodent.ecos.costi.orders.*;
import com.orodent.ecos.costi.products.controller.ProductCostsController;
import com.orodent.ecos.costi.products.service.ProductCostService;
import com.orodent.ecos.costi.products.view.ProductCostsView;
import com.orodent.ecos.costi.service.PowderPriceService;
import com.orodent.ecos.costi.view.PowderPricesView;
import com.orodent.ecos.module.ModuleContext;

import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.util.function.BooleanSupplier;

/**
 * Navigation creates/disposes feature sessions while keeping the borrowed database/workers alive.
 */
final class CostiController {
    private final ModuleContext context;
    private final StackPane root = new StackPane();
    private BooleanSupplier canLeave = () -> true;
    private Runnable close = () -> {};

    CostiController(ModuleContext context) {
        this.context = context;
        powders();
    }

    Parent root() {
        return root;
    }

    private void powders() {
        if (!canLeave.getAsBoolean()) return;
        close.run();
        var view = new PowderPricesView();
        view.orders.setOnAction(e -> orders());
        var controller =
                new PowderPricesController(
                        view,
                        new PowderPriceService(context.connections(), context.workers()),
                        context.showHome());
        canLeave = controller::canLeave;
        close = controller::dispose;
        view.productsNav.setOnAction(e -> products());
        view.consumptionNav.setOnAction(e -> consumption());
        view.ordersNav.setOnAction(e -> orders());
        view.scenariosNav.setOnAction(e->scenarios());
        root.getChildren().setAll(view);
        controller.start();
    }

    private void products() {
        if (!canLeave.getAsBoolean()) return;
        close.run();
        var view = new ProductCostsView();
        var controller =
                new ProductCostsController(
                        view,
                        new ProductCostService(context.connections(), context.workers()),
                        context.workers(),
                        context.showHome());
        canLeave = controller::canLeave;
        close = controller::dispose;
        view.powdersNav.setOnAction(e -> powders());
        view.consumptionNav.setOnAction(e -> consumption());
        view.ordersNav.setOnAction(e -> orders());
        view.scenariosNav.setOnAction(e->scenarios());
        root.getChildren().setAll(view);
        controller.start();
    }

    private void consumption() {
        if (!canLeave.getAsBoolean()) return;
        close.run();
        var view = new ConsumptionView();
        var controller =
                new ConsumptionController(
                        view,
                        new ConsumptionService(context.connections(), context.workers()),
                        context.workers(),
                        context.showHome());
        canLeave = controller::canLeave;
        close = controller::dispose;
        view.productsNav.setOnAction(e -> products());
        view.powdersNav.setOnAction(e -> powders());
        view.ordersNav.setOnAction(e -> orders());
        view.scenariosNav.setOnAction(e->scenarios());
        root.getChildren().setAll(view);
        controller.start();
    }

    private void orders() {
        if (!canLeave.getAsBoolean()) return;
        close.run();
        var view = new OrdersView();
        var controller =
                new OrdersController(
                        view,
                        new OrderService(context.connections(), context.workers()),
                        context.showHome());
        canLeave = controller::canLeave;
        close = controller::dispose;
        view.productsNav.setOnAction(e -> products());
        view.powdersNav.setOnAction(e -> powders());
        view.consumptionNav.setOnAction(e -> consumption());
        view.scenariosNav.setOnAction(e->scenarios());
        root.getChildren().setAll(view);
        controller.start();
    }

    private void scenarios(){
        if(!canLeave.getAsBoolean())return;close.run();
        var view=new ScenariosView();var controller=new ScenariosController(view,new ScenarioService(context.connections(),context.workers()),context.workers(),context.showHome());
        canLeave=controller::canLeave;close=controller::dispose;
        view.productsNav.setOnAction(e->products());view.powdersNav.setOnAction(e->powders());view.consumptionNav.setOnAction(e->consumption());view.ordersNav.setOnAction(e->orders());
        root.getChildren().setAll(view);controller.start();
    }

    void dispose() {
        close.run();
        root.getChildren().clear();
    }
}

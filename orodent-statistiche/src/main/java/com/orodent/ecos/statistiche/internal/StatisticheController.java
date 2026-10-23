package com.orodent.ecos.statistiche.internal;

import com.orodent.ecos.statistiche.navigation.StatisticheNavigator;
import com.orodent.ecos.statistiche.core.components.AppHeader;
import com.orodent.ecos.statistiche.features.sales.dashboard.controller.SalesDashboardController;
import com.orodent.ecos.statistiche.features.sales.dashboard.view.SalesDashboardView;
import com.orodent.ecos.statistiche.features.sales.importing.controller.SalesImportController;
import com.orodent.ecos.statistiche.features.sales.importing.view.SalesImportView;
import com.orodent.ecos.statistiche.features.customers.importing.controller.CustomerImportController;
import com.orodent.ecos.statistiche.features.customers.importing.view.CustomerImportView;
import com.orodent.ecos.statistiche.features.sales.customers.controller.CustomersController;
import com.orodent.ecos.statistiche.features.sales.customers.view.CustomersView;
import com.orodent.ecos.statistiche.features.sales.products.controller.ProductsController;
import com.orodent.ecos.statistiche.features.sales.products.view.ProductsView;
import com.orodent.ecos.statistiche.features.sales.discounts.controller.DiscountsController;
import com.orodent.ecos.statistiche.features.sales.discounts.view.DiscountsView;
import com.orodent.ecos.statistiche.features.sales.returns.controller.ReturnsController;
import com.orodent.ecos.statistiche.features.sales.returns.view.ReturnsView;
import com.orodent.ecos.statistiche.features.sales.archive.controller.SalesArchiveController;
import com.orodent.ecos.statistiche.features.sales.archive.view.SalesArchiveView;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;

import java.util.Objects;

public final class StatisticheController implements StatisticheNavigator {

    private final BorderPane root = new BorderPane();
    private final StatisticheServices services;
    private final Runnable onEcosHome;
    private Runnable activePageCleanup = () -> { };

    public StatisticheController(StatisticheServices services, Runnable onEcosHome) {
        this.services = Objects.requireNonNull(services, "services");
        this.onEcosHome = Objects.requireNonNull(onEcosHome, "onEcosHome");
        root.setId("statistiche-module");
        root.getStylesheets().add(css("/statistiche/css/global.css"));
    }

    public Parent root() {
        return root;
    }

    @Override
    public void showHome() {
        SalesDashboardView view = new SalesDashboardView();
        view.setId("statistiche-dashboard");
        configureHeader(view.header());
        SalesDashboardController controller = new SalesDashboardController(
                view,
                services.salesDashboardService(),
                this
        );
        showView(view, controller::dispose, "/statistiche/css/features/sales-dashboard.css");
        controller.loadInitialData();
    }

    @Override
    public void showSalesImport() {
        SalesImportView view = new SalesImportView();
        view.setId("stats-import-sales-view");
        configureHeader(view.header());
        SalesImportController controller = new SalesImportController(
                view,
                services.venditeCsvImportService()
        );
        showView(view, controller::dispose, "/statistiche/css/features/sales-import.css");
    }

    @Override
    public void showCustomerImport() {
        CustomerImportView view = new CustomerImportView();
        view.setId("stats-import-customers-view");
        configureHeader(view.header());
        CustomerImportController controller = new CustomerImportController(view, services.clientiCsvImportService());
        showView(view, controller::dispose, "/statistiche/css/features/sales-import.css");
    }

    @Override
    public void showCustomers() {
        openCustomers(null, null);
    }

    @Override
    public void showCustomer(String code, int year) {
        openCustomers(code, year);
    }

    private void openCustomers(String code, Integer year) {
        CustomersView view = new CustomersView();
        view.setId("stats-customers-view");
        configureHeader(view.header());
        CustomersController controller = new CustomersController(view, services.salesAnalysisService());
        showView(view, controller::dispose, "/statistiche/css/features/sales-dashboard.css", "/statistiche/css/features/customers-analysis.css", "/statistiche/css/features/customer-follow-up.css");
        if (code == null) {
            controller.loadInitialData();
        } else {
            controller.loadInitialData(code, year);
        }
    }

    @Override
    public void showProducts() {
        ProductsView view = new ProductsView();
        view.setId("stats-products-view");
        configureHeader(view.header());
        ProductsController controller = new ProductsController(view, services.productAnalysisService(), this);
        showView(view, controller::dispose, "/statistiche/css/features/sales-dashboard.css", "/statistiche/css/features/products-analysis.css");
        controller.loadInitialData();
    }

    @Override
    public void showDiscounts() {
        DiscountsView view = new DiscountsView();
        view.setId("stats-discounts-view");
        configureHeader(view.header());
        DiscountsController controller = new DiscountsController(view, services.salesAnalysisService());
        showView(view, controller::dispose, "/statistiche/css/features/sales-dashboard.css");
        controller.loadInitialData();
    }

    @Override
    public void showReturns() {
        ReturnsView view = new ReturnsView();
        view.setId("stats-returns-view");
        configureHeader(view.header());
        ReturnsController controller = new ReturnsController(view, services.salesAnalysisService());
        showView(view, controller::dispose, "/statistiche/css/features/sales-dashboard.css");
        controller.loadInitialData();
    }

    @Override
    public void showSalesArchive() {
        SalesArchiveView view = new SalesArchiveView();
        view.setId("stats-archive-view");
        configureHeader(view.header());
        SalesArchiveController controller = new SalesArchiveController(view, services.salesAnalysisService());
        showView(view, controller::dispose, "/statistiche/css/features/sales-dashboard.css");
        controller.loadInitialData();
    }

    private void showView(Parent page, Runnable cleanup, String... extraCss) {
        runActivePageCleanup();
        activePageCleanup = cleanup;
        root.setCenter(page);
        page.getStylesheets().add(css("/statistiche/css/components/monthly-forecast.css"));
        for (String stylesheet : extraCss) {
            page.getStylesheets().add(css(stylesheet));
        }
    }

    private String css(String resource) {
        return Objects.requireNonNull(getClass().getResource(resource),
                "CSS non trovato: " + resource).toExternalForm();
    }

    private void configureHeader(AppHeader header) {
        header.homeButton().setOnAction(event -> showHome());
        header.ecosHomeButton().setOnAction(event -> onEcosHome.run());
        header.salesImportItem().setOnAction(event -> showSalesImport());
        header.customersImportItem().setOnAction(event -> showCustomerImport());
        header.customersItem().setOnAction(event -> showCustomers());
        header.productsItem().setOnAction(event -> showProducts());
        header.discountsItem().setOnAction(event -> showDiscounts());
        header.archiveItem().setOnAction(event -> showSalesArchive());
        header.returnsItem().setOnAction(event -> showReturns());
    }

    private void runActivePageCleanup() {
        try {
            activePageCleanup.run();
        } catch (RuntimeException exception) {
            System.err.println("Errore durante la chiusura della pagina: " + exception.getMessage());
        }
    }

    public void dispose() {
        runActivePageCleanup();
        activePageCleanup = () -> { };
        root.setCenter(null);
    }
}

package com.orodent.ecos.statistiche.features.sales.products.controller;

import com.orodent.ecos.statistiche.navigation.StatisticheNavigator;
import com.orodent.ecos.statistiche.features.sales.products.model.*;
import com.orodent.ecos.statistiche.features.sales.products.service.ProductAnalysisService;
import com.orodent.ecos.statistiche.features.sales.products.view.ProductsView;
import javafx.application.Platform;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Coordinates asynchronous work; stale responses cannot replace a newer year, grouping or selection. */
public final class ProductsController {
    private final ProductsView view;
    private final ProductAnalysisService service;
    private ProductSalesSnapshot snapshot;
    private ProductGroupKey selected;
    private CompletableFuture<ProductOverview> overviewLoad;
    private CompletableFuture<ProductDetailData> detailLoad;
    private boolean disposed;

    public ProductsController(ProductsView view, ProductAnalysisService service, StatisticheNavigator navigator) {
        this.view = view; this.service = service;
        view.onYearChanged(this::load);
        view.onGroupingChanged(grouping -> {
            if (!disposed && snapshot != null) showOverview(service.regroup(snapshot, grouping));
        });
        view.onSelected(this::detail);
        view.refreshButton().setOnAction(event -> load(view.yearBox().getValue()));
        view.onCustomer(code -> { if (!disposed && snapshot != null) navigator.showCustomer(code, snapshot.year()); });
    }
    public void loadInitialData() { load(null); }
    private void load(Integer year) {
        if (disposed) return;
        showOverview(service.load(year, view.grouping()));
    }
    private void showOverview(CompletableFuture<ProductOverview> future) {
        detailLoad = null; overviewLoad = future; view.showLoading();
        future.whenComplete((data, error) -> Platform.runLater(() -> {
            if (disposed || overviewLoad != future) return;
            overviewLoad = null;
            if (error != null) { snapshot = null; view.showError(unwrap(error)); }
            else { snapshot = data.snapshot(); view.showOverview(data, selected); }
        }));
    }
    private void detail(ProductGroupSummary group) {
        if (disposed || snapshot == null) return;
        if (group == null) { detailLoad = null; return; }
        selected = group.key(); view.showDetailLoading(selected);
        CompletableFuture<ProductDetailData> future = service.detail(snapshot, group); detailLoad = future;
        future.whenComplete((data, error) -> Platform.runLater(() -> {
            if (disposed || detailLoad != future) return;
            detailLoad = null;
            if (error == null) view.showDetail(data); else view.showDetailError(unwrap(error));
        }));
    }
    private Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
    public void dispose() { disposed = true; overviewLoad = null; detailLoad = null; view.dispose(); }
}

package com.orodent.ecos.statistiche.features.sales.customers.controller;

import com.orodent.ecos.statistiche.features.sales.analysis.model.CustomerAnalysisItem;
import com.orodent.ecos.statistiche.features.sales.analysis.service.SalesAnalysisService;
import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerDetailData;
import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerMonthlyValue;
import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerOverviewData;
import com.orodent.ecos.statistiche.features.sales.customers.view.CustomersView;
import javafx.application.Platform;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class CustomersController {
    private final CustomersView view;
    private final SalesAnalysisService service;
    private CompletableFuture<CustomerOverviewData> overviewLoad;
    private CompletableFuture<CustomerDetailData> detailLoad;
    private CompletableFuture<List<CustomerMonthlyValue>> monthlyLoad;
    private CustomerAnalysisItem selectedCustomer;
    private CustomerSelection rememberedSelection;
    private boolean disposed;

    public CustomersController(CustomersView view, SalesAnalysisService service) {
        this.view = view;
        this.service = service;
        view.onYearChanged(this::loadOverview);
        view.onCustomerSelected(this::loadDetail);
        view.onComparisonYearsChanged(() -> {
            if (selectedCustomer != null) loadMonthlyHistory();
        });
    }

    private void loadMonthlyHistory() {
        view.showMonthlyLoading();
        CompletableFuture<List<CustomerMonthlyValue>> future =
                service.loadCustomerMonthlyHistory(
                        selectedCustomer.code(), view.yearBox().getValue(), view.comparisonYears()
                );
        monthlyLoad = future;
        future.whenComplete((data, error) -> Platform.runLater(() -> {
            if (monthlyLoad != future) return;
            monthlyLoad = null;
            if (disposed) return;
            if (error == null) view.showMonthlyHistory(data); else view.showMonthlyError(unwrap(error));
        }));
    }

    public void loadInitialData() { loadOverview(null); }

    public void loadInitialData(String customerCode, int year) {
        rememberedSelection = new CustomerSelection(customerCode, customerCode);
        loadOverview(year);
    }

    private void loadOverview(Integer year) {
        if (disposed || overviewLoad != null) return;
        detailLoad = null;
        monthlyLoad = null;
        view.showLoading();
        CompletableFuture<CustomerOverviewData> future = service.loadCustomerOverview(year);
        overviewLoad = future;
        future.whenComplete((data, error) -> Platform.runLater(() -> {
            if (overviewLoad != future) return;
            overviewLoad = null;
            if (disposed) return;
            if (error == null) {
                view.showOverview(data);
                restoreSelectedCustomer(data);
            } else {
                view.showError(unwrap(error));
            }
        }));
    }

    private void restoreSelectedCustomer(CustomerOverviewData overview) {
        if (rememberedSelection == null) return;
        overview.customers().items().stream()
                .filter(customer -> customer.code().equals(rememberedSelection.code()))
                .findFirst()
                .ifPresentOrElse(customer -> {
                    selectedCustomer = customer;
                    rememberedSelection = new CustomerSelection(customer.code(), customer.name());
                    view.selectCustomerSilently(customer);
                    loadDetail(customer, false);
                }, () -> {
                    selectedCustomer = null;
                    view.clearCustomerSelection();
                    view.showCustomerUnavailable(rememberedSelection.name(), rememberedSelection.code(),
                            overview.customers().selectedYear());
                });
    }

    private void loadDetail(CustomerAnalysisItem customer) {
        loadDetail(customer, true);
    }

    private void loadDetail(CustomerAnalysisItem customer, boolean rememberSelection) {
        selectedCustomer = customer;
        if (rememberSelection) {
            rememberedSelection = new CustomerSelection(customer.code(), customer.name());
        }
        monthlyLoad = null;
        view.showDetailLoading(customer);
        CompletableFuture<CustomerDetailData> future = service.loadCustomerDetail(
                customer.code(), view.yearBox().getValue(), view.comparisonYears());
        detailLoad = future;
        future.whenComplete((data, error) -> Platform.runLater(() -> {
            if (detailLoad != future) return;
            detailLoad = null;
            if (disposed) return;
            if (error == null) view.showDetail(data); else view.showDetailError(unwrap(error));
        }));
    }

    private Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        overviewLoad = null;
        detailLoad = null;
        monthlyLoad = null;
        view.dispose();
    }

    private record CustomerSelection(String code, String name) { }
}

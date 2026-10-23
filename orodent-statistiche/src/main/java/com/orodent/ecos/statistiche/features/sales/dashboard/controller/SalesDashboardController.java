package com.orodent.ecos.statistiche.features.sales.dashboard.controller;

import com.orodent.ecos.statistiche.navigation.StatisticheNavigator;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesDashboardData;
import com.orodent.ecos.statistiche.features.sales.dashboard.service.SalesDashboardService;
import com.orodent.ecos.statistiche.features.sales.dashboard.view.SalesDashboardView;
import com.orodent.ecos.statistiche.features.sales.dashboard.view.SalesDashboardView.PeriodPreset;
import javafx.application.Platform;

import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class SalesDashboardController {

    private final SalesDashboardView view;
    private final SalesDashboardService service;
    private CompletableFuture<SalesDashboardData> activeLoad;
    private boolean disposed;

    public SalesDashboardController(
            SalesDashboardView view,
            SalesDashboardService service,
            StatisticheNavigator navigator
    ) {
        this.view = Objects.requireNonNull(view, "view");
        this.service = Objects.requireNonNull(service, "service");
        Objects.requireNonNull(navigator, "navigator");
        view.applyButton().setOnAction(event -> loadSelectedPeriod());
        view.yearBox().setOnAction(event -> selectWholeYear());
        view.periodBox().setOnAction(event -> applyPeriodPreset());
        view.retryButton().setOnAction(event -> loadSelectedPeriod());
        view.importButton().setOnAction(event -> navigator.showSalesImport());
    }

    public void loadInitialData() {
        load(null, null, null);
    }

    private void selectWholeYear() {
        Integer year = view.yearBox().getValue();
        if (year != null) {
            view.fromPicker().setValue(LocalDate.of(year, 1, 1));
            view.toPicker().setValue(LocalDate.of(year, 12, 31));
        }
    }

    private void loadSelectedPeriod() {
        load(view.yearBox().getValue(), view.fromPicker().getValue(), view.toPicker().getValue());
    }

    private void applyPeriodPreset() {
        Integer year = view.yearBox().getValue();
        PeriodPreset preset = view.periodBox().getValue();
        if (year == null || preset == null || preset == PeriodPreset.CUSTOM) {
            return;
        }
        int firstMonth = switch (preset) {
            case WHOLE_YEAR, FIRST_QUARTER -> 1;
            case SECOND_QUARTER -> 4;
            case THIRD_QUARTER -> 7;
            case FOURTH_QUARTER -> 10;
            case CUSTOM -> throw new IllegalStateException("Periodo personalizzato inatteso");
        };
        int lastMonth = preset == PeriodPreset.WHOLE_YEAR ? 12 : firstMonth + 2;
        view.fromPicker().setValue(LocalDate.of(year, firstMonth, 1));
        view.toPicker().setValue(LocalDate.of(year, lastMonth, 1).withDayOfMonth(
                LocalDate.of(year, lastMonth, 1).lengthOfMonth()
        ));
    }

    private void load(Integer year, LocalDate from, LocalDate to) {
        if (activeLoad != null) {
            return;
        }
        view.showLoading();
        CompletableFuture<SalesDashboardData> future = service.load(year, from, to);
        activeLoad = future;
        future.whenComplete((data, error) -> Platform.runLater(() -> completeLoad(future, data, error)));
    }

    private void completeLoad(
            CompletableFuture<SalesDashboardData> completedFuture,
            SalesDashboardData data,
            Throwable error
    ) {
        if (activeLoad != completedFuture) {
            return;
        }
        activeLoad = null;
        if (disposed) {
            return;
        }
        if (error == null) {
            view.showData(data);
        } else {
            view.showError(unwrap(error));
        }
    }

    private Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        activeLoad = null;
        view.dispose();
    }
}

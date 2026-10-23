package com.orodent.ecos.statistiche.features.sales.analysis.controller;

import com.orodent.ecos.statistiche.features.sales.analysis.model.AnalysisPageData;
import com.orodent.ecos.statistiche.features.sales.analysis.view.AnalysisView;
import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public class AnalysisController<T> {
    protected final AnalysisView<T> view;
    private final Function<Integer, CompletableFuture<AnalysisPageData<T>>> loader;
    private CompletableFuture<AnalysisPageData<T>> activeLoad;
    private boolean disposed;

    public AnalysisController(AnalysisView<T> view,
                              Function<Integer, CompletableFuture<AnalysisPageData<T>>> loader) {
        this.view = view;
        this.loader = loader;
        view.refreshButton().setOnAction(event -> load(view.yearBox().getValue()));
    }

    public void loadInitialData() { load(null); }
    protected void load(Integer year) {
        if (activeLoad != null) return;
        view.showLoading();
        CompletableFuture<AnalysisPageData<T>> future = loader.apply(year);
        activeLoad = future;
        future.whenComplete((data, error) -> Platform.runLater(() -> {
            if (activeLoad != future) return;
            activeLoad = null;
            if (disposed) return;
            if (error == null) view.showData(data); else view.showError(unwrap(error));
        }));
    }

    private Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    public void dispose() { disposed = true; activeLoad = null; }
}

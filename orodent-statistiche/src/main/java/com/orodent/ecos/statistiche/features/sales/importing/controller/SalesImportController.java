package com.orodent.ecos.statistiche.features.sales.importing.controller;

import com.orodent.ecos.database.service.VenditeCsvImportService;
import com.orodent.ecos.database.service.VenditeImportReport;
import com.orodent.ecos.statistiche.features.sales.importing.view.SalesImportView;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class SalesImportController {

    private final SalesImportView view;
    private final VenditeCsvImportService importService;
    private Path selectedFile;
    private CompletableFuture<VenditeImportReport> activeImport;
    private boolean disposed;

    public SalesImportController(SalesImportView view, VenditeCsvImportService importService) {
        this.view = Objects.requireNonNull(view, "view");
        this.importService = Objects.requireNonNull(importService, "importService");
        view.selectFileButton().setOnAction(event -> selectFile());
        view.importButton().setOnAction(event -> importSelectedFile());
        view.onFileDropped(this::setSelectedFile);
    }

    private void selectFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleziona il file delle vendite");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("File CSV", "*.csv"));
        Window owner = view.getScene() == null ? null : view.getScene().getWindow();
        java.io.File file = chooser.showOpenDialog(owner);
        if (file != null) {
            setSelectedFile(file.toPath());
        }
    }

    private void setSelectedFile(Path path) {
        if (activeImport != null || !isCsv(path)) {
            return;
        }
        selectedFile = path;
        view.showSelectedFile(path);
    }

    private boolean isCsv(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".csv");
    }

    private void importSelectedFile() {
        if (selectedFile == null || activeImport != null) {
            return;
        }
        view.showImporting();
        CompletableFuture<VenditeImportReport> future = importService.importFile(selectedFile);
        activeImport = future;
        future.whenComplete((report, error) -> Platform.runLater(() -> completeImport(future, report, error)));
    }

    private void completeImport(
            CompletableFuture<VenditeImportReport> completedFuture,
            VenditeImportReport report,
            Throwable error
    ) {
        if (activeImport != completedFuture) {
            return;
        }
        activeImport = null;
        if (disposed) {
            return;
        }
        if (error != null) {
            view.showUnexpectedError(unwrap(error));
        } else {
            view.showReport(report);
        }
    }

    private Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    public void dispose() {
        disposed = true;
        activeImport = null;
    }
}

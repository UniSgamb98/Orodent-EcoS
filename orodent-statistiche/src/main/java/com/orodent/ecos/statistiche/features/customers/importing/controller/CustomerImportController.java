package com.orodent.ecos.statistiche.features.customers.importing.controller;

import com.orodent.ecos.database.service.ClientiCsvImportService;
import com.orodent.ecos.database.service.ClientiImportReport;
import com.orodent.ecos.statistiche.features.customers.importing.view.CustomerImportView;
import javafx.application.Platform;
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class CustomerImportController {
    private final CustomerImportView view;
    private final ClientiCsvImportService service;
    private Path selectedFile;
    private CompletableFuture<ClientiImportReport> activeImport;
    private boolean disposed;

    public CustomerImportController(CustomerImportView view, ClientiCsvImportService service) {
        this.view = view;
        this.service = service;
        view.selectButton().setOnAction(event -> chooseFile());
        view.importButton().setOnAction(event -> importFile());
        view.onFileDropped(this::selectFile);
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("File CSV", "*.csv"));
        java.io.File file = chooser.showOpenDialog(view.getScene() == null ? null : view.getScene().getWindow());
        if (file != null) selectFile(file.toPath());
    }

    private void selectFile(Path path) {
        if (activeImport == null && path.getFileName().toString().toLowerCase().endsWith(".csv")) {
            selectedFile = path;
            view.showSelectedFile(path);
        }
    }

    private void importFile() {
        if (selectedFile == null || activeImport != null) return;
        view.showImporting();
        CompletableFuture<ClientiImportReport> future = service.importFile(selectedFile);
        activeImport = future;
        future.whenComplete((report, error) -> Platform.runLater(() -> {
            if (activeImport != future) return;
            activeImport = null;
            if (disposed) return;
            if (error == null) view.showReport(report); else view.showError(unwrap(error));
        }));
    }

    private Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    public void dispose() {
        disposed = true;
        activeImport = null;
    }
}

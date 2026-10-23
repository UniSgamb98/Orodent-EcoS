package com.orodent.ecos.statistiche.features.customers.importing.view;

import com.orodent.ecos.statistiche.core.components.AppHeader;
import com.orodent.ecos.database.service.ClientiImportReport;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.function.Consumer;

public final class CustomerImportView extends BorderPane {
    private final AppHeader header = new AppHeader("Importa anagrafica clienti", "Aggiorna codici e denominazioni sociali");
    private final VBox dropArea = new VBox(10);
    private final Label fileLabel = new Label("Nessun file selezionato");
    private final Button selectButton = new Button("Seleziona file CSV");
    private final Button importButton = new Button("Importa anagrafica");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final VBox result = new VBox(10);
    private final Label resultTitle = new Label();
    private final Label resultMessage = new Label();
    private final TextArea errors = new TextArea();
    private Consumer<Path> dropped = path -> { };

    public CustomerImportView() {
        getStyleClass().add("page");
        setTop(header);
        setCenter(buildContent());
        configureDrop();
        progress.setVisible(false);
        progress.setManaged(false);
        importButton.setDisable(true);
        result.setVisible(false);
        result.setManaged(false);
    }

    public AppHeader header() { return header; }
    public Button selectButton() { return selectButton; }
    public Button importButton() { return importButton; }
    public void onFileDropped(Consumer<Path> handler) { dropped = handler; }

    public void showSelectedFile(Path path) {
        fileLabel.setText(path.getFileName().toString());
        importButton.setDisable(false);
        hideResult();
    }

    public void showImporting() {
        selectButton.setDisable(true);
        importButton.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);
        hideResult();
    }

    public void showReport(ClientiImportReport report) {
        finishLoading();
        if (report.imported()) {
            result.getStyleClass().setAll("result-card", "result-success");
            resultTitle.setText("Anagrafica aggiornata");
            resultMessage.setText(report.insertedRows() + " clienti importati; "
                    + report.deletedRows() + " clienti precedenti sostituiti.");
            errors.setVisible(false);
            errors.setManaged(false);
        } else {
            result.getStyleClass().setAll("result-card", "result-error");
            resultTitle.setText("Importazione non eseguita");
            resultMessage.setText("L'anagrafica precedente non è stata modificata.");
            StringBuilder details = new StringBuilder();
            report.documentErrors().forEach(error -> details.append("Riga ").append(error.lineNumber())
                    .append(": ").append(error.message()).append('\n'));
            report.rowErrors().forEach(error -> details.append("Riga ").append(error.lineNumber())
                    .append(" - ").append(error.field()).append(": ").append(error.message()).append('\n'));
            errors.setText(details.toString());
            errors.setVisible(true);
            errors.setManaged(true);
        }
        result.setVisible(true);
        result.setManaged(true);
    }

    public void showError(Throwable error) {
        finishLoading();
        result.getStyleClass().setAll("result-card", "result-error");
        resultTitle.setText("Errore durante l'importazione");
        resultMessage.setText(error == null || error.getMessage() == null
                ? "I dati precedenti sono stati conservati." : error.getMessage());
        result.setVisible(true);
        result.setManaged(true);
    }

    private VBox buildContent() {
        VBox content = new VBox(22);
        content.setPadding(new Insets(30, 36, 36, 36));
        Label title = new Label("Seleziona l'esportazione completa della rubrica");
        title.getStyleClass().add("section-title");
        Label description = new Label("Le denominazioni sociali su più righe vengono ricomposte automaticamente.");
        description.getStyleClass().add("muted-label");
        dropArea.getStyleClass().add("drop-area");
        dropArea.setAlignment(Pos.CENTER);
        dropArea.setPadding(new Insets(32));
        Label dropTitle = new Label("Trascina qui il file CSV");
        dropTitle.getStyleClass().add("drop-title");
        selectButton.getStyleClass().add("secondary-button");
        progress.setMaxSize(36, 36);
        dropArea.getChildren().addAll(dropTitle, selectButton, fileLabel, progress);
        Label warning = new Label("L'importazione sostituirà l'anagrafica clienti, ma non modificherà le vendite.");
        warning.getStyleClass().add("warning-banner");
        warning.setWrapText(true);
        importButton.getStyleClass().add("primary-button");
        HBox actions = new HBox(importButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        resultTitle.getStyleClass().add("result-title");
        errors.setEditable(false);
        errors.setPrefRowCount(8);
        result.getChildren().addAll(resultTitle, resultMessage, errors);
        content.getChildren().addAll(title, description, dropArea, warning, actions, result);
        return content;
    }

    private void configureDrop() {
        dropArea.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles() && event.getDragboard().getFiles().size() == 1) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        dropArea.setOnDragDropped(event -> {
            boolean accepted = event.getDragboard().hasFiles() && event.getDragboard().getFiles().size() == 1;
            if (accepted) dropped.accept(event.getDragboard().getFiles().getFirst().toPath());
            event.setDropCompleted(accepted);
            event.consume();
        });
    }

    private void finishLoading() {
        selectButton.setDisable(false);
        importButton.setDisable(false);
        progress.setVisible(false);
        progress.setManaged(false);
    }

    private void hideResult() {
        result.setVisible(false);
        result.setManaged(false);
    }
}

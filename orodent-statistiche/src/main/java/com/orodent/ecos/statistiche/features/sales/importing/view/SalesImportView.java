package com.orodent.ecos.statistiche.features.sales.importing.view;

import com.orodent.ecos.statistiche.core.components.AppHeader;
import com.orodent.ecos.core.csv.CsvImportResult;
import com.orodent.ecos.database.service.VenditeImportReport;
import javafx.beans.property.SimpleStringProperty;
import com.orodent.ecos.statistiche.core.components.TableColumns;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class SalesImportView extends BorderPane {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AppHeader header = new AppHeader(
            "Importa vendite",
            "Aggiorna in sicurezza le vendite comprese nell'esportazione"
    );
    private final VBox dropArea = new VBox(10);
    private final Label selectedFileLabel = new Label("Nessun file selezionato");
    private final Button selectFileButton = new Button("Seleziona file CSV");
    private final Button importButton = new Button("Importa dati");
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final VBox resultCard = new VBox(12);
    private final Label resultTitle = new Label();
    private final Label resultMessage = new Label();
    private final HBox metrics = new HBox(14);
    private final TableView<ImportErrorItem> errorTable = new TableView<>();
    private Consumer<Path> fileDropped = path -> { };

    public SalesImportView() {
        getStyleClass().add("page");
        setTop(header);
        setCenter(buildContent());
        configureDropArea();
        configureErrorTable();
        showReady();
    }

    public AppHeader header() {
        return header;
    }

    public Button selectFileButton() {
        return selectFileButton;
    }

    public Button importButton() {
        return importButton;
    }

    public void onFileDropped(Consumer<Path> handler) {
        fileDropped = handler;
    }

    public void showSelectedFile(Path path) {
        selectedFileLabel.setText(path.getFileName().toString());
        selectedFileLabel.getStyleClass().add("selected-file-name");
        importButton.setDisable(false);
        hideResult();
    }

    public void showImporting() {
        selectFileButton.setDisable(true);
        importButton.setDisable(true);
        importButton.setText("Importazione in corso…");
        progressIndicator.setVisible(true);
        progressIndicator.setManaged(true);
        dropArea.getStyleClass().add("drop-area-busy");
        hideResult();
    }

    public void showReport(VenditeImportReport report) {
        finishLoading();
        if (report.imported()) {
            resultCard.getStyleClass().setAll("result-card", "result-success");
            resultTitle.setText("Importazione completata");
            resultMessage.setText("Le vendite dal " + report.fromDate().format(DATE_FORMAT)
                    + " al " + report.throughDate().format(DATE_FORMAT)
                    + " sono state sostituite correttamente.");
            metrics.getChildren().setAll(
                    metric("Dal", report.fromDate().format(DATE_FORMAT)),
                    metric("Al", report.throughDate().format(DATE_FORMAT)),
                    metric("Righe lette", String.valueOf(report.totalRows())),
                    metric("Righe eliminate", String.valueOf(report.deletedRows())),
                    metric("Righe inserite", String.valueOf(report.insertedRows()))
            );
            errorTable.setVisible(false);
            errorTable.setManaged(false);
        } else {
            resultCard.getStyleClass().setAll("result-card", "result-error");
            resultTitle.setText("Importazione non eseguita");
            resultMessage.setText("Il database non è stato modificato. Correggi il file e riprova.");
            metrics.getChildren().clear();
            showErrors(report);
        }
        resultCard.setVisible(true);
        resultCard.setManaged(true);
    }

    public void showUnexpectedError(Throwable error) {
        finishLoading();
        resultCard.getStyleClass().setAll("result-card", "result-error");
        resultTitle.setText("Errore durante l'importazione");
        String message = error == null ? null : error.getMessage();
        resultMessage.setText(message == null || message.isBlank()
                ? "Si è verificato un errore imprevisto. I dati precedenti sono stati conservati."
                : message);
        metrics.getChildren().clear();
        errorTable.setVisible(false);
        errorTable.setManaged(false);
        resultCard.setVisible(true);
        resultCard.setManaged(true);
    }

    private VBox buildContent() {
        VBox content = new VBox(22);
        content.setPadding(new Insets(30, 36, 36, 36));

        Label instruction = new Label("Seleziona l'esportazione completa del gestionale");
        instruction.getStyleClass().add("section-title");
        Label description = new Label(
                "Il file viene controllato prima dell'aggiornamento e può comprendere qualsiasi intervallo di date."
        );
        description.getStyleClass().add("muted-label");
        description.setWrapText(true);

        dropArea.getStyleClass().add("drop-area");
        dropArea.setAlignment(Pos.CENTER);
        dropArea.setPadding(new Insets(32));
        Label dropTitle = new Label("Trascina qui il file CSV");
        dropTitle.getStyleClass().add("drop-title");
        Label separator = new Label("oppure");
        separator.getStyleClass().add("muted-label");
        selectFileButton.getStyleClass().add("secondary-button");
        selectedFileLabel.getStyleClass().add("muted-label");
        progressIndicator.setMaxSize(36, 36);
        dropArea.getChildren().addAll(dropTitle, separator, selectFileButton, selectedFileLabel, progressIndicator);

        HBox warning = new HBox(10);
        warning.getStyleClass().add("warning-banner");
        Label warningIcon = new Label("!");
        warningIcon.getStyleClass().add("warning-icon");
        Label warningText = new Label(
                "L'importazione sostituirà tutte le vendite comprese tra la data più vecchia e quella più recente "
                        + "del file. In caso di errore, l'operazione verrà annullata."
        );
        warningText.setWrapText(true);
        HBox.setHgrow(warningText, Priority.ALWAYS);
        warning.getChildren().addAll(warningIcon, warningText);

        importButton.getStyleClass().add("primary-button");
        HBox actions = new HBox(importButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        resultTitle.getStyleClass().add("result-title");
        resultMessage.setWrapText(true);
        resultCard.getChildren().addAll(resultTitle, resultMessage, metrics, errorTable);

        content.getChildren().addAll(instruction, description, dropArea, warning, actions, resultCard);
        return content;
    }

    private void configureDropArea() {
        dropArea.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if (dragboard.hasFiles() && dragboard.getFiles().size() == 1) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        dropArea.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean accepted = dragboard.hasFiles() && dragboard.getFiles().size() == 1;
            if (accepted) {
                fileDropped.accept(dragboard.getFiles().getFirst().toPath());
            }
            event.setDropCompleted(accepted);
            event.consume();
        });
    }

    private void configureErrorTable() {
        var lineColumn = TableColumns.<ImportErrorItem, Integer>value("Riga", ImportErrorItem::lineNumber, String::valueOf);
        lineColumn.setPrefWidth(70);

        TableColumn<ImportErrorItem, String> fieldColumn = new TableColumn<>("Colonna");
        fieldColumn.setCellValueFactory(item -> new SimpleStringProperty(item.getValue().field()));
        fieldColumn.setPrefWidth(190);

        TableColumn<ImportErrorItem, String> valueColumn = new TableColumn<>("Valore");
        valueColumn.setCellValueFactory(item -> new SimpleStringProperty(item.getValue().rawValue()));
        valueColumn.setPrefWidth(150);

        TableColumn<ImportErrorItem, String> messageColumn = new TableColumn<>("Errore");
        messageColumn.setCellValueFactory(item -> new SimpleStringProperty(item.getValue().message()));
        messageColumn.setPrefWidth(400);

        errorTable.getColumns().addAll(lineColumn, fieldColumn, valueColumn, messageColumn);
        errorTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        errorTable.setPrefHeight(220);
    }

    private void showErrors(VenditeImportReport report) {
        List<ImportErrorItem> errors = new ArrayList<>();
        for (CsvImportResult.DocumentError error : report.documentErrors()) {
            errors.add(new ImportErrorItem(error.lineNumber(), "Documento", "", error.message()));
        }
        for (CsvImportResult.RowError error : report.rowErrors()) {
            errors.add(new ImportErrorItem(
                    error.lineNumber(),
                    error.field(),
                    error.rawValue() == null ? "" : error.rawValue(),
                    error.message()
            ));
        }
        errorTable.getItems().setAll(errors);
        errorTable.setVisible(true);
        errorTable.setManaged(true);
    }

    private VBox metric(String label, String value) {
        VBox metric = new VBox(4);
        metric.getStyleClass().add("metric-card");
        metric.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(metric, Priority.ALWAYS);
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("metric-value");
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("muted-label");
        metric.getChildren().addAll(valueLabel, labelNode);
        return metric;
    }

    private void showReady() {
        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);
        importButton.setDisable(true);
        hideResult();
    }

    private void finishLoading() {
        selectFileButton.setDisable(false);
        importButton.setDisable(false);
        importButton.setText("Importa dati");
        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);
        dropArea.getStyleClass().remove("drop-area-busy");
    }

    private void hideResult() {
        resultCard.setVisible(false);
        resultCard.setManaged(false);
    }

    public record ImportErrorItem(int lineNumber, String field, String rawValue, String message) {
    }
}

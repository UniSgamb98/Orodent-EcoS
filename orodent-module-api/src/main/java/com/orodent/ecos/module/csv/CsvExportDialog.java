package com.orodent.ecos.module.csv;

import com.orodent.ecos.core.csv.*;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

/** Reusable preview/save flow. Data is an immutable snapshot; the supplied executor is borrowed. */
public final class CsvExportDialog {
    private CsvExportDialog() {}

    public static <T> void show(
            Window owner,
            String title,
            String scope,
            String fileName,
            List<T> data,
            CsvExportMapper<T> mapper,
            Executor workers) {
        List<T> snapshot = List.copyOf(data);
        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(scope);
        ButtonType save = new ButtonType("Salva CSV", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CLOSE);
        TableView<List<?>> preview = new TableView<>();
        preview.setId("csv-export-preview");
        preview.setPrefSize(840, 280);
        preview.setPlaceholder(new Label("Nessuna riga da esportare"));
        List<String> headers = List.copyOf(mapper.headers());
        for (int i = 0; i < headers.size(); i++) {
            final int columnIndex = i;
            TableColumn<List<?>, String> column = new TableColumn<>(headers.get(i));
            column.setPrefWidth(155);
            column.setSortable(false);
            column.setCellValueFactory(
                    c -> {
                        Object value = c.getValue().get(columnIndex);
                        return new ReadOnlyStringWrapper(
                                value == null
                                        ? ""
                                        : value instanceof BigDecimal n
                                                ? n.toPlainString().replace('.', ',')
                                                : value.toString());
                    });
            preview.getColumns().add(column);
        }
        for (T row : snapshot.stream().limit(8).toList()) preview.getItems().add(mapper.map(row));
        Label status =
                new Label(
                        snapshot.size()
                                + " righe · Anteprima delle prime 8 · CSV per Excel italiano");
        status.setWrapText(true);
        status.setId("csv-export-status");
        VBox content = new VBox(12, status, preview);
        dialog.getDialogPane().setContent(content);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        Button close = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        saveButton.setId("csv-export-save");
        saveButton.setDisable(snapshot.isEmpty());
        boolean[] writing = {false};
        dialog.setOnCloseRequest(
                e -> {
                    if (writing[0]) e.consume();
                });
        saveButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                e -> {
                    e.consume();
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Salva il file CSV");
                    chooser.setInitialFileName(fileName);
                    chooser.getExtensionFilters()
                            .add(new FileChooser.ExtensionFilter("File CSV", "*.csv"));
                    var chosen =
                            chooser.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
                    if (chosen == null) return;
                    writing[0] = true;
                    saveButton.setDisable(true);
                    close.setDisable(true);
                    status.setText("Esportazione in corso…");
                    try {
                        CompletableFuture.supplyAsync(
                                        () ->
                                                new CsvExporter<>(new CsvWriter(), mapper)
                                                        .exportFile(
                                                                chosen.toPath(),
                                                                snapshot,
                                                                CsvWriteOptions.excelItalian()),
                                        workers)
                                .whenComplete(
                                        (result, error) ->
                                                Platform.runLater(
                                                        () -> {
                                                            writing[0] = false;
                                                            close.setDisable(false);
                                                            if (error == null) {
                                                                status.setText(
                                                                        "Esportate "
                                                                                + result
                                                                                        .exportedRows()
                                                                                + " righe in "
                                                                                + result.path());
                                                                saveButton.setText(
                                                                        "Esporta di nuovo");
                                                            } else {
                                                                Throwable cause = error;
                                                                while (cause
                                                                                instanceof
                                                                                CompletionException
                                                                        && cause.getCause() != null)
                                                                    cause = cause.getCause();
                                                                status.setText(
                                                                        "Esportazione non riuscita:"
                                                                            + " "
                                                                                + cause
                                                                                        .getMessage());
                                                            }
                                                            saveButton.setDisable(false);
                                                        }));
                    } catch (RuntimeException failure) {
                        writing[0] = false;
                        close.setDisable(false);
                        saveButton.setDisable(false);
                        status.setText("Esportazione non riuscita: " + failure.getMessage());
                    }
                });
        dialog.setResizable(true);
        dialog.show();
    }
}

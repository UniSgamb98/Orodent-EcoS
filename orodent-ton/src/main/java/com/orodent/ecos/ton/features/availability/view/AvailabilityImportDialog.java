package com.orodent.ecos.ton.features.availability.view;

import static com.orodent.ecos.ton.features.availability.model.Availability.*;

import com.orodent.ecos.core.csv.CsvDocument;
import com.orodent.ecos.ton.features.availability.service.AvailabilityService;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** The reviewed, immutable preview is applied once; edits invalidate it. */
public final class AvailabilityImportDialog extends Dialog<Void> {
    private final AvailabilityService service;
    private final Kind kind;
    private final Path path;
    private final Consumer<Boolean> saving;
    private final Runnable success;
    private final ComboBox<String> delimiter = new ComboBox<>(),
            encoding = new ComboBox<>(),
            code = new ComboBox<>(),
            quantity = new ComboBox<>();
    private final ComboBox<Numbers> numbers = new ComboBox<>();
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final CheckBox duplicates = new CheckBox("Somma le righe con lo stesso codice"),
            zero = new CheckBox("Considera zero i codici assenti dal file");
    private final Button read = new Button("Leggi file"), previewButton = new Button("Anteprima");
    private final Label status = new Label();
    private final TableView<Map.Entry<String, java.math.BigDecimal>> table = new TableView<>();
    private final Button apply;
    private CsvDocument document;
    private Preview preview;
    private boolean updating, busy, writing, closed;
    private long generation;
    private CompletableFuture<?> pending;

    public AvailabilityImportDialog(
            Window owner,
            Kind kind,
            Path path,
            AvailabilityService service,
            Consumer<Boolean> saving,
            Runnable success) {
        this.service = service;
        this.kind = kind;
        this.path = path;
        this.saving = saving;
        this.success = success;
        if (owner != null) initOwner(owner);
        setTitle("Importa " + kind.label().toLowerCase(Locale.ITALIAN));
        getDialogPane().setId("ton-availability-import-dialog");
        getDialogPane().setPrefSize(900, 690);
        var applyType = new ButtonType("Importa", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, applyType);
        apply = (Button) getDialogPane().lookupButton(applyType);
        apply.setId("ton-availability-import-apply");
        apply.setDisable(true);
        delimiter.getItems().setAll("Punto e virgola (;) ", "Virgola (,)", "Tabulazione");
        delimiter.getSelectionModel().selectFirst();
        delimiter.setId("ton-availability-import-separator");
        encoding.getItems().setAll("UTF-8", "windows-1252");
        encoding.getSelectionModel().selectFirst();
        encoding.setId("ton-availability-import-encoding");
        numbers.getItems().setAll(Numbers.values());
        numbers.setValue(Numbers.ITALIAN);
        numbers.setId("ton-availability-import-numbers");
        code.setId("ton-availability-import-code");
        quantity.setId("ton-availability-import-quantity");
        date.setId("ton-availability-import-date");
        duplicates.setId("ton-availability-import-duplicates");
        zero.setId("ton-availability-import-zero");
        duplicates.setSelected(kind == Kind.ORDINI);
        read.setId("ton-availability-import-read");
        previewButton.setId("ton-availability-import-preview");
        status.setId("ton-availability-import-status");
        status.setWrapText(true);
        table.setId("ton-availability-import-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPrefHeight(240);
        table.setPlaceholder(new Label("Scegli le colonne e genera l’anteprima."));
        TechnicalUi.column(table, "Codice CSV", 200, Map.Entry::getKey);
        TechnicalUi.column(table, "Quantità", 140, e -> AvailabilityView.number(e.getValue()));
        TechnicalUi.column(
                table,
                "Articolo TON",
                220,
                e ->
                        preview == null
                                ? ""
                                : preview.matches().getOrDefault(e.getKey(), "Non associato"));
        var note =
                TechnicalUi.label(
                        "Il file sostituisce l’ultima importazione di questa fonte. Le altre fonti"
                            + " restano indipendenti.",
                        "muted-label");
        HBox format =
                new HBox(
                        12,
                        TechnicalUi.field("Separatore", delimiter),
                        TechnicalUi.field("Codifica", encoding),
                        read);
        format.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
        HBox fields =
                new HBox(
                        12,
                        TechnicalUi.field("Colonna codice", code),
                        TechnicalUi.field(
                                kind == Kind.ORDINI
                                        ? "Quantità residua da evadere"
                                        : "Colonna disponibilità",
                                quantity),
                        TechnicalUi.field("Numeri", numbers),
                        TechnicalUi.field("Data dei dati", date));
        for (var n : List.of(code, quantity)) {
            n.setMaxWidth(Double.MAX_VALUE);
        }
        VBox box =
                new VBox(
                        12,
                        new Label(path.getFileName().toString()),
                        note,
                        format,
                        fields,
                        duplicates,
                        zero,
                        previewButton,
                        status,
                        table);
        box.setStyle("-fx-padding: 12px;");
        getDialogPane().setContent(box);
        delimiter.valueProperty().addListener((o, a, b) -> resetDocument());
        encoding.valueProperty().addListener((o, a, b) -> resetDocument());
        code.valueProperty().addListener((o, a, b) -> invalidate());
        quantity.valueProperty().addListener((o, a, b) -> invalidate());
        numbers.valueProperty().addListener((o, a, b) -> invalidate());
        date.valueProperty().addListener((o, a, b) -> invalidate());
        duplicates.selectedProperty().addListener((o, a, b) -> invalidate());
        zero.selectedProperty().addListener((o, a, b) -> invalidate());
        read.setOnAction(e -> readFile());
        previewButton.setOnAction(e -> preview());
        apply.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                e -> {
                    e.consume();
                    apply();
                });
        setOnCloseRequest(
                e -> {
                    if (writing) e.consume();
                });
        setOnHidden(
                e -> {
                    closed = true;
                    generation++;
                    if (pending != null) pending.cancel(false);
                });
        readFile();
    }

    private void resetDocument() {
        if (updating || busy) return;
        document = null;
        updating = true;
        code.getItems().clear();
        quantity.getItems().clear();
        updating = false;
        invalidate();
    }

    private void invalidate() {
        if (updating || writing) return;
        generation++;
        if (pending != null) pending.cancel(false);
        preview = null;
        apply.setDisable(true);
        table.getItems().clear();
        status.setText("");
        previewButton.setDisable(document == null);
    }

    private char separator() {
        return switch (delimiter.getSelectionModel().getSelectedIndex()) {
            case 1 -> ',';
            case 2 -> '\t';
            default -> ';';
        };
    }

    private void readFile() {
        if (busy) return;
        document = null;
        preview = null;
        busy(true);
        long token = ++generation;
        pending =
                service.read(path, separator(), Charset.forName(encoding.getValue()))
                        .whenComplete(
                                (value, error) ->
                                        Platform.runLater(
                                                () -> {
                                                    if (closed || generation != token) return;
                                                    busy(false);
                                                    if (error != null) {
                                                        status.setText(message(error));
                                                        return;
                                                    }
                                                    document = value;
                                                    updating = true;
                                                    code.getItems().setAll(value.headers());
                                                    quantity.getItems().setAll(value.headers());
                                                    code.setValue(
                                                            guess(
                                                                    value.headers(),
                                                                    "articolo",
                                                                    "codice prodotto",
                                                                    "codice",
                                                                    "codice articolo"));
                                                    quantity.setValue(
                                                            kind == Kind.ORDINI
                                                                    ? guess(
                                                                            value.headers(),
                                                                            "da evadere",
                                                                            "quantità residua",
                                                                            "quantita residua",
                                                                            "da consegn.",
                                                                            "residuo",
                                                                            "quantità",
                                                                            "quantita")
                                                                    : guess(
                                                                            value.headers(),
                                                                            "disponibilità",
                                                                            "disponibilita",
                                                                            "giacenza",
                                                                            "quantità",
                                                                            "quantita"));
                                                    updating = false;
                                                    status.setText(
                                                            value.rows().size()
                                                                    + " righe lette. Verifica le"
                                                                    + " colonne e il formato dei"
                                                                    + " numeri.");
                                                    previewButton.setDisable(false);
                                                }));
    }

    private static String guess(List<String> headers, String... names) {
        for (String name : names) for (String h : headers) if (h.equalsIgnoreCase(name)) return h;
        return null;
    }

    private void preview() {
        if (busy || document == null) return;
        preview = null;
        busy(true);
        long token = ++generation;
        pending =
                service.preview(
                                kind,
                                document,
                                path.getFileName().toString(),
                                date.getValue(),
                                code.getValue(),
                                quantity.getValue(),
                                numbers.getValue(),
                                duplicates.isSelected(),
                                zero.isSelected())
                        .whenComplete(
                                (value, error) ->
                                        Platform.runLater(
                                                () -> {
                                                    if (closed || generation != token) return;
                                                    busy(false);
                                                    if (error != null) {
                                                        status.setText(message(error));
                                                        return;
                                                    }
                                                    preview = value;
                                                    table.getItems()
                                                            .setAll(
                                                                    value
                                                                            .values()
                                                                            .entrySet()
                                                                            .stream()
                                                                            .limit(100)
                                                                            .toList());
                                                    status.setText(
                                                            value.errors().isEmpty()
                                                                    ? value.values().size()
                                                                            + " codici · "
                                                                            + value.duplicates()
                                                                            + " righe ripetute · "
                                                                            + value.unmatched()
                                                                            + " codici non"
                                                                            + " associati."
                                                                            + " Anteprima dei primi"
                                                                            + " 100 codici.\n"
                                                                            + "I codici non"
                                                                            + " associati restano"
                                                                            + " conservati; non"
                                                                            + " entrano nei totali"
                                                                            + " TON."
                                                                    : String.join(
                                                                            "\n", value.errors()));
                                                    apply.setDisable(!value.errors().isEmpty());
                                                }));
    }

    private void apply() {
        if (busy || preview == null || !preview.errors().isEmpty()) return;
        writing = true;
        saving.accept(true);
        busy(true);
        service.apply(preview)
                .whenComplete(
                        (ignored, error) ->
                                Platform.runLater(
                                        () -> {
                                            writing = false;
                                            saving.accept(false);
                                            if (closed) return;
                                            busy(false);
                                            if (error != null) {
                                                status.setText(message(error));
                                                apply.setDisable(true);
                                                return;
                                            }
                                            success.run();
                                            close();
                                        }));
    }

    private void busy(boolean value) {
        busy = value;
        for (var n :
                List.of(
                        delimiter,
                        encoding,
                        code,
                        quantity,
                        numbers,
                        date,
                        duplicates,
                        zero,
                        read,
                        previewButton)) n.setDisable(value);
        apply.setDisable(value || preview == null);
        getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(writing);
        if (!value) previewButton.setDisable(document == null);
    }

    private static String message(Throwable e) {
        while (e instanceof CompletionException && e.getCause() != null) e = e.getCause();
        return Objects.toString(e.getMessage(), "Importazione non riuscita.");
    }
}

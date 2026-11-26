package com.orodent.ecos.misurazioni.labels.controller;

import static com.orodent.ecos.misurazioni.labels.model.Labels.*;

import com.orodent.ecos.documents.LabelPreviewPane;
import com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.*;
import com.orodent.ecos.misurazioni.labels.service.LabelService;
import com.orodent.ecos.module.ModuleContext;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.util.concurrent.CompletionException;
import java.util.function.Function;

/** One owned modal print session; database work uses the application's borrowed executor. */
public final class LabelPrintingController implements AutoCloseable {
    private final ModuleContext context;
    private final LabelService service;
    private final Function<LabelPreviewPane, Boolean> printJob;
    private final Dialog<Void> dialog = new Dialog<>();
    private final ComboBox<Template> templates = new ComboBox<>();
    private final DatePicker expiry = new DatePicker();
    private final Button print = new Button("Stampa etichetta"),
            previous = new Button("Ristampa ultima etichetta"),
            regenerate = new Button("Crea da modello");
    private final VBox options = new VBox(6);
    private String currentInfo;
    private final Label feedback = new Label("Caricamento dati etichetta…"), info = new Label();
    private final LabelPreviewPane preview = new LabelPreviewPane();
    private Source source;
    private Prepared prepared;
    private Issued issued;
    private boolean closed, saving, restoring, autoRequested;
    private long generation;

    public LabelPrintingController(
            ModuleContext context, LabelService service, Window owner, Key key, Runnable onClosed) {
        this(context, service, owner, key, onClosed, LabelPreviewPane::print);
    }

    public LabelPrintingController(
            ModuleContext context,
            LabelService service,
            Window owner,
            Key key,
            Runnable onClosed,
            Function<LabelPreviewPane, Boolean> printJob) {
        this.context = context;
        this.service = service;
        this.printJob = printJob;
        dialog.setTitle("Stampa etichetta");
        dialog.initOwner(owner);
        dialog.setResizable(true);
        templates.setId("label-template");
        templates.setMaxWidth(Double.MAX_VALUE);
        templates.setPromptText("Modello della specifica di produzione");
        expiry.setId("label-expiry");
        expiry.setPromptText("Solo se usata nel modello");
        print.setId("label-print");
        previous.setId("label-previous");
        preview.setId("label-preview");
        feedback.setId("label-feedback");
        feedback.setWrapText(true);
        info.setWrapText(true);
        info.setMinHeight(Region.USE_PREF_SIZE);
        info.setMaxWidth(Double.MAX_VALUE);
        feedback.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(templates, Priority.ALWAYS);
        options.getChildren()
                .setAll(
                        new Label("Versione del modello etichetta"),
                        templates,
                        new Label("Scadenza (se richiesta dal modello)"),
                        expiry);
        var actions = new HBox(10, print, previous, regenerate);
        var content = new VBox(12, info, options, preview, actions, feedback);
        content.setPadding(new Insets(12));
        content.setPrefSize(820, 600);
        VBox.setVgrow(preview, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setOnCloseRequest(
                e -> {
                    if (saving) e.consume();
                });
        dialog.setOnHidden(
                e -> {
                    close();
                    onClosed.run();
                });
        templates.setOnAction(
                e -> {
                    if (!restoring) render();
                });
        expiry.valueProperty()
                .addListener(
                        (o, b, a) -> {
                            if (!restoring) render();
                        });
        print.setOnAction(e -> print());
        previous.setOnAction(e -> stored());
        regenerate.setOnAction(
                e -> {
                    issued = null;
                    render();
                });
        print.setDisable(true);
        previous.setDisable(true);
        regenerate.setDisable(true);
        dialog.show();
        service.source(key)
                .whenComplete(
                        (value, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (closed) return;
                                            if (error != null) {
                                                feedback.setText(message(error));
                                                return;
                                            }
                                            source = value;
                                            restoring = true;
                                            try {
                                                templates.getItems().setAll(source.templates());
                                                templates.setValue(
                                                        source.assignedTemplate() == null
                                                                ? null
                                                                : source.templates().stream()
                                                                        .filter(
                                                                                t ->
                                                                                        t.id()
                                                                                                == source
                                                                                                        .assignedTemplate())
                                                                        .findFirst()
                                                                        .orElse(null));
                                                templates.setDisable(true);
                                            } finally {
                                                restoring = false;
                                            }
                                            info.setText(
                                                    source.job().article()
                                                            + " · "
                                                            + source.name()
                                                            + " · Lotto "
                                                            + source.job().lot()
                                                            + "\nGTIN: "
                                                            + (source.gtin() == null
                                                                    ? "non impostato nell’articolo"
                                                                    : source.gtin())
                                                            + " · "
                                                            + source.job().readings().size()
                                                            + " dischi");
                                            currentInfo = info.getText();
                                            previous.setDisable(source.latest() == null);
                                            regenerate.setDisable(false);
                                            if (source.assignedTemplate() == null) {
                                                templates.setDisable(true);
                                                regenerate.setDisable(true);
                                                feedback.setText(
                                                        "Nessun modello etichetta assegnato nella"
                                                            + " specifica di produzione di questo"
                                                            + " lavoro. Collegalo prima di"
                                                            + " stampare.");
                                            } else render();
                                        }));
    }

    private void render() {
        if (closed || saving || source == null || source.assignedTemplate() == null) return;
        options.setVisible(true);
        options.setManaged(true);
        info.setText(currentInfo);
        long token = ++generation;
        prepared = null;
        issued = null;
        print.setDisable(true);
        templates.setDisable(true);
        expiry.setDisable(false);
        Template template = templates.getValue();
        if (template == null) {
            feedback.setText(
                    source.templates().isEmpty()
                            ? "Crea e approva un modello di tipo Etichetta dischi in TON ›"
                                    + " Documenti › Modelli documenti."
                            : "La specifica storica non ha un modello etichetta: seleziona"
                                    + " esplicitamente una versione per questo lavoro.");
            return;
        }
        feedback.setText("Preparazione anteprima…");
        try {
            var selectedExpiry = expiry.getValue();
            var data = LabelService.payload(source, selectedExpiry);
            preview.render(
                    template.content(),
                    data,
                    html -> {
                        if (closed || token != generation) return;
                        prepared = LabelService.prepared(source, template, selectedExpiry, html);
                        print.setDisable(false);
                        if (!autoRequested) {
                            autoRequested = true;
                            Platform.runLater(
                                    () -> {
                                        if (!closed && token == generation) print();
                                    });
                        }
                        feedback.setText(
                                "Formato "
                                        + prepared.paper().width()
                                        + " × "
                                        + prepared.paper().height()
                                        + " mm · Il modello e il contenuto generato resteranno"
                                        + " nello storico dei dischi.");
                    },
                    error -> {
                        if (!closed && token == generation) feedback.setText(error);
                    });
        } catch (RuntimeException failure) {
            feedback.setText(message(failure));
        }
    }

    private void stored() {
        if (source == null || source.latest() == null || saving) return;
        long token = ++generation;
        issued = source.latest();
        prepared = null;
        print.setDisable(true);
        templates.setDisable(true);
        expiry.setDisable(true);
        var selected = issued;
        options.setVisible(false);
        options.setManaged(false);
        var data = com.google.gson.JsonParser.parseString(selected.data()).getAsJsonObject();
        info.setText(
                data.get("article").getAsString()
                        + " · Lotto "
                        + data.get("lot").getAsString()
                        + "\nEtichetta storica #"
                        + selected.id()
                        + " · GTIN: "
                        + data.get("gtin").getAsString());
        preview.stored(
                selected.html(),
                selected.paper(),
                () -> {
                    if (!closed && token == generation) {
                        print.setDisable(false);
                        feedback.setText(
                                "Etichetta storica #"
                                        + selected.id()
                                        + " · Ristampa del contenuto originale, indipendente dalle"
                                        + " modifiche al catalogo e ai modelli.");
                    }
                },
                feedback::setText);
    }

    private void print() {
        if (saving || closed || !preview.printable()) return;
        if (issued != null) {
            send();
            return;
        }
        if (prepared == null) return;
        saving = true;
        print.setDisable(true);
        templates.setDisable(true);
        expiry.setDisable(true);
        previous.setDisable(true);
        regenerate.setDisable(true);
        feedback.setText("Salvataggio etichetta e collegamento ai dischi…");
        var operator =
                context.operators()
                        .current()
                        .map(o -> new Operator(o.id().toString(), o.username()))
                        .orElse(null);
        service.issue(prepared, operator)
                .whenComplete(
                        (saved, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (closed) return;
                                            saving = false;
                                            print.setDisable(false);
                                            previous.setDisable(source.latest() == null);
                                            regenerate.setDisable(false);
                                            if (error != null) {
                                                feedback.setText(message(error));
                                                return;
                                            }
                                            issued = saved;
                                            source = withLatest(source, saved);
                                            previous.setDisable(false);
                                            send();
                                        }));
    }

    private void send() {
        try {
            boolean accepted = printJob.apply(preview);
            feedback.setText(
                    accepted
                            ? "Etichetta #" + issued.id() + " inviata alla stampante."
                            : "Stampa annullata. L’etichetta #"
                                    + issued.id()
                                    + " resta conservata e può essere ristampata.");
            dialog.close();
        } catch (RuntimeException failure) {
            feedback.setText(message(failure));
        }
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        return error.getMessage() == null
                ? "Operazione etichetta non riuscita."
                : error.getMessage();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        generation++;
        preview.close();
        dialog.hide();
    }
}

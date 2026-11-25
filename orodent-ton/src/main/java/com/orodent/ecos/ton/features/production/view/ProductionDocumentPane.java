package com.orodent.ecos.ton.features.production.view;

import com.orodent.ecos.documents.DocumentEditorPane;
import com.orodent.ecos.documents.DocumentPaper;
import com.orodent.ecos.documents.PrinterPaper;
import com.orodent.ecos.ton.features.production.model.Production.Plan;
import com.orodent.ecos.ton.features.production.service.ProductionDocumentData;

import javafx.print.PrinterJob;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;

import java.util.function.Consumer;

/**
 * A passive preview of the user's template. No fabricated document layout or external navigation.
 */
public final class ProductionDocumentPane extends BorderPane {
    private DocumentEditorPane renderer;
    private WebView preview;
    private long generation;
    private boolean disposed;
    private String html;
    private DocumentPaper paper;
    private boolean ready, printPending;
    private Consumer<String> printFailure = ignored -> {};

    public ProductionDocumentPane() {
        setMinSize(0, 0);
        setId("ton-production-document-preview");
        setCenter(new Label("Scegli un modello documento approvato nel pannello a sinistra."));
    }

    public void show(
            Plan plan,
            String number,
            String stored,
            Consumer<String> generated,
            Consumer<String> error) {
        long request = ++generation;
        ready = false;
        if (plan == null || plan.count() == 0 || plan.template() == null) {
            html = null;
            printPending = false;
            setCenter(
                    new Label(
                            plan != null && plan.template() == null
                                    ? "Nessun modello documento selezionato."
                                    : "Inserisci le quantità per comporre il documento."));
            return;
        }
        paper = DocumentPaper.from(plan.template().content());
        if (stored != null) {
            display(stored);
            generated.accept(stored);
            return;
        }
        Runnable render =
                () -> {
                    if (disposed || request != generation) return;
                    try {
                        String value =
                                renderer.renderProduction(
                                        plan.template().content(),
                                        ProductionDocumentData.payload(plan, number));
                        display(value);
                        generated.accept(value);
                    } catch (RuntimeException e) {
                        error.accept(e.getMessage());
                    }
                };
        if (renderer == null) {
            renderer = new DocumentEditorPane();
            setCenter(new Label("Composizione del foglio operativo dal modello selezionato…"));
            renderer.start(render, error);
        } else if (renderer.ready()) render.run();
        else {
            renderer.dispose();
            renderer = new DocumentEditorPane();
            renderer.start(render, error);
        }
    }

    private void display(String value) {
        html = value;
        if (preview == null) {
            preview = new WebView();
            preview.setId("ton-production-document-webview");
            preview.setMinSize(0, 0);
            preview.setContextMenuEnabled(false);
            preview.getEngine().setJavaScriptEnabled(false);
            preview.getEngine()
                    .getLoadWorker()
                    .stateProperty()
                    .addListener(
                            (o, a, b) -> {
                                if (b == javafx.concurrent.Worker.State.SUCCEEDED) {
                                    ready = true;
                                    if (printPending) {
                                        printPending = false;
                                        try {
                                            print();
                                        } catch (RuntimeException failure) {
                                            printFailure.accept(failure.getMessage());
                                        }
                                    }
                                }
                            });
            preview.getEngine().setCreatePopupHandler(p -> null);
            preview.getEngine()
                    .locationProperty()
                    .addListener(
                            (o, a, b) -> {
                                if (b != null && !b.isEmpty() && !"about:blank".equals(b))
                                    preview.getEngine().getLoadWorker().cancel();
                            });
        }
        setCenter(preview);
        ready = false;
        preview.getEngine().loadContent(value);
    }

    public boolean printable() {
        return ready && html != null;
    }

    public void onPrintFailure(Consumer<String> handler) {
        printFailure = handler;
    }

    public void print() {
        if (!printable()) {
            printPending = true;
            return;
        }
        var job = PrinterJob.createPrinterJob();
        if (job == null) throw new IllegalStateException("Nessuna stampante disponibile.");
        if (job.showPrintDialog(getScene() == null ? null : getScene().getWindow())) {
            try {
                PrinterPaper.configure(job, paper);
            } catch (RuntimeException error) {
                job.cancelJob();
                throw error;
            }
            preview.getEngine().print(job);
            job.endJob();
        }
    }

    public void dispose() {
        disposed = true;
        printPending = false;
        generation++;
        if (renderer != null) renderer.dispose();
        if (preview != null) {
            preview.getEngine().getLoadWorker().cancel();
            preview.getEngine().load(null);
        }
        setCenter(null);
    }
}

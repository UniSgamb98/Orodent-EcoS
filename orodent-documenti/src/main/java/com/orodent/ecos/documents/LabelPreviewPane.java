package com.orodent.ecos.documents;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.print.PrinterJob;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;

import java.util.function.Consumer;

/** Shared passive label preview; the exact returned HTML is persisted before printing. */
public final class LabelPreviewPane extends BorderPane implements AutoCloseable {
    private final DocumentEditorPane renderer = new DocumentEditorPane();
    private final WebView preview = new WebView();
    private boolean closed, ready;
    private long generation;
    private DocumentPaper paper;
    private String html;

    public LabelPreviewPane() {
        setMinSize(0, 0);
        setPrefSize(600, 380);
        preview.setContextMenuEnabled(false);
        preview.getEngine().setCreatePopupHandler(p -> null);
        preview.getEngine()
                .locationProperty()
                .addListener(
                        (o, b, a) -> {
                            if (a != null && !a.isBlank() && !a.equals("about:blank"))
                                preview.getEngine().getLoadWorker().cancel();
                        });
        setCenter(new Label("Preparazione anteprima…"));
    }

    public void render(
            String template, String data, Consumer<String> generated, Consumer<String> error) {
        long token = ++generation;
        ready = false;
        html = null;
        Runnable action =
                () -> {
                    if (closed || token != generation) return;
                    try {
                        paper = DocumentPaper.from(template);
                        String value = renderer.renderLabel(template, data);
                        preview.getEngine()
                                .getLoadWorker()
                                .stateProperty()
                                .addListener(
                                        new javafx.beans.value.ChangeListener<Worker.State>() {
                                            @Override
                                            public void changed(
                                                    javafx.beans.value.ObservableValue<
                                                                    ? extends Worker.State>
                                                            o,
                                                    Worker.State b,
                                                    Worker.State a) {
                                                if (a != Worker.State.SUCCEEDED
                                                        && a != Worker.State.FAILED) return;
                                                preview.getEngine()
                                                        .getLoadWorker()
                                                        .stateProperty()
                                                        .removeListener(this);
                                                Platform.runLater(
                                                        () -> {
                                                            if (closed || token != generation)
                                                                return;
                                                            if (a == Worker.State.FAILED) {
                                                                error.accept(
                                                                        "Impossibile caricare"
                                                                                + " l’anteprima.");
                                                                return;
                                                            }
                                                            boolean fits =
                                                                    CanvasPrintLayout.canvas(
                                                                                    preview)
                                                                            ? CanvasPrintLayout
                                                                                    .fits(preview)
                                                                            : Boolean.TRUE.equals(
                                                                                    preview.getEngine()
                                                                                            .executeScript(
                                                                                                    "(function(){var"
                                                                                                        + " box=document.getElementById('label-output'),body=box.firstElementChild;return"
                                                                                                        + " body.getBoundingClientRect().bottom<=box.getBoundingClientRect().bottom-parseFloat(getComputedStyle(box).paddingBottom)+1"
                                                                                                        + " && body.scrollWidth<=body.clientWidth+1;})()"));
                                                            if (!fits) {
                                                                error.accept(
                                                                        "Il contenuto supera il"
                                                                            + " formato etichetta."
                                                                            + " Riduci testo,"
                                                                            + " immagini o margini"
                                                                            + " nel modello.");
                                                                return;
                                                            }
                                                            html = value;
                                                            ready = true;
                                                            generated.accept(value);
                                                        });
                                            }
                                        });
                        setCenter(preview);
                        preview.getEngine().loadContent(value);
                    } catch (RuntimeException failure) {
                        error.accept(failure.getMessage());
                    }
                };
        if (renderer.ready()) action.run();
        else renderer.start(action, error);
    }

    public void stored(String value, DocumentPaper size, Runnable loaded, Consumer<String> error) {
        long token = ++generation;
        ready = false;
        html = null;
        paper = size;
        var engine = preview.getEngine();
        engine.getLoadWorker()
                .stateProperty()
                .addListener(
                        new javafx.beans.value.ChangeListener<Worker.State>() {
                            @Override
                            public void changed(
                                    javafx.beans.value.ObservableValue<? extends Worker.State> o,
                                    Worker.State b,
                                    Worker.State a) {
                                if (a != Worker.State.SUCCEEDED && a != Worker.State.FAILED) return;
                                engine.getLoadWorker().stateProperty().removeListener(this);
                                if (closed || token != generation) return;
                                if (a == Worker.State.FAILED) {
                                    error.accept("Impossibile aprire l’etichetta storica.");
                                    return;
                                }
                                html = value;
                                ready = true;
                                loaded.run();
                            }
                        });
        setCenter(preview);
        engine.loadContent(value);
    }

    public boolean printable() {
        return ready && html != null && !closed;
    }

    /** true means accepted by the print spooler; it does not certify physical output. */
    public boolean print() {
        if (!printable()) throw new IllegalStateException("Attendi l’anteprima dell’etichetta.");
        var job = PrinterJob.createPrinterJob();
        if (job == null) throw new IllegalStateException("Nessuna stampante disponibile.");
        try {
            if (!job.showPrintDialog(getScene() == null ? null : getScene().getWindow())) {
                job.cancelJob();
                return false;
            }
            if (CanvasPrintLayout.canvas(preview)) CanvasPrintLayout.prepare(preview, job, paper);
            else {
                var layout =
                        PrinterPaper.configure(
                                job, new DocumentPaper(paper.width(), paper.height(), 0), true);
                double left = layout.getLeftMargin() * 25.4 / 72,
                        right = layout.getRightMargin() * 25.4 / 72,
                        top = layout.getTopMargin() * 25.4 / 72,
                        bottom = layout.getBottomMargin() * 25.4 / 72;
                if (Math.max(Math.max(left, right), Math.max(top, bottom)) > paper.margin() + 0.01)
                    throw new IllegalStateException(
                            "I margini della stampante superano quelli dell’etichetta. Configura"
                                + " l’area stampabile nel driver oppure crea una versione del"
                                + " modello con margini compatibili.");
                double width = paper.width() - left - right, height = paper.height() - top - bottom;
                String css =
                        "@media print {html,body {width:"
                                + width
                                + "mm!important;height:"
                                + height
                                + "mm!important;} #label-output {width:"
                                + width
                                + "mm!important;height:"
                                + height
                                + "mm!important;padding:"
                                + Math.max(0, paper.margin() - top)
                                + "mm "
                                + Math.max(0, paper.margin() - right)
                                + "mm "
                                + Math.max(0, paper.margin() - bottom)
                                + "mm "
                                + Math.max(0, paper.margin() - left)
                                + "mm!important;}}";
                preview.getEngine()
                        .executeScript(
                                "(function(){var"
                                    + " s=document.createElement('style');s.id='ecos-print-form';s.textContent="
                                        + new com.google.gson.JsonPrimitive(css)
                                        + ";document.head.appendChild(s);})()");
            }
            preview.getEngine().print(job);
            if (!job.endJob())
                throw new IllegalStateException("La stampante non ha accettato il lavoro.");
            return true;
        } catch (RuntimeException failure) {
            job.cancelJob();
            throw failure;
        } finally {
            if (!closed) CanvasPrintLayout.clear(preview);
            if (!closed)
                preview.getEngine()
                        .executeScript("document.getElementById('ecos-print-form')?.remove()");
        }
    }

    @Override
    public void close() {
        closed = true;
        generation++;
        renderer.dispose();
        preview.getEngine().getLoadWorker().cancel();
        preview.getEngine().load(null);
        setCenter(null);
    }
}

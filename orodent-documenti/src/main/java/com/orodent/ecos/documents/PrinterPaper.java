package com.orodent.ecos.documents;

import javafx.print.*;

/** Select an exact printer form, never silently shrink a label onto A4. */
public final class PrinterPaper {
    private PrinterPaper() {}

    public static PageLayout configure(PrinterJob job, DocumentPaper size) {
        return configure(job, size, false);
    }

    public static PageLayout configure(
            PrinterJob job, DocumentPaper size, boolean compensateHardwareMargins) {
        var printer = job.getPrinter();
        for (var paper : printer.getPrinterAttributes().getSupportedPapers()) {
            double w = paper.getWidth() * 25.4 / 72, h = paper.getHeight() * 25.4 / 72;
            PageOrientation orientation = null;
            if (close(w, size.width()) && close(h, size.height()))
                orientation = PageOrientation.PORTRAIT;
            else if (close(h, size.width()) && close(w, size.height()))
                orientation = PageOrientation.LANDSCAPE;
            if (orientation != null) {
                double margin = size.margin() * 72 / 25.4;
                var layout =
                        printer.createPageLayout(
                                paper, orientation, margin, margin, margin, margin);
                if (!compensateHardwareMargins
                        && (layout.getLeftMargin() > margin + 0.6
                                || layout.getRightMargin() > margin + 0.6
                                || layout.getTopMargin() > margin + 0.6
                                || layout.getBottomMargin() > margin + 0.6))
                    throw new IllegalStateException(
                            "Il formato della stampante impone margini superiori a quelli del"
                                + " modello. Configura l’area stampabile nel driver oppure crea una"
                                + " versione con margini compatibili.");
                job.getJobSettings().setPageLayout(layout);
                return layout;
            }
        }
        throw new IllegalStateException(
                "La stampante selezionata non espone il formato "
                        + size.width()
                        + " × "
                        + size.height()
                        + " mm. Configura questo formato nel driver della stampante e riprova.");
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) < 0.6;
    }
}

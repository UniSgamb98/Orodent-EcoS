package com.orodent.ecos.documents;

import com.google.gson.*;

import javafx.print.PrinterJob;
import javafx.scene.web.WebView;

/** Maintains page coordinates when a physical printer has unprintable edge margins. */
public final class CanvasPrintLayout {
    private CanvasPrintLayout() {}

    public static boolean canvas(WebView view) {
        return Boolean.TRUE.equals(
                view.getEngine().executeScript("Boolean(document.querySelector('.canvas-sheet'))"));
    }

    public static boolean fits(WebView view) {
        return Boolean.TRUE.equals(
                view.getEngine()
                        .executeScript(
                                """
(function(){
 const sheets=[...document.querySelectorAll('.canvas-sheet')];
 return sheets.every(sheet=>[...sheet.querySelectorAll('.canvas-object')].every(object=>{
   const box=object.getBoundingClientRect(),page=sheet.getBoundingClientRect(),content=object.querySelector('.canvas-object-content');
   const code=object.querySelector('.gs1-tight img'),shadow=code && getComputedStyle(code).boxShadow.match(/[-\\d.]+px/g);
   const quiet=shadow ? Math.max(0,parseFloat(shadow[shadow.length-1])) : 0;
   return box.left-quiet>=page.left-.5 && box.top-quiet>=page.top-.5 && box.right+quiet<=page.right+.5 && box.bottom+quiet<=page.bottom+.5 && content && content.scrollWidth<=content.clientWidth+1 && content.scrollHeight<=content.clientHeight+1;
 }));
})()
"""));
    }

    public static void prepare(WebView view, PrinterJob job, DocumentPaper paper) {
        if (!fits(view))
            throw new IllegalStateException(
                    "Un oggetto o il suo contenuto supera il riquadro o la pagina. Correggi le"
                            + " dimensioni nel modello prima di stampare.");
        var layout =
                PrinterPaper.configure(
                        job, new DocumentPaper(paper.width(), paper.height(), 0), true);
        double left = layout.getLeftMargin() * 25.4 / 72,
                right = layout.getRightMargin() * 25.4 / 72,
                top = layout.getTopMargin() * 25.4 / 72,
                bottom = layout.getBottomMargin() * 25.4 / 72;
        String margins = new Gson().toJson(new double[] {left, top, right, bottom});
        boolean printable =
                Boolean.TRUE.equals(
                        view.getEngine()
                                .executeScript(
                                        """
(function(m){return [...document.querySelectorAll('.canvas-sheet')].every(sheet=>{
 const page=sheet.getBoundingClientRect(),scale=25.4/96;
 return [...sheet.querySelectorAll('.canvas-object')].every(object=>{const r=object.getBoundingClientRect();
 const code=object.querySelector('.gs1-tight img'),shadow=code && getComputedStyle(code).boxShadow.match(/[-\\d.]+px/g);
 const quiet=shadow ? Math.max(0,parseFloat(shadow[shadow.length-1])) : 0;
 return (r.left-quiet-page.left)*scale>=m[0]-.1 && (r.top-quiet-page.top)*scale>=m[1]-.1 && (page.right-r.right-quiet)*scale>=m[2]-.1 && (page.bottom-r.bottom-quiet)*scale>=m[3]-.1;});
});})(%s)
"""
                                                .formatted(margins)));
        if (!printable)
            throw new IllegalStateException(
                    "Un oggetto ricade nei margini non stampabili della stampante. Spostalo verso"
                            + " l’interno o scegli una stampante compatibile.");
        double width = paper.width() - left - right, height = paper.height() - top - bottom;
        String css =
                "@media print {html,body {width:"
                        + width
                        + "mm!important;height:auto!important;} .canvas-sheet {width:"
                        + width
                        + "mm!important;height:"
                        + height
                        + "mm!important;padding:0!important;} .canvas-sheet > .canvas-document"
                        + " {width:"
                        + paper.width()
                        + "mm!important;height:"
                        + paper.height()
                        + "mm!important;transform:translate(-"
                        + left
                        + "mm,-"
                        + top
                        + "mm);transform-origin:top left;}}";
        view.getEngine()
                .executeScript(
                        "(function(){const"
                            + " s=document.createElement('style');s.id='ecos-canvas-print';s.textContent="
                                + new JsonPrimitive(css)
                                + ";document.head.appendChild(s);})()");
    }

    public static void clear(WebView view) {
        view.getEngine().executeScript("document.getElementById('ecos-canvas-print')?.remove()");
    }
}

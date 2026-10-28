package com.orodent.ecos.statistiche.features.sales.products.view;

import com.orodent.ecos.statistiche.features.sales.products.model.ProductSalesPace;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;

/** Explains historical pace separately from net accounting totals. */
final class ProductSalesPacePane extends VBox {
    private final ProductFormats formats = new ProductFormats();
    private final FlowPane cards = new FlowPane(10, 10);
    private final Label scope = new Label();

    ProductSalesPacePane() {
        super(8);
        setId("stats-product-sales-pace");
        scope.setId("stats-product-sales-pace-scope");
        scope.setWrapText(true);
        scope.setMinHeight(Region.USE_PREF_SIZE);
        scope.getStyleClass().add("product-scope-note");
        getChildren().setAll(cards, scope);
    }

    void show(ProductSalesPace pace) {
        cards.getChildren().setAll(
                card("Dischi / settimana", formats.pace(pace.weeklyQuantity()), "stats-product-weekly-sales",
                        "Quantità positiva venduta / giorni di calendario × 7. Include i giorni senza vendite."),
                card("Dischi / sett. · ultimi " + pace.recentDays() + " giorni", formats.pace(pace.recentWeeklyQuantity()),
                        "stats-product-recent-weekly-sales", "Ritmo degli ultimi 91 giorni fino alla data dei dati, limitato allo storico disponibile nell'anno selezionato."),
                card("Tipico / documento", formats.pace(pace.medianDocumentQuantity()), "stats-product-median-document",
                        "Mediana delle quantità per documento: metà dei documenti contiene una quantità non superiore a questa. Risente meno di pochi acquisti molto grandi."),
                card("Media / documento", formats.pace(pace.meanDocumentQuantity()), "stats-product-mean-document",
                        "Quantità positiva venduta divisa per i documenti di vendita che contengono il gruppo. Le righe dello stesso documento vengono sommate."));
        if (pace.days() == 0) {
            scope.setText("Periodo osservato non disponibile: ritmo e quantità per documento non calcolabili.");
            return;
        }
        var date = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        scope.setText("Vendite positive dal " + pace.from().format(date) + " al " + pace.through().format(date)
                + ": " + formats.number(pace.soldQuantity()) + " dischi · " + pace.documents() + " documenti · " + pace.days()
                + " giorni di calendario. Resi e note di credito esclusi. Ritmo storico, non previsione di esaurimento."
                + (pace.documents() < 5 ? " Campione ridotto: meno di 5 documenti di vendita." : ""));
    }

    private VBox card(String title, String text, String id, String explanation) {
        Label heading = new Label(title);
        heading.getStyleClass().add("muted-label");
        heading.setWrapText(true);
        Label value = new Label(text);
        value.setId(id);
        value.getStyleClass().add("product-metric-value");
        VBox card = new VBox(6, heading, value);
        card.setMinWidth(0);
        card.setPrefWidth(180);
        card.getStyleClass().add("product-metric");
        Tooltip.install(card, new Tooltip(explanation));
        return card;
    }
}

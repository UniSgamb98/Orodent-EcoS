package com.orodent.ecos.statistiche.features.sales.archive.view;

import com.orodent.ecos.statistiche.features.sales.analysis.model.*;
import com.orodent.ecos.statistiche.features.sales.analysis.view.AnalysisView;
import static com.orodent.ecos.statistiche.core.components.TableColumns.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class SalesArchiveView extends AnalysisView<SalesArchiveItem> {
    private final TextField search = new TextField();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final NumberFormat number = NumberFormat.getNumberInstance(Locale.ITALY);

    public SalesArchiveView() {
        super("Archivio vendite", "Consulta le righe importate e risali ai dati di origine");
        search.setPromptText("Documento, cliente o prodotto");
        search.setPrefWidth(300);
        Label label = new Label("Ricerca"); label.getStyleClass().add("filter-label");
        filterBar.getChildren().add(1, new VBox(5, label, search));
        search.setOnAction(event -> refreshButton().fire());
    }

    public String searchText() { return search.getText(); }

    @Override protected void configureTable(TableView<SalesArchiveItem> table) {
        table.getColumns().addAll(value("Data", SalesArchiveItem::date, date -> date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))),
                text("Documento", SalesArchiveItem::document), text("Cliente", SalesArchiveItem::customerName),
                text("Codice cliente", SalesArchiveItem::customerCode), text("Prodotto", SalesArchiveItem::productCode),
                text("Descrizione", SalesArchiveItem::productDescription),
                value("Quantità", item -> item.quantity(), amount -> number.format(amount)),
                value("Sconto", SalesArchiveItem::discountPercentage, amount -> number.format(amount) + "%"),
                value("Netto", SalesArchiveItem::netAmount, amount -> currency.format(amount)),
                text("Operazione", item -> item.operation().name()));
    }

    @Override protected void updateSummary(AnalysisPageData<SalesArchiveItem> data) {
        metrics.getChildren().setAll(metric("Righe visualizzate", number.format(data.summary().count())),
                metric("Limite risultati", "500"));
    }

}

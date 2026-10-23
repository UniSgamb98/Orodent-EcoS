package com.orodent.ecos.statistiche.features.sales.returns.view;

import com.orodent.ecos.statistiche.features.sales.analysis.model.*;
import com.orodent.ecos.statistiche.features.sales.analysis.view.AnalysisView;
import static com.orodent.ecos.statistiche.core.components.TableColumns.*;
import javafx.scene.control.TableView;

import java.text.NumberFormat;
import java.util.Locale;

public final class ReturnsView extends AnalysisView<ReturnAnalysisItem> {
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final NumberFormat number = NumberFormat.getNumberInstance(Locale.ITALY);

    public ReturnsView() { super("Resi e note di credito", "Valore e quantità delle rettifiche per cliente"); }

    @Override protected void configureTable(TableView<ReturnAnalysisItem> table) {
        table.getColumns().addAll(text("Codice", ReturnAnalysisItem::code),
                text("Cliente", ReturnAnalysisItem::name),
                value("Importo", ReturnAnalysisItem::amount, amount -> currency.format(amount)),
                value("Quantità", item -> item.quantity(), amount -> number.format(amount)),
                value("Documenti", item -> item.documents(), amount -> number.format(amount)));
    }

    @Override protected void updateSummary(AnalysisPageData<ReturnAnalysisItem> data) {
        metrics.getChildren().setAll(metric("Valore rettifiche", currency.format(data.summary().primaryValue())),
                metric("Quantità resa", number.format(data.summary().secondaryValue())),
                metric("Documenti", number.format(data.summary().count())));
    }

}

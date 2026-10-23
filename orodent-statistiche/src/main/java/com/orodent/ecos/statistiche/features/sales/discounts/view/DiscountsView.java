package com.orodent.ecos.statistiche.features.sales.discounts.view;

import com.orodent.ecos.statistiche.features.sales.analysis.model.*;
import com.orodent.ecos.statistiche.features.sales.analysis.view.AnalysisView;
import static com.orodent.ecos.statistiche.core.components.TableColumns.*;
import javafx.scene.control.TableView;

import java.text.NumberFormat;
import java.util.Locale;

public final class DiscountsView extends AnalysisView<DiscountAnalysisItem> {
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final NumberFormat number = NumberFormat.getNumberInstance(Locale.ITALY);

    public DiscountsView() { super("Analisi sconti", "Sconti concessi e incidenza per cliente"); }

    @Override protected void configureTable(TableView<DiscountAnalysisItem> table) {
        table.getColumns().addAll(text("Codice", DiscountAnalysisItem::code),
                text("Cliente", DiscountAnalysisItem::name),
                value("Fatturato", item -> item.revenue(), amount -> currency.format(amount)),
                value("Sconto concesso", DiscountAnalysisItem::discount, amount -> currency.format(amount)),
                value("Sconto medio", DiscountAnalysisItem::averagePercentage, amount -> number.format(amount) + "%"));
    }

    @Override protected void updateSummary(AnalysisPageData<DiscountAnalysisItem> data) {
        metrics.getChildren().setAll(metric("Sconti concessi", currency.format(data.summary().primaryValue())),
                metric("Sconto medio", number.format(data.summary().secondaryValue()) + "%"),
                metric("Fatturato netto", currency.format(data.summary().tertiaryValue())),
                metric("Documenti", number.format(data.summary().count())));
    }

}

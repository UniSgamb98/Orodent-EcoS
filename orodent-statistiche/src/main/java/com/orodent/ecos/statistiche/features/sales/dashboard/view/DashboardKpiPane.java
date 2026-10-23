package com.orodent.ecos.statistiche.features.sales.dashboard.view;

import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesDashboardData;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.List;

/** Owns the KPI presentation; the dashboard view only forwards loaded data. */
final class DashboardKpiPane extends GridPane {
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final NumberFormat number = NumberFormat.getNumberInstance(Locale.ITALY);
    private final DashboardMetricCard revenue = new DashboardMetricCard("Fatturato netto");
    private final DashboardMetricCard quantity = new DashboardMetricCard("Quantità");
    private final DashboardMetricCard customers = new DashboardMetricCard("Clienti");
    private final DashboardMetricCard documents = new DashboardMetricCard("Documenti");

    DashboardKpiPane() {
        setHgap(14);
        var cards = List.of(revenue, quantity, customers, documents);
        for (int index = 0; index < cards.size(); index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25); column.setMinWidth(0); column.setHgrow(Priority.ALWAYS);
            getColumnConstraints().add(column); add(cards.get(index), index, 0);
        }
    }

    void show(SalesDashboardData data) {
        var current = data.summary(); var previous = data.previousSummary(); int year = data.selectedYear();
        revenue.show(current.netRevenue(), previous.netRevenue(), year, data.projections().revenue(), currency::format);
        quantity.show(current.quantity(), previous.quantity(), year, data.projections().quantity(), number::format);
        documents.show(BigDecimal.valueOf(current.documents()), BigDecimal.valueOf(previous.documents()), year,
                data.projections().documents(), number::format);
        customers.show(BigDecimal.valueOf(current.customers()), BigDecimal.valueOf(previous.customers()), year, null, number::format);
        revenue.showSecondary("Valore medio documento", current.averageDocumentValue(), previous.averageDocumentValue(), year, currency::format);
    }
}

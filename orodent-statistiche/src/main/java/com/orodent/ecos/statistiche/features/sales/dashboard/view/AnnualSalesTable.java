package com.orodent.ecos.statistiche.features.sales.dashboard.view;

import com.orodent.ecos.statistiche.features.sales.dashboard.model.AnnualSalesComparison;
import com.orodent.ecos.statistiche.features.sales.projection.model.ProjectionMethod;
import javafx.beans.property.SimpleIntegerProperty;
import static com.orodent.ecos.statistiche.core.components.TableColumns.value;
import javafx.scene.control.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Displays actual annual data and estimates in explicitly separate columns. */
final class AnnualSalesTable extends TableView<AnnualSalesComparison> {
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final NumberFormat number = NumberFormat.getNumberInstance(Locale.ITALY);
    private Map<Integer, AnnualSalesComparison> comparisons = Map.of();

    AnnualSalesTable() {
        TableColumn<AnnualSalesComparison, Integer> year = new TableColumn<>("Anno");
        year.setCellValueFactory(item -> new SimpleIntegerProperty(item.getValue().year()).asObject());
        year.setSortType(TableColumn.SortType.DESCENDING);
        getColumns().addAll(year,
                value("Fatturato", item -> item.summary().netRevenue(), currency::format),
                value("Δ registrato", AnnualSalesComparison::revenueChangePercentage, this::percentage),
                value("Previsto 31/12", item -> forecast(item) ? item.projection().projectedAnnualRevenue() : null, currency::format),
                value("Δ previsto", this::projectedChange, this::percentage),
                value("Quantità", item -> item.summary().quantity(), number::format),
                value("Clienti", item -> item.summary().customers(), number::format),
                value("Media doc.", item -> item.summary().averageDocumentValue(), currency::format));
        getSortOrder().add(year);
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        setPrefHeight(310);
        setPlaceholder(new Label("Nessuno storico disponibile"));
    }
    void show(List<AnnualSalesComparison> values) {
        comparisons = values.stream().collect(Collectors.toUnmodifiableMap(AnnualSalesComparison::year, Function.identity()));
        getItems().setAll(values); sort();
    }
    private boolean forecast(AnnualSalesComparison item) {
        return item.projection() != null && item.projection().method() != ProjectionMethod.ACTUAL;
    }
    private BigDecimal projectedChange(AnnualSalesComparison item) {
        var previous = comparisons.get(item.year() - 1);
        if (!forecast(item) || previous == null || previous.summary().netRevenue().signum() == 0) return null;
        BigDecimal baseline = previous.summary().netRevenue();
        return item.projection().projectedAnnualRevenue().subtract(baseline)
                .multiply(BigDecimal.valueOf(100)).divide(baseline.abs(), 1, RoundingMode.HALF_UP);
    }
    private String percentage(BigDecimal value) {
        return value == null ? "—" : (value.signum() > 0 ? "+" : "") + number.format(value) + "%";
    }
}

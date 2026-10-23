package com.orodent.ecos.statistiche.features.sales.dashboard.view;

import com.orodent.ecos.statistiche.features.sales.projection.model.MetricProjection;
import com.orodent.ecos.statistiche.features.sales.projection.model.ProjectionMethod;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;

/** Renders a recorded or projected comparison with the same colors and tooltip conventions. */
final class DashboardComparisonLabel extends Label {
    private final NumberFormat number = NumberFormat.getNumberInstance(Locale.ITALY);

    DashboardComparisonLabel() {
        getStyleClass().add("metric-comparison");
        setWrapText(true); setMinWidth(0); setMinHeight(Region.USE_PREF_SIZE);
    }

    void show(BigDecimal actual, BigDecimal previous, int year, MetricProjection projection,
              Function<BigDecimal, String> format) {
        boolean projected = projection != null && projection.method() != ProjectionMethod.ACTUAL;
        BigDecimal current = projected ? projection.projectedAnnualValue() : actual;
        String comparison;
        if (previous.signum() == 0) {
            comparison = "— nessun confronto disponibile";
        } else {
            BigDecimal percentage = current.subtract(previous).multiply(BigDecimal.valueOf(100))
                    .divide(previous.abs(), 1, RoundingMode.HALF_UP);
            String arrow = percentage.signum() > 0 ? "▲ +" : percentage.signum() < 0 ? "▼ " : "= ";
            comparison = arrow + number.format(percentage) + "% rispetto al " + (year - 1);
        }
        setText(comparison + (projected ? " · proiezione al 31/12" : ""));
        getStyleClass().removeAll("comparison-positive", "comparison-negative", "comparison-neutral");
        getStyleClass().add(previous.signum() == 0 || current.compareTo(previous) == 0 ? "comparison-neutral"
                : current.compareTo(previous) > 0 ? "comparison-positive" : "comparison-negative");
        String detail = "Registrato: " + format.apply(actual) + "\nValore " + (year - 1) + ": " + format.apply(previous);
        if (projected) {
            detail = "Stima al 31/12: " + format.apply(current) + "\n" + detail
                    + "\nMetodo: " + (projection.method() == ProjectionMethod.SEASONAL ? "stagionalità storica della misura" : "proporzione dei giorni trascorsi")
                    + "\nDati aggiornati al " + projection.dataThrough().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        setTooltip(new Tooltip(detail));
    }
}

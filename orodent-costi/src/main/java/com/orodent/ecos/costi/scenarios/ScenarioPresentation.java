package com.orodent.ecos.costi.scenarios;

import com.orodent.ecos.costi.view.PriceFormats;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Display rounding only: calculations and exports retain their original precision. */
final class ScenarioPresentation {
    private ScenarioPresentation() {}

    static String money(BigDecimal value) {
        return value == null ? "—" : PriceFormats.number(value, 2, 2) + " €";
    }

    static String delta(BigDecimal value) {
        return value == null ? "Non calcolabile" : sign(value) + money(value);
    }

    static String percent(BigDecimal value) {
        return value == null ? "—" : sign(value) + PriceFormats.number(value, 1, 1) + "%";
    }

    static String sign(BigDecimal value) {
        return value.signum() > 0 ? "+" : "";
    }

    static String tone(BigDecimal value) {
        return value == null || value.signum() == 0
                ? "impact-neutral"
                : value.signum() < 0 ? "impact-decrease" : "impact-increase";
    }

    static String date(LocalDate value) {
        return value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    static String timestamp(Instant value) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(value);
    }

    static void precise(Label label, BigDecimal value, String unit) {
        label.setTooltip(value == null ? null : new Tooltip(ScenarioDetails.n(value) + " " + unit));
    }

    static VBox metric(String title, Label value, String id) {
        value.setId(id);
        value.setWrapText(true);
        value.getStyleClass().add("impact-metric-value");
        var caption = new Label(title);
        caption.getStyleClass().add("impact-caption");
        caption.setWrapText(true);
        var box = new VBox(8, caption, value);
        box.getStyleClass().add("impact-metric");
        return box;
    }
}

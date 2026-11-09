package com.orodent.ecos.costi.consumption;

import com.orodent.ecos.costi.consumption.Consumption.*;
import com.orodent.ecos.costi.view.PriceFormats;

import javafx.scene.canvas.*;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * One curve per powder AND source, never a sum by colour. Mouse tracking uses exact calendar dates.
 */
public final class PowderChart extends Pane {
    private final Canvas canvas = new Canvas(), cursor = new Canvas();
    private final Tooltip tooltip = new Tooltip();
    private List<Series> series = List.of();
    private Analysis analysis;
    private Metric metric = Metric.CONSUMO;
    private LocalDate hovered;
    private double low, high;

    public PowderChart() {
        setId("consumption-chart");
        setMinHeight(250);
        setPrefHeight(310);
        cursor.setMouseTransparent(true);
        getChildren().addAll(canvas, cursor);
        tooltip.setShowDelay(javafx.util.Duration.millis(100));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(450);
        Tooltip.install(canvas, tooltip);
        widthProperty().addListener((o, b, a) -> draw());
        heightProperty().addListener((o, b, a) -> draw());
        canvas.setOnMouseMoved(
                e -> {
                    if (analysis == null) return;
                    double x =
                            Math.max(
                                    0, Math.min(1, (e.getX() - 65) / Math.max(1, getWidth() - 90)));
                    hovered = analysis.request().from().plusDays(Math.round(x * span()));
                    Series nearest =
                            series.stream()
                                    .filter(v -> value(v, hovered) != null)
                                    .min(
                                            Comparator.comparingDouble(
                                                    v ->
                                                            Math.abs(
                                                                    25
                                                                            + (getHeight() - 65)
                                                                                    * (high
                                                                                            - value(
                                                                                                            v,
                                                                                                            hovered)
                                                                                                    .doubleValue())
                                                                                    / Math.max(
                                                                                            .00001,
                                                                                            high
                                                                                                    - low)
                                                                            - e.getY())))
                                    .orElse(null);
                    tooltip.setText(
                            nearest == null
                                    ? hovered + "\nNessun dato disponibile"
                                    : hoverText(
                                            hovered,
                                            series.stream()
                                                    .filter(
                                                            v ->
                                                                    v.powder().id()
                                                                            == nearest.powder()
                                                                                    .id())
                                                    .toList()));
                    drawCursor();
                });
        canvas.setOnMouseExited(
                e -> {
                    hovered = null;
                    drawCursor();
                });
    }

    public void show(Analysis analysis, List<Series> visible, Metric metric) {
        tooltip.setText("");
        hovered = null;
        this.analysis = analysis;
        this.series = List.copyOf(visible);
        this.metric = metric;
        draw();
    }

    public int visibleSeriesCount() {
        return series.size();
    }

    public String hoverText(LocalDate date) {
        return hoverText(date, series);
    }

    private String hoverText(LocalDate date, List<Series> shown) {
        if (analysis == null) return "";
        StringBuilder text = new StringBuilder(date.toString());
        for (Series s : shown) {
            BigDecimal value = value(s, date);
            text.append("\n")
                    .append(s.powder().code())
                    .append(" · ")
                    .append(s.basis())
                    .append("\n")
                    .append(metric == Metric.CONSUMO ? "Consumo: " : "Disponibilità: ")
                    .append(
                            value == null
                                    ? "Non determinabile"
                                    : PriceFormats.number(value, 0, 4) + " kg")
                    .append(
                            metric == Metric.DISPONIBILITA && date.equals(s.powder().stockDate())
                                    ? " · dichiarata"
                                    : date.isAfter(analysis.request().through())
                                                    || metric == Metric.DISPONIBILITA
                                            ? " · stimata"
                                            : " · teorico ricostruito");
            BigDecimal stock = Consumption.stockOn(s, date, analysis.request().calendar());
            if (metric == Metric.CONSUMO)
                text.append("\nDisponibilità stimata: ")
                        .append(
                                stock == null
                                        ? "Non determinabile"
                                        : PriceFormats.number(stock, 0, 3) + " kg");
            BigDecimal coverage =
                    stock == null ? null : Consumption.divide(stock.max(BigDecimal.ZERO), s.rate());
            text.append("\nCopertura: ")
                    .append(
                            coverage == null
                                    ? "Non determinabile"
                                    : PriceFormats.number(coverage, 0, 1) + " giorni");
        }
        return text.toString();
    }

    private BigDecimal value(Series s, LocalDate date) {
        return metric == Metric.CONSUMO
                ? s.daily().get(date)
                : Consumption.stockOn(s, date, analysis.request().calendar());
    }

    private long span() {
        return Math.max(
                1,
                ChronoUnit.DAYS.between(
                        analysis.request().from(),
                        analysis.request().today().plusDays(analysis.request().horizon())));
    }

    private double x(LocalDate date) {
        return 65
                + (getWidth() - 90)
                        * ChronoUnit.DAYS.between(analysis.request().from(), date)
                        / span();
    }

    private void draw() {
        double w = getWidth(), h = getHeight();
        if (w < 100 || h < 100) return;
        cursor.setWidth(w);
        cursor.setHeight(h);
        canvas.setWidth(w);
        canvas.setHeight(h);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, w, h);
        if (analysis == null) {
            g.setFill(Color.GRAY);
            g.fillText("Seleziona un periodo e carica i dati", 70, 50);
            return;
        }
        double min = 0, max = 0;
        boolean any = false;
        for (Series s : series)
            for (LocalDate d = analysis.request().from();
                    !d.isAfter(analysis.request().today().plusDays(analysis.request().horizon()));
                    d = d.plusDays(1)) {
                BigDecimal v = value(s, d);
                if (v != null) {
                    any = true;
                    min = Math.min(min, v.doubleValue());
                    max = Math.max(max, v.doubleValue());
                }
            }
        double extent = Math.max(.001, max - min);
        max += extent * .08;
        min -= extent * .04;
        final double lo = min, hi = max;
        low = lo;
        high = hi;
        java.util.function.Function<BigDecimal, Double> y =
                v -> 25 + (h - 65) * (hi - v.doubleValue()) / (hi - lo);
        g.setFill(Color.web("#edf5fa"));
        double future = x(analysis.request().today());
        g.fillRect(future, 20, Math.max(0, w - 25 - future), h - 60);
        g.setStroke(Color.web("#dce5ed"));
        g.setFill(Color.web("#57768c"));
        for (int n = 0; n <= 4; n++) {
            double yy = 25 + (h - 65) * n / 4;
            g.strokeLine(65, yy, w - 25, yy);
            g.fillText(String.format(Locale.ITALY, "%.2f", hi - (hi - lo) * n / 4), 3, yy + 4);
        }
        for (int n = 0; n <= 4; n++) {
            LocalDate d = analysis.request().from().plusDays(span() * n / 4);
            g.fillText(d.toString(), Math.min(w - 95, x(d)), h - 13);
        }
        g.fillText("kg", 10, 15);
        g.fillText("Previsione", Math.min(w - 95, future + 5), 15);
        for (Series s : series) {
            g.setStroke(color(s.powder()));
            g.setLineWidth(1.9);
            if (s.basis() == Basis.VENDITE) g.setLineDashes(6, 4);
            else g.setLineDashes();
            LocalDate previous = null;
            BigDecimal previousValue = null;
            for (LocalDate d = analysis.request().from();
                    !d.isAfter(analysis.request().today().plusDays(analysis.request().horizon()));
                    d = d.plusDays(1)) {
                BigDecimal v = value(s, d);
                if (v != null && previousValue != null)
                    g.strokeLine(x(previous), y.apply(previousValue), x(d), y.apply(v));
                previous = d;
                previousValue = v;
            }
        }
        g.setLineDashes();
        drawCursor();
        if (!any) {
            g.setFill(Color.web("#8a6500"));
            g.fillText("Nessun dato calcolabile per le polveri e le fonti selezionate", 80, 60);
        }
    }

    private void drawCursor() {
        var g = cursor.getGraphicsContext2D();
        g.clearRect(0, 0, cursor.getWidth(), cursor.getHeight());
        if (hovered != null && analysis != null) {
            g.setStroke(Color.web("#173f5f"));
            g.strokeLine(x(hovered), 20, x(hovered), getHeight() - 40);
        }
    }

    public static Color color(Powder p) {
        double base =
                switch (Objects.toString(p.color(), "")) {
                    case "BIANCA" -> 205;
                    case "GIALLA" -> 40;
                    case "ROSA" -> 330;
                    case "GRIGIA" -> 230;
                    default -> 150;
                };
        return Color.hsb(
                base + (p.id() % 5 - 2) * 7, .45 + (p.id() % 3) * .12, .55 + (p.id() % 4) * .09);
    }
}

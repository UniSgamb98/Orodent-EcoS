package com.orodent.ecos.statistiche.core.components;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.Supplier;

/** Attaches a live tooltip even when JavaFX creates or replaces a point's node later. */
public final class ChartPointTooltip<X, Y> implements AutoCloseable {
    private final XYChart.Data<X, Y> point;
    private final Tooltip tooltip = new Tooltip();
    private final StringBinding text;
    private final ChangeListener<Node> nodeListener = (observable, previous, current) -> {
        if (previous != null) Tooltip.uninstall(previous, tooltip);
        tooltip.hide();
        if (current != null) Tooltip.install(current, tooltip);
    };

    public ChartPointTooltip(XYChart.Data<X, Y> point, Supplier<String> formatter,
                             Observable... formattingDependencies) {
        this.point = Objects.requireNonNull(point);
        Objects.requireNonNull(formatter);
        Observable[] dependencies = new Observable[formattingDependencies.length + 2];
        dependencies[0] = point.XValueProperty();
        dependencies[1] = point.YValueProperty();
        System.arraycopy(formattingDependencies, 0, dependencies, 2, formattingDependencies.length);
        text = Bindings.createStringBinding(formatter::get, dependencies);
        tooltip.textProperty().bind(text);
        tooltip.setShowDelay(Duration.millis(150));
        tooltip.setShowDuration(Duration.INDEFINITE);
        tooltip.setHideDelay(Duration.ZERO);
        tooltip.getStyleClass().add("chart-point-tooltip");
        point.nodeProperty().addListener(nodeListener);
        if (point.getNode() != null) Tooltip.install(point.getNode(), tooltip);
    }

    /** Release listeners and bindings when the owning series is removed. */
    @Override
    public void close() {
        point.nodeProperty().removeListener(nodeListener);
        if (point.getNode() != null) Tooltip.uninstall(point.getNode(), tooltip);
        tooltip.hide();
        tooltip.textProperty().unbind();
        text.dispose();
    }
}

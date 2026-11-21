package com.orodent.ecos.ton.features.catalog.bulk.view;

import com.orodent.ecos.ton.features.catalog.bulk.model.BulkCatalogModels.*;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/** Bounded catalogue pages; products stay selected across subsequent pages. */
public final class BulkParentPicker extends VBox {
    private final CatalogKind kind;
    private final boolean multiple;
    private final ComboBox<Parent> single = new ComboBox<>();
    private final VBox choices = new VBox();
    private final ScrollPane products = new ScrollPane(choices);
    private final Button more = new Button("Altri risultati…"),
            selectAll = new Button("Seleziona tutto");
    private final Label feedback = new Label();
    private final LinkedHashMap<Long, Parent> visible = new LinkedHashMap<>(),
            selected = new LinkedHashMap<>();
    private Runnable changed = () -> {};
    private boolean updating;

    public BulkParentPicker(CatalogKind kind, boolean multiple, String id) {
        super(7);
        this.kind = kind;
        this.multiple = multiple;
        setMinWidth(0);
        single.setId(id);
        single.setMaxWidth(Double.MAX_VALUE);
        single.setVisibleRowCount(10);
        single.setPromptText("Seleziona la linea");
        more.setId(id + "-more");
        more.getStyleClass().add("bulk-link-button");
        more.setVisible(false);
        more.setManaged(false);
        feedback.getStyleClass().add("bulk-small");
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        feedback.setWrapText(true);
        feedback.setMinHeight(Region.USE_PREF_SIZE);
        if (multiple) {
            ScrollPane scroll = products;
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setPrefHeight(360);
            scroll.setMinHeight(260);
            scroll.getStyleClass().add("bulk-product-picker");
            scroll.setId(id);
            getChildren().add(scroll);
            Button clear = new Button("Azzera selezione");
            selectAll.getStyleClass().add("bulk-link-button");
            clear.getStyleClass().add("bulk-link-button");
            selectAll.setId(id + "-all");
            clear.setId(id + "-clear");
            clear.setOnAction(
                    e -> {
                        selected.clear();
                        render();
                        changed.run();
                    });
            FlowPane commands = new FlowPane(5, 5, selectAll, clear);
            getChildren().add(commands);
        } else {
            getChildren().add(single);
            single.valueProperty()
                    .addListener(
                            (o, a, b) -> {
                                if (!updating) {
                                    selected.clear();
                                    if (b != null) selected.put(b.id(), b);
                                    changed.run();
                                }
                            });
        }
        getChildren().addAll(more, feedback);
    }

    public void fitAvailableHeight(boolean fit) {
        if (!multiple) return;
        setMinHeight(fit ? 0 : Region.USE_PREF_SIZE);
        products.setMinHeight(fit ? 100 : 260);
        VBox.setVgrow(products, fit ? Priority.ALWAYS : Priority.NEVER);
    }

    public double minimumFlexibleHeight(double width) {
        double result = getInsets().getTop() + getInsets().getBottom();
        long count = getChildren().stream().filter(javafx.scene.Node::isManaged).count();
        result += Math.max(0, count - 1) * getSpacing();
        for (var child : getChildren())
            if (child.isManaged()) {
                result += child == products ? 100 : child.prefHeight(width);
            }
        return result;
    }

    public void onSelection(Runnable action) {
        changed = action;
    }

    public CatalogKind kind() {
        return kind;
    }

    public Button more() {
        return more;
    }

    public int loaded() {
        return visible.size();
    }

    public List<Parent> selected() {
        return List.copyOf(selected.values());
    }

    public Parent value() {
        return selected.isEmpty() ? null : selected.values().iterator().next();
    }

    public Long id() {
        return value() == null ? null : value().id();
    }

    public void select(Parent parent) {
        updating = true;
        try {
            if (!multiple) selected.clear();
            if (parent != null) {
                selected.put(parent.id(), parent);
                if (!multiple) {
                    if (single.getItems().stream().noneMatch(p -> p.id() == parent.id()))
                        single.getItems().add(parent);
                    single.setValue(parent);
                }
            }
            render();
        } finally {
            updating = false;
        }
    }

    public void replaceSelected(List<Parent> parents) {
        updating = true;
        try {
            for (Parent p : parents) if (selected.containsKey(p.id())) selected.put(p.id(), p);
            if (!multiple && value() != null) {
                Parent current = value();
                single.getItems().removeIf(p -> p.id() == current.id());
                single.getItems().addFirst(current);
                single.setValue(current);
            }
            render();
        } finally {
            updating = false;
        }
    }

    public void results(ParentPage page, boolean append) {
        updating = true;
        try {
            Parent before = value();
            if (!append) visible.clear();
            page.rows()
                    .forEach(
                            p -> {
                                visible.put(p.id(), p);
                                if (selected.containsKey(p.id())) selected.put(p.id(), p);
                            });
            if (!multiple) {
                var options = new ArrayList<>(visible.values());
                Parent current = before == null ? null : selected.get(before.id());
                if (current != null && options.stream().noneMatch(p -> p.id() == current.id()))
                    options.addFirst(current);
                single.getItems().setAll(options);
                single.setValue(current);
            }
            more.setVisible(page.more());
            more.setManaged(page.more());
            feedback.setText(visible.isEmpty() ? "Nessun risultato." : "");
            render();
        } finally {
            updating = false;
        }
    }

    /** A bulk article group belongs to one line; a line change cannot retain other products. */
    public void restrictToLine(Long lineId) {
        visible.values().removeIf(p -> lineId == null || p.lineId() != lineId);
        selected.values().removeIf(p -> lineId == null || p.lineId() != lineId);
        more.setVisible(false);
        more.setManaged(false);
        render();
    }

    public void error(String text) {
        feedback.setText(text);
    }

    private void render() {
        if (!multiple) return;
        choices.getChildren().clear();
        for (Parent parent : visible.values()) {
            CheckBox check = new CheckBox();
            check.setOnMouseClicked(e -> e.consume());
            check.setSelected(selected.containsKey(parent.id()));
            check.setDisable(!parent.available());
            check.setAccessibleText(parent.code());
            check.setId("ton-bulk-select-product-" + parent.id());
            Label code = new Label(parent.code());
            code.getStyleClass().add("bulk-product-name");
            Label description = new Label(parent.name());
            description.getStyleClass().add("bulk-small");
            description.setWrapText(true);
            VBox labels = new VBox(2, code, description);
            labels.setMinWidth(0);
            HBox.setHgrow(labels, Priority.ALWAYS);
            HBox row = new HBox(8, check, labels);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("bulk-product-option");
            check.setOnAction(
                    e -> {
                        if (check.isSelected()) selected.put(parent.id(), parent);
                        else selected.remove(parent.id());
                        changed.run();
                    });
            row.setOnMouseClicked(
                    e -> {
                        if (e.getTarget() != check && !check.isDisabled()) {
                            check.fire();
                            e.consume();
                        }
                    });
            choices.getChildren().add(row);
        }
        if (visible.isEmpty())
            choices.getChildren().add(new Label("Seleziona una linea per vedere i prodotti."));
    }

    public Button selectAllButton() {
        return selectAll;
    }

    public void selectAll(List<Parent> products) {
        products.stream().filter(Parent::available).forEach(p -> selected.put(p.id(), p));
        render();
        changed.run();
    }
}

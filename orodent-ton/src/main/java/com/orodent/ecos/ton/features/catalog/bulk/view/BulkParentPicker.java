package com.orodent.ecos.ton.features.catalog.bulk.view;

import com.orodent.ecos.ton.features.catalog.bulk.model.BulkCatalogModels.*;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/** Bounded search pages; selected products survive filters and subsequent pages. */
public final class BulkParentPicker extends VBox {
    private final CatalogKind kind;
    private final boolean multiple;
    private final TextField query = new TextField();
    private final ComboBox<Parent> single = new ComboBox<>();
    private final VBox choices = new VBox();
    private final FlowPane tags = new FlowPane(5, 5);
    private final Button more = new Button("Altri risultati…");
    private final Label feedback = new Label();
    private final LinkedHashMap<Long, Parent> visible = new LinkedHashMap<>(),
            selected = new LinkedHashMap<>();
    private Runnable changed = () -> {};
    private boolean updating;

    public BulkParentPicker(CatalogKind kind, boolean multiple, boolean clearable, String id) {
        super(7);
        this.kind = kind;
        this.multiple = multiple;
        setMinWidth(0);
        query.setId(id + "-search");
        query.setPromptText("Cerca " + (kind == CatalogKind.LINES ? "linea" : "prodotto o colore"));
        query.setAccessibleText(query.getPromptText());
        single.setId(id);
        single.setMaxWidth(Double.MAX_VALUE);
        single.setVisibleRowCount(10);
        single.setPromptText(clearable ? "Tutte le linee" : "Seleziona la linea");
        more.setId(id + "-more");
        more.getStyleClass().add("bulk-link-button");
        more.setVisible(false);
        more.setManaged(false);
        feedback.getStyleClass().add("bulk-small");
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        feedback.setWrapText(true);
        feedback.setMinHeight(Region.USE_PREF_SIZE);
        getChildren().add(query);
        if (multiple) {
            ScrollPane scroll = new ScrollPane(choices);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setPrefHeight(180);
            scroll.setMinHeight(130);
            scroll.getStyleClass().add("bulk-product-picker");
            scroll.setId(id);
            tags.getStyleClass().add("bulk-selected-tags");
            getChildren().addAll(scroll, tags);
            Button all = new Button("Seleziona visibili"), clear = new Button("Azzera selezione");
            all.getStyleClass().add("bulk-link-button");
            clear.getStyleClass().add("bulk-link-button");
            all.setId(id + "-all");
            clear.setId(id + "-clear");
            all.setOnAction(
                    e -> {
                        visible.values().stream()
                                .filter(Parent::available)
                                .forEach(p -> selected.put(p.id(), p));
                        render();
                        changed.run();
                    });
            clear.setOnAction(
                    e -> {
                        selected.clear();
                        render();
                        changed.run();
                    });
            FlowPane commands = new FlowPane(5, 5, all, clear);
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
            if (clearable) {
                Button clear = new Button("Tutte le linee");
                clear.getStyleClass().add("bulk-link-button");
                clear.setId(id + "-clear");
                clear.setOnAction(e -> single.setValue(null));
                getChildren().add(clear);
            }
        }
        getChildren().addAll(more, feedback);
    }

    public void onSelection(Runnable action) {
        changed = action;
    }

    public CatalogKind kind() {
        return kind;
    }

    public TextField query() {
        return query;
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
            Label description = new Label(parent.name() + " · " + parent.material());
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
                        renderTags();
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
            choices.getChildren().add(new Label("Cerca i prodotti da selezionare."));
        renderTags();
    }

    private void renderTags() {
        tags.getChildren().clear();
        for (Parent p : selected.values()) {
            Button tag = new Button(p.code() + " ×");
            tag.setAccessibleText("Rimuovi " + p.code() + " dalla selezione");
            tag.getStyleClass().add("bulk-product-tag");
            tag.setOnAction(
                    e -> {
                        selected.remove(p.id());
                        render();
                        changed.run();
                    });
            tags.getChildren().add(tag);
        }
    }
}

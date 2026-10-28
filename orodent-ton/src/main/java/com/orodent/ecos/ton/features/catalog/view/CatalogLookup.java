package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.Choice;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

/** Search is resolved by the service in bounded pages, rather than loading every parent. */
public final class CatalogLookup extends VBox {
    private final TextField query;
    private final ComboBox<Choice> choices = new ComboBox<>();
    private final CatalogKind kind;
    private boolean updating;

    public CatalogLookup(CatalogKind kind, String id, boolean clearable) {
        super(5);
        this.kind = kind;
        setMinWidth(0);
        query = CatalogUi.field(id + "-search", "Cerca " + kind.singular() + " per codice o nome");
        choices.setId(id);
        choices.setPromptText("Seleziona " + kind.singular());
        choices.setMaxWidth(Double.MAX_VALUE);
        choices.setVisibleRowCount(10);
        getChildren().addAll(query, choices);
        if (clearable) {
            Button clear = CatalogUi.button("Tutti", id + "-clear", false);
            clear.setOnAction(
                    e -> {
                        query.clear();
                        choices.setValue(null);
                    });
            getChildren().add(clear);
        }
    }

    public CatalogKind kind() {
        return kind;
    }

    public TextField query() {
        return query;
    }

    public ComboBox<Choice> choices() {
        return choices;
    }

    public boolean updating() {
        return updating;
    }

    public Choice value() {
        return choices.getValue();
    }

    public Long id() {
        return value() == null ? null : value().id();
    }

    public void select(Choice value) {
        updating = true;
        try {
            if (value != null && choices.getItems().stream().noneMatch(c -> c.id() == value.id()))
                choices.getItems().add(value);
            choices.setValue(value);
        } finally {
            updating = false;
        }
    }

    public void results(List<Choice> items) {
        updating = true;
        try {
            Choice selected = value();
            var result = new ArrayList<>(items);
            Choice current =
                    selected == null
                            ? null
                            : result.stream()
                                    .filter(c -> c.id() == selected.id())
                                    .findFirst()
                                    .orElse(selected);
            if (current != null && result.stream().noneMatch(c -> c.id() == current.id()))
                result.addFirst(current);
            choices.getItems().setAll(result);
            choices.setValue(current);
        } finally {
            updating = false;
        }
    }

    public void reset() {
        updating = true;
        try {
            query.clear();
            choices.getItems().clear();
            choices.setValue(null);
        } finally {
            updating = false;
        }
    }
}

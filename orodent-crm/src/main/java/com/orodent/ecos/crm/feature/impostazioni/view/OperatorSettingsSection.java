package com.orodent.ecos.crm.feature.impostazioni.view;

import com.orodent.ecos.crm.core.database.service.OperatorManagementService.Operator;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.util.*;
import java.util.function.*;

/** Inline names, explicit activation and removal actions, including inactive operators. */
public final class OperatorSettingsSection extends VBox {
    public record Rename(UUID id, String name, String expectedName) {}

    private record PendingName(String text, String expectedName) {}

    private final VBox rows = new VBox(8);
    private final Label feedback = new Label();
    private Consumer<Rename> rename = request -> {};
    private BiConsumer<UUID, Boolean> active = (id, value) -> {};
    private Consumer<Operator> remove = operator -> {};
    private Runnable add = () -> {};

    public OperatorSettingsSection() {
        super(10);
        setId("crm-settings-operators");
        getStyleClass().add("settings-section");
        setMinWidth(0);
        Label title = new Label("Operatori");
        title.getStyleClass().add("settings-section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button create = new Button("+ Nuovo operatore");
        create.setId("crm-settings-operator-add");
        create.getStyleClass().add("settings-add-button");
        create.setOnAction(e -> add.run());
        HBox heading = new HBox(10, title, spacer, create);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label help =
                new Label(
                        "Modifica il nome senza cambiare le assegnazioni. Gli operatori disattivati"
                                + " restano nello storico.");
        help.setWrapText(true);
        help.getStyleClass().add("settings-subtitle");
        feedback.setWrapText(true);
        feedback.setId("crm-settings-operator-feedback");
        getChildren().addAll(heading, help, rows, feedback);
    }

    public void onRename(Consumer<Rename> action) {
        rename = action;
    }

    public void onActive(BiConsumer<UUID, Boolean> action) {
        active = action;
    }

    public void onRemove(Consumer<Operator> action) {
        remove = action;
    }

    public void onAdd(Runnable action) {
        add = action;
    }

    public boolean hasPending() {
        return !pendingNames().isEmpty();
    }

    private Map<UUID, PendingName> pendingNames() {
        var pending = new HashMap<UUID, PendingName>();
        for (var node : rows.getChildren()) {
            if (node.getProperties().get("operator") instanceof Operator operator
                    && node.getProperties().get("name") instanceof TextField name
                    && node.getProperties().get("expected-name") instanceof String expected
                    && !name.getText().strip().equals(expected))
                pending.put(operator.id(), new PendingName(name.getText(), expected));
        }
        return pending;
    }

    public void show(List<Operator> operators) {
        var pending = pendingNames();
        rows.getChildren().clear();
        for (var operator : operators) {
            var draft = pending.get(operator.id());
            if (draft != null && draft.text().strip().equals(operator.name())) draft = null;
            String expectedName = draft == null ? operator.name() : draft.expectedName();
            TextField name = new TextField(draft == null ? operator.name() : draft.text());
            name.setMinWidth(60);
            name.setId("crm-operator-name-" + operator.id());
            name.getStyleClass().add("operator-name");
            name.setAccessibleText("Nome operatore " + operator.name());
            HBox.setHgrow(name, Priority.ALWAYS);
            Button save = new Button("Salva");
            save.setId("crm-operator-save-" + operator.id());
            save.getStyleClass().add("operator-save");
            save.setDisable(
                    name.getText().isBlank() || name.getText().strip().equals(operator.name()));
            name.textProperty()
                    .addListener(
                            (o, b, a) ->
                                    save.setDisable(
                                            a.isBlank() || a.strip().equals(operator.name())));
            save.setOnAction(
                    e -> rename.accept(new Rename(operator.id(), name.getText(), expectedName)));
            name.setOnAction(
                    e -> {
                        if (!save.isDisabled()) save.fire();
                    });
            MenuItem toggle = new MenuItem(operator.active() ? "Disattiva" : "Riattiva");
            toggle.setId("crm-operator-active-" + operator.id());
            toggle.setDisable(operator.current());
            toggle.setOnAction(e -> active.accept(operator.id(), !operator.active()));
            MenuItem delete =
                    new MenuItem(
                            operator.linked()
                                    ? (operator.active()
                                            ? "Rimuovi dagli attivi…"
                                            : "Collegato allo storico")
                                    : "Elimina operatore…");
            delete.setId("crm-operator-remove-" + operator.id());
            delete.setDisable(operator.current() || operator.linked() && !operator.active());
            delete.setOnAction(e -> remove.accept(operator));
            MenuItem copyId = new MenuItem("Copia ID operatore");
            copyId.setId("crm-operator-copy-id-" + operator.id());
            copyId.setOnAction(
                    e -> {
                        var content = new ClipboardContent();
                        content.putString(operator.id().toString());
                        Clipboard.getSystemClipboard().setContent(content);
                    });
            MenuButton actions = new MenuButton("⋯", null, copyId, toggle, delete);
            actions.setTooltip(new Tooltip("Azioni operatore"));
            actions.setAccessibleText("Azioni per " + operator.name());
            Label state =
                    new Label(
                            operator.current()
                                    ? "In uso"
                                    : operator.active() ? "Attivo" : "Disattivato");
            state.setId("crm-operator-state-" + operator.id());
            state.getStyleClass().add("settings-subtitle");
            HBox line = new HBox(8, name, save, actions);
            line.setAlignment(Pos.CENTER_LEFT);
            VBox row = new VBox(4, line, state);
            row.getStyleClass().add("settings-forno-row");
            row.getProperties().put("operator", operator);
            row.getProperties().put("name", name);
            row.getProperties().put("expected-name", expectedName);
            rows.getChildren().add(row);
        }
        if (operators.isEmpty()) rows.getChildren().add(new Label("Nessun operatore presente."));
    }

    public void feedback(String text, boolean error) {
        feedback.setText(text);
        feedback.getStyleClass().remove("settings-error");
        if (error) feedback.getStyleClass().add("settings-error");
    }
}

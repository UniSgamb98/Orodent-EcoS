package com.orodent.ecos.crm.feature.impostazioni.view;

import com.orodent.ecos.crm.feature.impostazioni.dto.ImpostazioneVoce;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ImpostazioniEditorSection extends VBox {
    private boolean resuming;

    public void resumeEditing() {
        resuming = true;
        try {
            edit.setSelected(true);
            setEditable(true);
        } finally {
            resuming = false;
        }
    }

    private final List<String> fields;
    private final VBox rows = new VBox(8);
    private final CheckBox edit = new CheckBox("Modifica");
    private final Button add = new Button("+");

    public ImpostazioniEditorSection(String title, List<String> fields) {
        super(10);
        this.fields = List.copyOf(fields);
        getStyleClass().add("settings-section");
        Label heading = new Label(title);
        heading.getStyleClass().add("settings-section-title");
        add.getStyleClass().add("settings-add-button");
        add.setVisible(false);
        add.setManaged(false);
        getChildren().addAll(heading, new HBox(8, edit, add), rows);
    }

    public void onEditChanged(Consumer<Boolean> action) {
        edit.selectedProperty()
                .addListener(
                        (o, old, selected) -> {
                            setEditable(selected);
                            if (!resuming) action.accept(selected);
                        });
    }

    public void onAdd(Runnable action) {
        add.setOnAction(e -> action.run());
    }

    public void setVoci(List<ImpostazioneVoce> voci) {
        rows.getChildren().clear();
        voci.forEach(this::addRow);
        setEditable(edit.isSelected());
    }

    public void addEmpty() {
        addRow(new ImpostazioneVoce(null, java.util.Collections.nCopies(fields.size(), "")));
        setEditable(true);
    }

    public List<ImpostazioneVoce> getVoci() {
        List<ImpostazioneVoce> result = new ArrayList<>();
        for (var node : rows.getChildren())
            if (node instanceof VBox row) {
                List<String> values = new ArrayList<>();
                for (int i = 0; i < fields.size(); i++)
                    values.add(((TextField) row.getProperties().get("field" + i)).getText());
                result.add(
                        new ImpostazioneVoce(
                                (java.util.UUID) row.getProperties().get("id"), values));
            }
        return result;
    }

    private void addRow(ImpostazioneVoce voce) {
        VBox row = new VBox(5);
        row.getStyleClass().add("settings-forno-row");
        row.getProperties().put("id", voce.id());
        for (int i = 0; i < fields.size(); i++) {
            String value = voce.valori().get(i);
            TextField field = new TextField(value == null ? "" : value);
            field.setPromptText(fields.get(i));
            row.getProperties().put("field" + i, field);
            row.getChildren().add(field);
        }
        Button remove = new Button("−");
        remove.getStyleClass().add("settings-remove-button");
        remove.setOnAction(e -> rows.getChildren().remove(row));
        row.getProperties().put("remove", remove);
        row.getChildren().add(remove);
        rows.getChildren().add(row);
    }

    private void setEditable(boolean editable) {
        add.setVisible(editable);
        add.setManaged(editable);
        for (var node : rows.getChildren())
            if (node instanceof VBox row) {
                for (int i = 0; i < fields.size(); i++)
                    ((TextField) row.getProperties().get("field" + i)).setEditable(editable);
                Button remove = (Button) row.getProperties().get("remove");
                remove.setVisible(editable);
                remove.setManaged(editable);
            }
    }
}

package com.orodent.ecos.crm.feature.impostazioni.view;

import com.orodent.ecos.crm.core.ui.AppSidebar;
import com.orodent.ecos.crm.feature.impostazioni.dto.Forno;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ImpostazioniView extends BorderPane {

    private static final String[] SETTINGS_AREAS = {
        "Materiale di Consumo", "Canali di acquisto", "Parco Fresatori", "Forni"
    };

    private final AppSidebar sidebar = new AppSidebar();
    private final VBox forniRows = new VBox(8);
    private final CheckBox modificaForni = new CheckBox("Modifica");
    private final Button addForno = new Button("+");
    private final Map<String, ImpostazioniEditorSection> editors = new HashMap<>();

    public ImpostazioniView() {
        setLeft(sidebar);
        setCenter(createContent());
    }

    public AppSidebar getSidebar() {
        return sidebar;
    }

    public void onForniEditChanged(Consumer<Boolean> action) {
        modificaForni
                .selectedProperty()
                .addListener(
                        (o, old, selected) -> {
                            setForniEditable(selected);
                            action.accept(selected);
                        });
    }

    public void onAddForno(Runnable action) {
        addForno.setOnAction(event -> action.run());
    }

    public void setForni(List<Forno> forni) {
        forniRows.getChildren().clear();
        forni.forEach(this::addFornoRow);
        setForniEditable(modificaForni.isSelected());
    }

    public void addEmptyForno() {
        addFornoRow(new Forno(null, "", "", ""));
        setForniEditable(true);
    }

    public List<Forno> getForni() {
        List<Forno> result = new ArrayList<>();
        for (var node : forniRows.getChildren())
            if (node instanceof VBox row)
                result.add(
                        new Forno(
                                (java.util.UUID) row.getProperties().get("id"),
                                ((TextField) row.getProperties().get("t")).getText(),
                                ((TextField) row.getProperties().get("m")).getText(),
                                ((TextField) row.getProperties().get("mo")).getText()));
        return result;
    }

    public void showForniSaveError() {
        Label error = new Label("Salvataggio forni non riuscito.");
        error.getStyleClass().add("settings-error");
        forniRows.getChildren().add(error);
    }

    public ImpostazioniEditorSection getEditor(String table) {
        return editors.get(table);
    }

    private VBox createContent() {
        VBox content = new VBox(18);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("settings-content");

        Label title = new Label("Impostazioni");
        title.getStyleClass().add("settings-title");
        Label subtitle = new Label("Gestisci le configurazioni dell'applicazione.");
        subtitle.getStyleClass().add("settings-subtitle");

        GridPane sections = new GridPane();
        sections.setHgap(14);
        sections.setVgap(14);
        sections.getStyleClass().add("settings-sections");
        for (int column = 0; column < 3; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100d / 3);
            constraints.setHgrow(Priority.ALWAYS);
            sections.getColumnConstraints().add(constraints);
        }

        for (int index = 0; index < SETTINGS_AREAS.length; index++) {
            sections.add(createSection(SETTINGS_AREAS[index]), index % 3, index / 3);
        }

        content.getChildren().addAll(title, subtitle, sections);
        return content;
    }

    private VBox createSection(String title) {
        String table =
                switch (title) {
                    case "Materiale di Consumo" -> "MATERIALI_DI_CONSUMO";
                    case "Canali di acquisto" -> "CANALI_DI_ACQUISTO";
                    case "Parco Fresatori" -> "FRESATORI";
                    default -> "FORNI";
                };
        List<String> fields =
                switch (table) {
                    case "MATERIALI_DI_CONSUMO" -> List.of("Materiale", "Marchio", "Modello");
                    case "CANALI_DI_ACQUISTO" -> List.of("Modalità", "Nota");
                    case "FRESATORI" -> List.of("Marca", "Modello");
                    default -> List.of("Tecnologia", "Marca", "Modello");
                };
        ImpostazioniEditorSection editor = new ImpostazioniEditorSection(title, fields);
        editors.put(table, editor);
        return editor;
    }

    private VBox createForniSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("settings-section");
        Label title = new Label("Forni");
        title.getStyleClass().add("settings-section-title");
        addForno.getStyleClass().add("settings-add-button");
        addForno.setVisible(false);
        addForno.setManaged(false);
        section.getChildren()
                .addAll(title, new javafx.scene.layout.HBox(8, modificaForni, addForno), forniRows);
        return section;
    }

    private void addFornoRow(Forno forno) {
        VBox row = new VBox(5);
        row.getStyleClass().add("settings-forno-row");
        TextField tecnologia = new TextField(forno.tecnologia());
        tecnologia.setPromptText("Tecnologia");
        TextField marca = new TextField(forno.marca());
        marca.setPromptText("Marca");
        TextField modello = new TextField(forno.modello());
        modello.setPromptText("Modello");
        Button remove = new Button("−");
        remove.getStyleClass().add("settings-remove-button");
        remove.setOnAction(event -> forniRows.getChildren().remove(row));
        row.getProperties().put("id", forno.id());
        row.getProperties().put("t", tecnologia);
        row.getProperties().put("m", marca);
        row.getProperties().put("mo", modello);
        row.getProperties().put("r", remove);
        row.getChildren().addAll(tecnologia, marca, modello, remove);
        forniRows.getChildren().add(row);
    }

    private void setForniEditable(boolean editable) {
        addForno.setVisible(editable);
        addForno.setManaged(editable);
        for (var node : forniRows.getChildren())
            if (node instanceof VBox row) {
                ((TextField) row.getProperties().get("t")).setEditable(editable);
                ((TextField) row.getProperties().get("m")).setEditable(editable);
                ((TextField) row.getProperties().get("mo")).setEditable(editable);
                Button remove = (Button) row.getProperties().get("r");
                remove.setVisible(editable);
                remove.setManaged(editable);
            }
    }
}

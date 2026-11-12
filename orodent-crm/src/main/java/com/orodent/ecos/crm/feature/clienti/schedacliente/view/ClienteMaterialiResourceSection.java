package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeClienteItem;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Sezione autonoma per visualizzare e modificare i materiali di consumo associati al cliente. */
final class ClienteMaterialiResourceSection {

    private final ClienteResourceSection section =
            new ClienteResourceSection("Materiali", "+ Materiale");
    private final List<MaterialeCatalogItem> catalog = new ArrayList<>();
    private final List<MaterialeEditorRow> editorRows = new ArrayList<>();

    ClienteMaterialiResourceSection() {
        section.addButton().setOnAction(event -> addEditorRow(emptyMateriale()));
        section.hideActions();
    }

    ClienteResourceSection root() {
        return section;
    }

    void setCatalog(List<MaterialeCatalogItem> values) {
        catalog.clear();
        if (values != null) {
            catalog.addAll(values);
        }
        editorRows.forEach(this::refreshSuggestions);
    }

    void render(List<MaterialeClienteItem> materiali) {
        section.clearCards();
        editorRows.clear();
        section.showViewActions();

        if (materiali == null || materiali.isEmpty()) {
            section.addCard(createEmptyLabel("Nessun materiale associato"));
            return;
        }

        for (MaterialeClienteItem materiale : materiali) {
            section.addCard(createCard(materiale));
        }
    }

    void renderEditor(List<MaterialeClienteEditInput> materiali) {
        section.clearCards();
        editorRows.clear();
        section.showEditActions();

        if (materiali == null || materiali.isEmpty()) {
            section.addCard(createEmptyLabel("Nessun materiale associato"));
            return;
        }

        materiali.forEach(this::addEditorRow);
    }

    List<MaterialeClienteEditInput> collect() {
        return editorRows.stream().map(MaterialeEditorRow::toInput).toList();
    }

    Button editButton() {
        return section.editButton();
    }

    Button saveButton() {
        return section.saveButton();
    }

    Button cancelButton() {
        return section.cancelButton();
    }

    private VBox createCard(MaterialeClienteItem materiale) {
        VBox card = new VBox(8);
        card.getStyleClass().add("client-profile-forno-card");
        Label materialTitle =
                new Label(
                        display(materiale.materiale()).isBlank()
                                ? "Materiale"
                                : display(materiale.materiale()));
        materialTitle.getStyleClass().add("client-profile-material-title");
        card.getChildren()
                .addAll(
                        materialTitle,
                        createDisplayRow(
                                "Marchio: " + display(materiale.marchio()),
                                "Modello: " + display(materiale.modello())),
                        createDisplayRow(
                                "Consumo: " + display(materiale.consumo()),
                                "Frequenza acquisto: " + display(materiale.frequenzaAcquisto())));
        if (materiale.nota() != null && !materiale.nota().isBlank()) {
            Label note = new Label("Nota: " + materiale.nota());
            note.getStyleClass().add("client-profile-resource-note");
            card.getChildren().add(note);
        }
        return card;
    }

    private void addEditorRow(MaterialeClienteEditInput materiale) {
        VBox card = new VBox(8);
        card.getStyleClass().add("client-profile-forno-card");

        ResourceAutocompleteComboBox materialeField =
                createAutocompleteComboBox(materiale.materiale(), "Materiale");
        ResourceAutocompleteComboBox marchio =
                createAutocompleteComboBox(materiale.marchio(), "Marchio");
        ResourceAutocompleteComboBox modello =
                createAutocompleteComboBox(materiale.modello(), "Modello");
        TextField consumo = new TextField(display(materiale.consumo()));
        consumo.setPromptText("Consumo");
        TextField frequenzaAcquisto = new TextField(display(materiale.frequenzaAcquisto()));
        frequenzaAcquisto.setPromptText("Frequenza acquisto");
        TextArea nota = new TextArea(display(materiale.nota()));
        nota.setPromptText("Nota cliente-materiale");
        nota.getStyleClass().add("client-profile-resource-note-editor");

        Button remove = new Button("−");
        remove.getStyleClass().add("client-profile-delete-interaction-button");

        MaterialeEditorRow editorRow =
                new MaterialeEditorRow(
                        materiale.id(),
                        materiale.materialeId(),
                        card,
                        materialeField,
                        marchio,
                        modello,
                        consumo,
                        frequenzaAcquisto,
                        nota);
        editorRows.add(editorRow);
        remove.setOnAction(
                event -> {
                    editorRows.remove(editorRow);
                    section.removeCard(card);
                });

        card.getChildren()
                .addAll(
                        createSingleAutocompleteLine("Materiale", materialeField),
                        createEditorLine("Marchio", marchio, "Modello", modello),
                        createConsumptionFrequencyLine(consumo, frequenzaAcquisto),
                        ClienteProfileFormControls.resourceField("Note", nota),
                        remove);
        section.addEditorCard(
                card,
                materiale.id() == null,
                "materiale",
                () ->
                        ClienteProfileFormControls.summary(
                                materialeField.textValue(),
                                marchio.textValue(),
                                modello.textValue()),
                () ->
                        ClienteProfileFormControls.summary(
                                consumo.getText(), frequenzaAcquisto.getText()),
                remove,
                materialeField.getEditor().textProperty(),
                marchio.getEditor().textProperty(),
                modello.getEditor().textProperty(),
                consumo.textProperty(),
                frequenzaAcquisto.textProperty());
        refreshSuggestions(editorRow);
    }

    private ResourceAutocompleteComboBox createAutocompleteComboBox(String value, String prompt) {
        ResourceAutocompleteComboBox comboBox =
                new ResourceAutocompleteComboBox(
                        value,
                        prompt,
                        field ->
                                editorRows.stream()
                                        .filter(row -> row.contains(field))
                                        .findFirst()
                                        .ifPresent(row -> refreshSuggestions(row, field)));
        comboBox.setOnShowing(
                event ->
                        editorRows.stream()
                                .filter(row -> row.contains(comboBox))
                                .findFirst()
                                .ifPresent(this::refreshSuggestions));
        return comboBox;
    }

    private void refreshSuggestions(MaterialeEditorRow row) {
        refreshSuggestions(row, null);
    }

    private void refreshSuggestions(
            MaterialeEditorRow row, ResourceAutocompleteComboBox activeField) {
        updateSuggestions(row.materiale(), MaterialeCatalogItem::materiale, row, activeField);
        updateSuggestions(row.marchio(), MaterialeCatalogItem::marchio, row, activeField);
        updateSuggestions(row.modello(), MaterialeCatalogItem::modello, row, activeField);
    }

    private void updateSuggestions(
            ResourceAutocompleteComboBox field,
            Function<MaterialeCatalogItem, String> extractor,
            MaterialeEditorRow row,
            ResourceAutocompleteComboBox activeField) {
        field.setSuggestions(suggestionsFor(extractor, row, field), field == activeField);
    }

    private List<String> suggestionsFor(
            Function<MaterialeCatalogItem, String> extractor,
            MaterialeEditorRow row,
            ResourceAutocompleteComboBox field) {
        Set<String> values = new LinkedHashSet<>();
        for (MaterialeCatalogItem item : catalog) {
            if (matches(row.materiale(), item.materiale(), field)
                    && matches(row.marchio(), item.marchio(), field)
                    && matches(row.modello(), item.modello(), field)) {
                String value = extractor.apply(item);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return new ArrayList<>(values);
    }

    private boolean matches(
            ResourceAutocompleteComboBox field,
            String candidate,
            ResourceAutocompleteComboBox ignoredField) {
        if (field == ignoredField) {
            return true;
        }
        String value = display(field.textValue());
        return value.isBlank() || display(candidate).toLowerCase().contains(value.toLowerCase());
    }

    private HBox createDisplayRow(String first, String second) {
        HBox row = new HBox(18);
        row.getStyleClass().add("client-profile-forno-row");
        Label firstLabel = new Label(first);
        Label secondLabel = new Label(second);
        firstLabel.getStyleClass().add("client-profile-resource-value");
        secondLabel.getStyleClass().add("client-profile-resource-value");
        row.getChildren().addAll(firstLabel, secondLabel);
        return row;
    }

    private FlowPane createEditorLine(
            String firstLabel,
            ResourceAutocompleteComboBox firstField,
            String secondLabel,
            ResourceAutocompleteComboBox secondField) {
        FlowPane row = new FlowPane(10, 8);
        row.getStyleClass().add("client-profile-forno-row");
        row.getChildren()
                .addAll(
                        createEditorField(firstLabel, firstField),
                        createEditorField(secondLabel, secondField));
        return row;
    }

    private FlowPane createSingleAutocompleteLine(
            String label, ResourceAutocompleteComboBox field) {
        FlowPane row = new FlowPane(10, 8);
        row.getStyleClass().add("client-profile-forno-row");
        row.getChildren().add(createEditorField(label, field));
        return row;
    }

    private FlowPane createConsumptionFrequencyLine(
            TextField consumo, TextField frequenzaAcquisto) {
        FlowPane row = new FlowPane(10, 8);
        row.getStyleClass().add("client-profile-forno-row");
        row.getChildren()
                .addAll(
                        createTextField("Consumo", consumo),
                        createTextField("Frequenza acquisto", frequenzaAcquisto));
        return row;
    }

    private HBox createTextField(String labelText, TextField field) {
        return ClienteProfileFormControls.resourceField(labelText, field);
    }

    private HBox createEditorField(String labelText, ResourceAutocompleteComboBox field) {
        return ClienteProfileFormControls.resourceField(labelText, field);
    }

    private Label createEmptyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("client-profile-info-label");
        return label;
    }

    private MaterialeClienteEditInput emptyMateriale() {
        return new MaterialeClienteEditInput(null, null, "", "", "", "", "", "");
    }

    private String display(String value) {
        return value == null ? "" : value;
    }

    private record MaterialeEditorRow(
            UUID id,
            UUID materialeId,
            VBox card,
            ResourceAutocompleteComboBox materiale,
            ResourceAutocompleteComboBox marchio,
            ResourceAutocompleteComboBox modello,
            TextField consumo,
            TextField frequenzaAcquisto,
            TextArea nota) {
        private boolean contains(ResourceAutocompleteComboBox field) {
            return materiale == field || marchio == field || modello == field;
        }

        private MaterialeClienteEditInput toInput() {
            return new MaterialeClienteEditInput(
                    id,
                    materialeId,
                    text(materiale),
                    text(marchio),
                    text(modello),
                    consumo.getText().trim(),
                    frequenzaAcquisto.getText().trim(),
                    nota.getText());
        }

        private static String text(ResourceAutocompleteComboBox field) {
            return field.trimmedTextValue();
        }
    }
}

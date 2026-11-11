package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreClienteItem;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Sezione autonoma per visualizzare e modificare i fresatori associati al cliente. */
final class ClienteFresatoriResourceSection {

    private final ClienteResourceSection section =
            new ClienteResourceSection("Fresatori", "+ Fresatore");
    private final List<FresatoreCatalogItem> catalog = new ArrayList<>();
    private final List<FresatoreEditorRow> editorRows = new ArrayList<>();

    ClienteFresatoriResourceSection() {
        section.addButton().setOnAction(event -> addEditorRow(emptyFresatore()));
        section.hideActions();
    }

    ClienteResourceSection root() {
        return section;
    }

    void setCatalog(List<FresatoreCatalogItem> values) {
        catalog.clear();
        if (values != null) {
            catalog.addAll(values);
        }
        editorRows.forEach(this::refreshSuggestions);
    }

    void render(List<FresatoreClienteItem> fresatori) {
        section.clearCards();
        editorRows.clear();
        section.showViewActions();

        if (fresatori == null || fresatori.isEmpty()) {
            section.addCard(createEmptyLabel("Nessun fresatore associato"));
            return;
        }

        for (FresatoreClienteItem fresatore : fresatori) {
            section.addCard(createCard(fresatore));
        }
    }

    void renderEditor(List<FresatoreClienteEditInput> fresatori) {
        section.clearCards();
        editorRows.clear();
        section.showEditActions();

        if (fresatori == null || fresatori.isEmpty()) {
            addEditorRow(emptyFresatore());
            return;
        }

        fresatori.forEach(this::addEditorRow);
    }

    List<FresatoreClienteEditInput> collect() {
        return editorRows.stream().map(FresatoreEditorRow::toInput).toList();
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

    private VBox createCard(FresatoreClienteItem fresatore) {
        VBox card = new VBox(8);
        card.getStyleClass().add("client-profile-forno-card");
        card.getChildren()
                .add(
                        createDisplayRow(
                                "Marca: " + display(fresatore.marca()),
                                "Modello: " + display(fresatore.modello())));
        if (fresatore.nota() != null && !fresatore.nota().isBlank()) {
            Label note = new Label("Nota: " + fresatore.nota());
            note.getStyleClass().add("client-profile-resource-note");
            card.getChildren().add(note);
        }
        return card;
    }

    private void addEditorRow(FresatoreClienteEditInput fresatore) {
        VBox card = new VBox(8);
        card.getStyleClass().add("client-profile-forno-card");

        ResourceAutocompleteComboBox marca = createAutocompleteComboBox(fresatore.marca(), "Marca");
        ResourceAutocompleteComboBox modello =
                createAutocompleteComboBox(fresatore.modello(), "Modello");
        TextArea nota = new TextArea(display(fresatore.nota()));
        nota.setPromptText("Nota cliente-fresatore");
        nota.getStyleClass().add("client-profile-resource-note-editor");

        Button remove = new Button("−");
        remove.getStyleClass().add("client-profile-delete-interaction-button");

        FresatoreEditorRow editorRow =
                new FresatoreEditorRow(
                        fresatore.id(), fresatore.fresatoreId(), card, marca, modello, nota);
        editorRows.add(editorRow);
        remove.setOnAction(
                event -> {
                    editorRows.remove(editorRow);
                    section.removeCard(card);
                });

        card.getChildren()
                .addAll(createEditorLine("Marca", marca, "Modello", modello), nota, remove);
        section.addCard(card);
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

    private void refreshSuggestions(FresatoreEditorRow row) {
        refreshSuggestions(row, null);
    }

    private void refreshSuggestions(
            FresatoreEditorRow row, ResourceAutocompleteComboBox activeField) {
        updateSuggestions(row.marca(), FresatoreCatalogItem::marca, row, activeField);
        updateSuggestions(row.modello(), FresatoreCatalogItem::modello, row, activeField);
    }

    private void updateSuggestions(
            ResourceAutocompleteComboBox field,
            Function<FresatoreCatalogItem, String> extractor,
            FresatoreEditorRow row,
            ResourceAutocompleteComboBox activeField) {
        field.setSuggestions(suggestionsFor(extractor, row, field), field == activeField);
    }

    private List<String> suggestionsFor(
            Function<FresatoreCatalogItem, String> extractor,
            FresatoreEditorRow row,
            ResourceAutocompleteComboBox field) {
        Set<String> values = new LinkedHashSet<>();
        for (FresatoreCatalogItem item : catalog) {
            if (matches(row.marca(), item.marca(), field)
                    && matches(row.modello(), item.modello(), field)) {
                String value = extractor.apply(item);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return new ArrayList<>(values);
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

    private HBox createEditorLine(
            String firstLabel,
            ResourceAutocompleteComboBox firstField,
            String secondLabel,
            ResourceAutocompleteComboBox secondField) {
        HBox row = new HBox(10);
        row.getStyleClass().add("client-profile-forno-row");
        row.getChildren()
                .addAll(
                        createEditorField(firstLabel, firstField),
                        createEditorField(secondLabel, secondField));
        return row;
    }

    private VBox createEditorField(String labelText, ResourceAutocompleteComboBox field) {
        VBox wrapper = new VBox(4);
        Label label = new Label(labelText + ":");
        label.getStyleClass().add("client-profile-resource-field-label");
        wrapper.getChildren().addAll(label, field);
        return wrapper;
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

    private Label createEmptyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("client-profile-info-label");
        return label;
    }

    private FresatoreClienteEditInput emptyFresatore() {
        return new FresatoreClienteEditInput(null, null, "", "", "");
    }

    private String display(String value) {
        return value == null ? "" : value;
    }

    private record FresatoreEditorRow(
            UUID id,
            UUID fresatoreId,
            VBox card,
            ResourceAutocompleteComboBox marca,
            ResourceAutocompleteComboBox modello,
            TextArea nota) {
        private boolean contains(ResourceAutocompleteComboBox field) {
            return marca == field || modello == field;
        }

        private FresatoreClienteEditInput toInput() {
            return new FresatoreClienteEditInput(
                    id, fresatoreId, text(marca), text(modello), nota.getText());
        }

        private static String text(ResourceAutocompleteComboBox field) {
            return field.trimmedTextValue();
        }
    }
}

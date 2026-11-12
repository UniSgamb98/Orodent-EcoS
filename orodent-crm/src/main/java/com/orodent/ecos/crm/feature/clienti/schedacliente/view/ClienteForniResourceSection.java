package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoClienteItem;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Sezione autonoma per visualizzare e modificare i forni associati al cliente. */
final class ClienteForniResourceSection {

    private final ClienteResourceSection section = new ClienteResourceSection("Forni", "+ Forno");
    private final List<FornoCatalogItem> catalog = new ArrayList<>();
    private final List<FornoEditorRow> editorRows = new ArrayList<>();

    ClienteForniResourceSection() {
        section.addButton().setOnAction(event -> addEditorRow(emptyForno()));
    }

    ClienteResourceSection root() {
        return section;
    }

    void setCatalog(List<FornoCatalogItem> values) {
        catalog.clear();
        if (values != null) {
            catalog.addAll(values);
        }
        editorRows.forEach(this::refreshSuggestions);
    }

    void render(List<FornoClienteItem> forni) {
        section.clearCards();
        editorRows.clear();
        section.showViewActions();

        if (forni == null || forni.isEmpty()) {
            section.addCard(createEmptyLabel("Nessun forno associato"));
            return;
        }

        for (FornoClienteItem forno : forni) {
            section.addCard(createCard(forno));
        }
    }

    void renderEditor(List<FornoClienteEditInput> forni) {
        section.clearCards();
        editorRows.clear();
        section.showEditActions();

        if (forni == null || forni.isEmpty()) {
            section.addCard(createEmptyLabel("Nessun forno associato"));
            return;
        }

        forni.forEach(this::addEditorRow);
    }

    List<FornoClienteEditInput> collect() {
        return editorRows.stream().map(FornoEditorRow::toInput).toList();
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

    private VBox createCard(FornoClienteItem forno) {
        VBox card = new VBox(8);
        card.getStyleClass().add("client-profile-forno-card");
        card.getChildren()
                .addAll(
                        createDisplayRow(
                                "Tecnologia: " + display(forno.tecnologia()),
                                "Anno: " + display(forno.anno())),
                        createDisplayRow(
                                "Marca: " + display(forno.marca()),
                                "Modello: " + display(forno.modello())));
        if (forno.nota() != null && !forno.nota().isBlank()) {
            Label note = new Label("Nota: " + forno.nota());
            note.getStyleClass().add("client-profile-resource-note");
            card.getChildren().add(note);
        }
        return card;
    }

    private void addEditorRow(FornoClienteEditInput forno) {
        VBox card = new VBox(8);
        card.getStyleClass().add("client-profile-forno-card");

        ResourceAutocompleteComboBox tecnologia =
                createAutocompleteComboBox(forno.tecnologia(), "Tecnologia");
        ResourceAutocompleteComboBox anno = createAutocompleteComboBox(forno.anno(), "Anno");
        ResourceAutocompleteComboBox marca = createAutocompleteComboBox(forno.marca(), "Marca");
        ResourceAutocompleteComboBox modello =
                createAutocompleteComboBox(forno.modello(), "Modello");
        TextArea nota = new TextArea(display(forno.nota()));
        nota.setPromptText("Nota cliente-forno");
        nota.getStyleClass().add("client-profile-resource-note-editor");

        Button remove = new Button("−");
        remove.getStyleClass().add("client-profile-delete-interaction-button");

        FornoEditorRow editorRow =
                new FornoEditorRow(
                        forno.id(), forno.fornoId(), card, tecnologia, anno, marca, modello, nota);
        editorRows.add(editorRow);
        remove.setOnAction(
                event -> {
                    editorRows.remove(editorRow);
                    section.removeCard(card);
                });

        card.getChildren()
                .addAll(
                        createEditorLine("Tecnologia", tecnologia, "Anno", anno),
                        createEditorLine("Marca", marca, "Modello", modello),
                        ClienteProfileFormControls.resourceField("Note", nota),
                        remove);
        section.addEditorCard(
                card,
                forno.id() == null,
                "forno",
                () -> ClienteProfileFormControls.summary(marca.textValue(), modello.textValue()),
                () -> ClienteProfileFormControls.summary(tecnologia.textValue(), anno.textValue()),
                remove,
                marca.getEditor().textProperty(),
                modello.getEditor().textProperty(),
                tecnologia.getEditor().textProperty(),
                anno.getEditor().textProperty());
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

    private void refreshSuggestions(FornoEditorRow row) {
        refreshSuggestions(row, null);
    }

    private void refreshSuggestions(FornoEditorRow row, ResourceAutocompleteComboBox activeField) {
        updateSuggestions(row.tecnologia(), FornoCatalogItem::tecnologia, row, false, activeField);
        updateAnnoSuggestions(row, activeField);
        updateSuggestions(row.marca(), FornoCatalogItem::marca, row, false, activeField);
        updateSuggestions(row.modello(), FornoCatalogItem::modello, row, true, activeField);
    }

    private void updateAnnoSuggestions(
            FornoEditorRow row, ResourceAutocompleteComboBox activeField) {
        List<String> values =
                editorRows.stream()
                        .map(editorRow -> editorRow.anno().trimmedTextValue())
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .toList();
        row.anno().setSuggestions(values, row.anno() == activeField);
    }

    private void updateSuggestions(
            ResourceAutocompleteComboBox field,
            Function<FornoCatalogItem, String> extractor,
            FornoEditorRow row,
            boolean filterByMarca,
            ResourceAutocompleteComboBox activeField) {
        field.setSuggestions(
                suggestionsFor(extractor, row, field, filterByMarca), field == activeField);
    }

    private List<String> suggestionsFor(
            Function<FornoCatalogItem, String> extractor,
            FornoEditorRow row,
            ResourceAutocompleteComboBox field,
            boolean filterByMarca) {
        Set<String> values = new LinkedHashSet<>();
        for (FornoCatalogItem item : catalog) {
            if (matches(row.tecnologia(), item.tecnologia(), field)
                    && matches(row.marca(), item.marca(), field)
                    && (!filterByMarca || matches(row.marca(), item.marca(), null))) {
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

    private HBox createEditorField(String labelText, ResourceAutocompleteComboBox field) {
        return ClienteProfileFormControls.resourceField(labelText, field);
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

    private FornoClienteEditInput emptyForno() {
        return new FornoClienteEditInput(null, null, "", "", "", "", "");
    }

    private String display(String value) {
        return value == null ? "" : value;
    }

    private record FornoEditorRow(
            UUID id,
            UUID fornoId,
            VBox card,
            ResourceAutocompleteComboBox tecnologia,
            ResourceAutocompleteComboBox anno,
            ResourceAutocompleteComboBox marca,
            ResourceAutocompleteComboBox modello,
            TextArea nota) {
        private boolean contains(ResourceAutocompleteComboBox field) {
            return tecnologia == field || anno == field || marca == field || modello == field;
        }

        private FornoClienteEditInput toInput() {
            return new FornoClienteEditInput(
                    id,
                    fornoId,
                    text(tecnologia),
                    text(anno),
                    text(marca),
                    text(modello),
                    nota.getText());
        }

        private static String text(ResourceAutocompleteComboBox field) {
            return field.trimmedTextValue();
        }
    }
}

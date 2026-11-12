package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ContactEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ContactItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ValueEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ValueItem;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

final class ClienteContactsSection extends VBox {

    private final List<ContactEditControls> contactEditControls = new ArrayList<>();
    private List<TextField> companyPhoneFields = List.of();
    private List<TextField> companyEmailFields = List.of();

    ClienteContactsSection() {
        super(8);
    }

    void setCompanyValueSources(List<TextField> phoneFields, List<TextField> emailFields) {
        companyPhoneFields = phoneFields == null ? List.of() : phoneFields;
        companyEmailFields = emailFields == null ? List.of() : emailFields;
        refreshLinkedContactOptions();
    }

    void render(List<ContactItem> values) {
        getChildren().clear();
        if (values.isEmpty()) {
            getChildren()
                    .add(ClienteProfileFormControls.createInfoLabel("Nessun dato disponibile"));
            return;
        }
        values.forEach(contact -> getChildren().add(createContactCard(contact)));
    }

    void renderEditor(List<ContactEditInput> values) {
        getChildren().clear();
        contactEditControls.clear();
        List<ContactEditInput> safeValues =
                values.isEmpty()
                        ? List.of(new ContactEditInput(null, "", List.of(), List.of()))
                        : values;
        safeValues.forEach(this::addContactEditor);
    }

    List<ContactEditInput> collectContacts() {
        return contactEditControls.stream()
                .map(
                        control ->
                                new ContactEditInput(
                                        control.id(),
                                        valueOf(control.descriptionField()),
                                        linkedValuesOf(control.phoneFields(), companyPhoneFields),
                                        linkedValuesOf(control.emailFields(), companyEmailFields)))
                .toList();
    }

    void refreshLinkedContactOptions() {
        contactEditControls.forEach(
                control -> {
                    refreshLinkedComboOptions(control.phoneFields(), companyPhoneFields);
                    refreshLinkedComboOptions(control.emailFields(), companyEmailFields);
                });
    }

    private void addContactEditor(ContactEditInput value) {
        VBox card = new VBox(8);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("client-profile-timeline-card");
        TextField descriptionField =
                ClienteProfileFormControls.createTextField(
                        value.descrizione(), "Nome referente / contatto");

        List<ComboBox<String>> phoneFields = new ArrayList<>();
        List<ComboBox<String>> emailFields = new ArrayList<>();
        VBox phoneBox =
                createLinkedEditableValuesSection(
                        "Telefoni contatto",
                        phoneFields,
                        value.telefoni(),
                        "Telefono contatto",
                        linkedOptions(companyPhoneFields, value.telefoni()));
        VBox emailBox =
                createLinkedEditableValuesSection(
                        "Email contatto",
                        emailFields,
                        value.email(),
                        "Email contatto",
                        linkedOptions(companyEmailFields, value.email()));

        HBox actions = new HBox(8);
        Button addButton = ClienteProfileFormControls.createSmallButton("+");
        Button removeButton = ClienteProfileFormControls.createSmallButton("-");
        addButton.setOnAction(
                event -> addContactEditor(new ContactEditInput(null, "", List.of(), List.of())));
        removeButton.setOnAction(
                event -> {
                    getChildren().remove(card);
                    contactEditControls.removeIf(control -> control.container() == card);
                    if (contactEditControls.isEmpty()) {
                        addContactEditor(new ContactEditInput(null, "", List.of(), List.of()));
                    }
                });
        actions.getChildren().addAll(addButton, removeButton);

        card.getChildren()
                .addAll(
                        ClienteProfileFormControls.createFieldRow("Contatto", descriptionField),
                        phoneBox,
                        emailBox,
                        actions);
        getChildren().add(card);
        contactEditControls.add(
                new ContactEditControls(
                        value.id(), descriptionField, phoneFields, emailFields, card));
    }

    private VBox createContactCard(ContactItem contact) {
        VBox card = new VBox(10);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("client-profile-contact-card");

        Label name = new Label(emptyFallback(contact.descrizione()));
        name.setWrapText(true);
        name.getStyleClass().add("client-profile-contact-name");
        card.getChildren().add(name);

        if (!contact.telefoni().isEmpty()) {
            card.getChildren().add(createContactValueGroup("Telefoni", contact.telefoni()));
        }
        if (!contact.email().isEmpty()) {
            card.getChildren().add(createContactValueGroup("Email", contact.email()));
        }
        return card;
    }

    private VBox createContactValueGroup(String title, List<ValueItem> values) {
        VBox group = new VBox(4);
        group.getStyleClass().add("client-profile-contact-value-group");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("client-profile-contact-value-title");
        group.getChildren().add(titleLabel);
        values.stream()
                .map(ValueItem::value)
                .filter(value -> value != null && !value.isBlank())
                .forEach(
                        value -> {
                            group.getChildren()
                                    .add(ClienteProfileFormControls.copyableValue(value));
                        });
        return group;
    }

    private VBox createLinkedEditableValuesSection(
            String title,
            List<ComboBox<String>> target,
            List<ValueEditInput> values,
            String prompt,
            List<String> options) {
        VBox section = new VBox(8);
        section.getStyleClass().add("client-profile-edit-values-section");
        section.getChildren().add(ClienteProfileFormControls.createEditSectionLabel(title));
        addLinkedEditableValues(section, target, values, prompt, options);
        return section;
    }

    private void addLinkedEditableValues(
            VBox container,
            List<ComboBox<String>> target,
            List<ValueEditInput> values,
            String prompt,
            List<String> options) {
        target.clear();
        List<ValueEditInput> safeValues =
                values.isEmpty() ? List.of(new ValueEditInput(null, "")) : values;
        safeValues.forEach(
                value ->
                        addLinkedEditableValueRow(
                                container, target, value.id(), value.value(), prompt, options));
    }

    private void addLinkedEditableValueRow(
            VBox container,
            List<ComboBox<String>> target,
            java.util.UUID id,
            String value,
            String prompt,
            List<String> options) {
        ComboBox<String> field = new ComboBox<>();
        field.setEditable(true);
        field.setPromptText(prompt);
        field.getStyleClass().add("client-profile-linked-combo");
        field.getItems().setAll(options);
        field.getEditor().setText(emptyFallbackForEdit(value));
        field.setUserData(id);
        target.add(field);

        HBox row = new HBox(8);
        row.getStyleClass().add("client-profile-edit-row");
        Button addButton = ClienteProfileFormControls.createSmallButton("+");
        Button removeButton = ClienteProfileFormControls.createSmallButton("-");
        addButton.setOnAction(
                event -> addLinkedEditableValueRow(container, target, null, "", prompt, options));
        removeButton.setOnAction(
                event -> {
                    target.remove(field);
                    container.getChildren().remove(row);
                    if (target.isEmpty()) {
                        addLinkedEditableValueRow(container, target, null, "", prompt, options);
                    }
                });
        HBox.setHgrow(field, Priority.ALWAYS);
        row.getChildren().addAll(field, addButton, removeButton);
        container.getChildren().add(row);
    }

    private void refreshLinkedComboOptions(
            List<ComboBox<String>> comboFields, List<TextField> sourceFields) {
        List<String> options = linkedOptions(sourceFields, linkedValuesForOptions(comboFields));
        comboFields.forEach(field -> field.getItems().setAll(options));
    }

    private List<ValueEditInput> linkedValuesForOptions(List<ComboBox<String>> fields) {
        return fields.stream()
                .map(
                        field ->
                                new ValueEditInput(
                                        (java.util.UUID) field.getUserData(), comboValue(field)))
                .toList();
    }

    private List<String> linkedOptions(
            List<TextField> sourceFields, List<ValueEditInput> selectedValues) {
        List<String> options = new ArrayList<>();
        sourceFields.stream()
                .map(this::valueOf)
                .filter(value -> !value.isBlank())
                .forEach(options::add);
        selectedValues.stream()
                .map(ValueEditInput::value)
                .filter(value -> value != null && !value.isBlank() && !options.contains(value))
                .forEach(options::add);
        return options;
    }

    private List<ValueEditInput> linkedValuesOf(
            List<ComboBox<String>> fields, List<TextField> sourceFields) {
        return fields.stream()
                .map(
                        field ->
                                new ValueEditInput(
                                        idForLinkedValue(field, sourceFields), comboValue(field)))
                .toList();
    }

    private java.util.UUID idForLinkedValue(ComboBox<String> field, List<TextField> sourceFields) {
        String value = comboValue(field);
        if (!value.isBlank()) {
            for (TextField sourceField : sourceFields) {
                if (value.equals(valueOf(sourceField))) {
                    return (java.util.UUID) sourceField.getUserData();
                }
            }
        }
        return (java.util.UUID) field.getUserData();
    }

    private String emptyFallback(String value) {
        return value == null || value.isBlank() ? "Contatto senza nome" : value;
    }

    private String valueOf(TextField field) {
        return field == null ? "" : field.getText();
    }

    private String comboValue(ComboBox<String> comboBox) {
        String editorText = comboBox.getEditor().getText();
        if (editorText != null && !editorText.isBlank()) {
            return editorText.trim();
        }
        String value = comboBox.getValue();
        return value == null ? "" : value.trim();
    }

    private String emptyFallbackForEdit(String value) {
        return value == null ? "" : value;
    }

    private record ContactEditControls(
            java.util.UUID id,
            TextField descriptionField,
            List<ComboBox<String>> phoneFields,
            List<ComboBox<String>> emailFields,
            VBox container) {}
}

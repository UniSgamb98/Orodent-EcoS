package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ClienteProfile;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.EditProfileDraft;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ValueEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ValueItem;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class ClienteDataSection extends VBox {

    private static final double GAP = 12;
    private static final double CUSTOMER_DATA_TILE_WIDTH = 240;
    private static final double CUSTOMER_DATA_TWO_COLUMN_BREAKPOINT =
            CUSTOMER_DATA_TILE_WIDTH * 2 + GAP;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final VBox content;
    private final List<TextField> phoneEditFields = new ArrayList<>();
    private final List<TextField> emailEditFields = new ArrayList<>();
    private final List<TextField> siteEditFields = new ArrayList<>();
    private TextField ragioneSocialeEditField;
    private ChoiceBox<String> tipoClienteEditField;
    private List<String> tipoClienteOptions = List.of();
    private ChoiceBox<String> statoTrattativaEditField;
    private ChoiceBox<Integer> coinvolgimentoEditField;
    private List<String> statoTrattativaOptions = List.of();
    private TextField partitaIvaEditField;
    private TextField codiceFiscaleEditField;
    private DatePicker acquisizioneEditPicker;
    private Runnable linkedOptionsRefresh = () -> {};

    ClienteDataSection() {
        super(12);
        getStyleClass().add("new-client-section");
        Label title = new Label("Azienda");
        title.getStyleClass().add("new-client-section-title");
        content = new VBox(8);
        getChildren().addAll(title, content);
    }

    void render(ClienteProfile profile) {
        content.getChildren().clear();
        VBox commercial =
                new VBox(
                        10,
                        createInfoLabel("Tipo cliente: " + emptyFallback(profile.tipoCliente())),
                        createInfoLabel(
                                "Stato trattativa: " + emptyFallback(profile.statoTrattativa())),
                        createInfoLabel(
                                "Coinvolgimento: " + emptyFallback(profile.coinvolgimento())),
                        createInfoLabel("Partita IVA: " + emptyFallback(profile.partitaIva())),
                        createInfoLabel(
                                "Codice fiscale: " + emptyFallback(profile.codiceFiscale())),
                        createInfoLabel("Acquisizione: " + formatDate(profile.acquisizione())));
        TitledPane disclosure = new TitledPane("Dati fiscali e commerciali", commercial);
        disclosure.setExpanded(false);
        disclosure.setAnimated(false);
        disclosure.getStyleClass().add("client-profile-disclosure");
        content.getChildren()
                .addAll(
                        valueGroup("Telefono aziendale", profile.telefoni()),
                        valueGroup("Email aziendale", profile.email()),
                        valueGroup("Sito web", profile.sitiWeb()),
                        disclosure);
    }

    private VBox valueGroup(String name, List<ValueItem> values) {
        VBox group = new VBox(5);
        Label label = new Label(name);
        label.getStyleClass().add("client-profile-contact-value-title");
        group.getChildren().add(label);
        if (values.isEmpty()) group.getChildren().add(createInfoLabel("Non indicato"));
        else
            values.forEach(
                    value ->
                            group.getChildren()
                                    .add(ClienteProfileFormControls.copyableValue(value.value())));
        return group;
    }

    void renderEditor(EditProfileDraft draft) {
        content.getChildren().clear();
        phoneEditFields.clear();
        emailEditFields.clear();
        siteEditFields.clear();
        ragioneSocialeEditField = createTextField(draft.ragioneSociale(), "Ragione sociale");
        tipoClienteEditField = createChoiceBox(draft.tipoCliente(), tipoClienteOptions);
        statoTrattativaEditField = createChoiceBox(draft.statoTrattativa(), statoTrattativaOptions);
        coinvolgimentoEditField = createIntegerChoiceBox(draft.coinvolgimento());
        partitaIvaEditField = createTextField(draft.partitaIva(), "Partita IVA");
        codiceFiscaleEditField = createTextField(draft.codiceFiscale(), "Codice fiscale");
        acquisizioneEditPicker = new DatePicker(draft.acquisizione());
        acquisizioneEditPicker.getStyleClass().add("client-profile-call-date-picker");

        ResponsiveTilePane grid = createCustomerDataGrid();
        addCustomerDataNode(grid, createFieldRow("Ragione sociale", ragioneSocialeEditField));
        addCustomerDataNode(grid, createChoiceRow("Tipo cliente", tipoClienteEditField));
        addCustomerDataNode(grid, createChoiceRow("Stato trattativa", statoTrattativaEditField));
        addCustomerDataNode(
                grid, createIntegerChoiceRow("Coinvolgimento", coinvolgimentoEditField));
        addCustomerDataNode(grid, createFieldRow("Partita IVA", partitaIvaEditField));
        addCustomerDataNode(grid, createFieldRow("Codice fiscale", codiceFiscaleEditField));
        addCustomerDataNode(grid, createDateRow("Acquisizione", acquisizioneEditPicker));

        content.getChildren()
                .addAll(
                        grid,
                        createEditableValuesSection(
                                "Telefoni azienda",
                                phoneEditFields,
                                draft.telefoni(),
                                "Telefono azienda"),
                        createEditableValuesSection(
                                "Email azienda", emailEditFields, draft.email(), "Email azienda"),
                        createEditableValuesSection(
                                "Siti web", siteEditFields, draft.sitiWeb(), "Sito web"));
    }

    String ragioneSociale() {
        return valueOf(ragioneSocialeEditField);
    }

    String tipoCliente() {
        return choiceValueOf(tipoClienteEditField);
    }

    String statoTrattativa() {
        return choiceValueOf(statoTrattativaEditField);
    }

    Integer coinvolgimento() {
        return coinvolgimentoEditField == null ? null : coinvolgimentoEditField.getValue();
    }

    String partitaIva() {
        return valueOf(partitaIvaEditField);
    }

    String codiceFiscale() {
        return valueOf(codiceFiscaleEditField);
    }

    LocalDate acquisizione() {
        return acquisizioneEditPicker == null ? null : acquisizioneEditPicker.getValue();
    }

    List<ValueEditInput> telefoni() {
        return valuesOf(phoneEditFields);
    }

    List<ValueEditInput> email() {
        return valuesOf(emailEditFields);
    }

    List<ValueEditInput> sitiWeb() {
        return valuesOf(siteEditFields);
    }

    List<TextField> phoneEditFields() {
        return phoneEditFields;
    }

    List<TextField> emailEditFields() {
        return emailEditFields;
    }

    void setTipoClienteOptions(List<String> options) {
        tipoClienteOptions = options == null ? List.of() : List.copyOf(options);
    }

    void setStatoTrattativaOptions(List<String> options) {
        statoTrattativaOptions = options == null ? List.of() : List.copyOf(options);
    }

    void setLinkedOptionsRefresh(Runnable linkedOptionsRefresh) {
        this.linkedOptionsRefresh = linkedOptionsRefresh == null ? () -> {} : linkedOptionsRefresh;
    }

    private ResponsiveTilePane createCustomerDataGrid() {
        ResponsiveTilePane grid = new ResponsiveTilePane(GAP, CUSTOMER_DATA_TWO_COLUMN_BREAKPOINT);
        grid.getStyleClass().add("client-profile-data-grid");
        return grid;
    }

    private void addCustomerDataNode(ResponsiveTilePane grid, Node node) {
        grid.addStretchingTile(node);
    }

    private VBox createEditableValuesSection(
            String title, List<TextField> target, List<ValueEditInput> values, String prompt) {
        VBox section = new VBox(8);
        section.getStyleClass().add("client-profile-edit-values-section");
        section.getChildren().add(createEditSectionLabel(title));
        addEditableValues(section, target, values, prompt);
        return section;
    }

    private void addEditableValues(
            VBox container, List<TextField> target, List<ValueEditInput> values, String prompt) {
        target.clear();
        List<ValueEditInput> safeValues =
                values.isEmpty() ? List.of(new ValueEditInput(null, "")) : values;
        safeValues.forEach(
                value -> addEditableValueRow(container, target, value.id(), value.value(), prompt));
    }

    private void addEditableValueRow(
            VBox container,
            List<TextField> target,
            java.util.UUID id,
            String value,
            String prompt) {
        TextField field = createTextField(value, prompt);
        field.setUserData(id);
        target.add(field);
        field.textProperty()
                .addListener((observable, oldValue, newValue) -> linkedOptionsRefresh.run());
        HBox row = new HBox(8);
        row.getStyleClass().add("client-profile-edit-row");
        Button addButton = createSmallButton("+");
        Button removeButton = createSmallButton("-");
        addButton.setOnAction(
                event -> {
                    addEditableValueRow(container, target, null, "", prompt);
                    linkedOptionsRefresh.run();
                });
        removeButton.setOnAction(
                event -> {
                    target.remove(field);
                    container.getChildren().remove(row);
                    if (target.isEmpty()) {
                        addEditableValueRow(container, target, null, "", prompt);
                    }
                    linkedOptionsRefresh.run();
                });
        HBox.setHgrow(field, Priority.ALWAYS);
        row.getChildren().addAll(field, addButton, removeButton);
        container.getChildren().add(row);
    }

    private HBox createIntegerChoiceRow(String labelText, ChoiceBox<Integer> field) {
        HBox row = createEditRow(labelText, field);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private HBox createChoiceRow(String labelText, ChoiceBox<String> field) {
        HBox row = createEditRow(labelText, field);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private HBox createFieldRow(String labelText, TextField field) {
        HBox row = createEditRow(labelText, field);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private HBox createDateRow(String labelText, DatePicker picker) {
        HBox row = createEditRow(labelText, picker);
        HBox.setHgrow(picker, Priority.ALWAYS);
        return row;
    }

    private HBox createEditRow(String labelText, Region field) {
        return ClienteProfileFormControls.createFieldRow(labelText, field);
    }

    private ChoiceBox<Integer> createIntegerChoiceBox(Integer selectedValue) {
        ChoiceBox<Integer> choiceBox = new ChoiceBox<>();
        choiceBox.getStyleClass().add("client-profile-edit-choice-box");
        choiceBox.setMaxWidth(Double.MAX_VALUE);
        choiceBox.getItems().setAll(1, 2, 3, 4, 5);
        if (selectedValue != null && selectedValue >= 1 && selectedValue <= 5) {
            choiceBox.setValue(selectedValue);
        }
        return choiceBox;
    }

    private ChoiceBox<String> createChoiceBox(String selectedValue, List<String> sourceOptions) {
        ChoiceBox<String> choiceBox = new ChoiceBox<>();
        choiceBox.getStyleClass().add("client-profile-edit-choice-box");
        choiceBox.setMaxWidth(Double.MAX_VALUE);
        List<String> options = new ArrayList<>(sourceOptions);
        String cleanSelectedValue = emptyFallbackForEdit(selectedValue);
        if (!cleanSelectedValue.isBlank() && !options.contains(cleanSelectedValue)) {
            options.add(0, cleanSelectedValue);
        }
        choiceBox.getItems().setAll(options);
        if (!cleanSelectedValue.isBlank()) {
            choiceBox.setValue(cleanSelectedValue);
        }
        return choiceBox;
    }

    private TextField createTextField(String value, String prompt) {
        return ClienteProfileFormControls.createTextField(value, prompt);
    }

    private Label createInfoLabel(String text) {
        return ClienteProfileFormControls.createInfoLabel(text);
    }

    private Label createEditSectionLabel(String text) {
        return ClienteProfileFormControls.createEditSectionLabel(text);
    }

    private List<ValueEditInput> valuesOf(List<TextField> fields) {
        return fields.stream()
                .map(
                        field ->
                                new ValueEditInput(
                                        (java.util.UUID) field.getUserData(), field.getText()))
                .toList();
    }

    private String valueOf(TextField field) {
        return field == null ? "" : field.getText();
    }

    private String choiceValueOf(ChoiceBox<String> choiceBox) {
        String value = choiceBox == null ? null : choiceBox.getValue();
        return value == null ? "" : value;
    }

    private String joinProfileValues(List<ValueItem> values) {
        return values.isEmpty()
                ? "-"
                : String.join(", ", values.stream().map(ValueItem::value).toList());
    }

    private String formatDate(LocalDate date) {
        return date == null ? "-" : DATE_FORMATTER.format(date);
    }

    private String emptyFallback(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String emptyFallback(Integer value) {
        return value == null ? "-" : value.toString();
    }

    private String emptyFallbackForEdit(String value) {
        return value == null ? "" : value;
    }

    private Button createSmallButton(String text) {
        return ClienteProfileFormControls.createSmallButton(text);
    }
}

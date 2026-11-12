package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.AddressEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.AddressItem;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

final class ClienteAddressesSection extends VBox {

    private final List<AddressEditControls> addressEditControls = new ArrayList<>();

    ClienteAddressesSection() {
        super(8);
    }

    void render(List<AddressItem> values) {
        getChildren().clear();
        if (values.isEmpty()) {
            getChildren()
                    .add(ClienteProfileFormControls.createInfoLabel("Nessun dato disponibile"));
            return;
        }
        values.stream()
                .map(this::formatAddress)
                .forEach(
                        value ->
                                getChildren()
                                        .add(ClienteProfileFormControls.createInfoLabel(value)));
    }

    void renderEditor(List<AddressEditInput> values) {
        getChildren().clear();
        addressEditControls.clear();
        List<AddressEditInput> safeValues =
                values.isEmpty() ? List.of(emptyAddressInput()) : values;
        safeValues.forEach(this::addAddressEditor);
    }

    List<AddressEditInput> collectAddresses() {
        return addressEditControls.stream()
                .map(
                        control ->
                                new AddressEditInput(
                                        control.id(),
                                        valueOf(control.countryField()),
                                        valueOf(control.regionField()),
                                        valueOf(control.provinceField()),
                                        valueOf(control.cityField()),
                                        valueOf(control.addressField()),
                                        valueOf(control.streetNumberField()),
                                        valueOf(control.zipField()),
                                        control.primaryCheck().isSelected()))
                .toList();
    }

    private void addAddressEditor(AddressEditInput value) {
        VBox card = new VBox(8);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("client-profile-timeline-card");
        TextField countryField = ClienteProfileFormControls.createTextField(value.paese(), "Paese");
        TextField regionField =
                ClienteProfileFormControls.createTextField(value.regione(), "Regione");
        TextField provinceField =
                ClienteProfileFormControls.createTextField(value.provincia(), "Provincia");
        TextField cityField = ClienteProfileFormControls.createTextField(value.citta(), "Città");
        TextField addressField =
                ClienteProfileFormControls.createTextField(value.indirizzo(), "Indirizzo");
        TextField streetNumberField =
                ClienteProfileFormControls.createTextField(value.numeroCivico(), "Numero civico");
        TextField zipField = ClienteProfileFormControls.createTextField(value.cap(), "CAP");
        CheckBox primaryCheck = new CheckBox("Indirizzo principale");
        primaryCheck.getStyleClass().add("client-profile-primary-check");
        primaryCheck.setSelected(value.principale());
        primaryCheck
                .selectedProperty()
                .addListener(
                        (observable, oldValue, newValue) -> {
                            if (newValue) {
                                unsetOtherPrimaryChecks(primaryCheck);
                            }
                        });

        HBox actions = new HBox(8);
        Button addButton = ClienteProfileFormControls.createSmallButton("+");
        Button removeButton = ClienteProfileFormControls.createSmallButton("-");
        addButton.setOnAction(event -> addAddressEditor(emptyAddressInput()));
        removeButton.setOnAction(
                event -> {
                    getChildren().remove(card);
                    addressEditControls.removeIf(control -> control.container() == card);
                    if (addressEditControls.isEmpty()) {
                        addAddressEditor(emptyAddressInput());
                    }
                });
        actions.getChildren()
                .add(
                        ClienteProfileFormControls.rowMenu(
                                addButton,
                                removeButton,
                                "Aggiungi indirizzo",
                                "Elimina indirizzo"));

        card.getChildren()
                .addAll(
                        ClienteProfileFormControls.createFieldRow("Paese", countryField),
                        ClienteProfileFormControls.createFieldRow("Regione", regionField),
                        ClienteProfileFormControls.createFieldRow("Provincia", provinceField),
                        ClienteProfileFormControls.createFieldRow("Città", cityField),
                        ClienteProfileFormControls.createFieldRow("Indirizzo", addressField),
                        ClienteProfileFormControls.createFieldRow(
                                "Numero civico", streetNumberField),
                        ClienteProfileFormControls.createFieldRow("CAP", zipField),
                        primaryCheck,
                        actions);
        getChildren().add(card);
        addressEditControls.add(
                new AddressEditControls(
                        value.id(),
                        countryField,
                        regionField,
                        provinceField,
                        cityField,
                        addressField,
                        streetNumberField,
                        zipField,
                        primaryCheck,
                        card));
        if (primaryCheck.isSelected()) {
            unsetOtherPrimaryChecks(primaryCheck);
        }
    }

    private void unsetOtherPrimaryChecks(CheckBox selectedCheck) {
        addressEditControls.stream()
                .map(AddressEditControls::primaryCheck)
                .filter(checkBox -> checkBox != selectedCheck)
                .forEach(checkBox -> checkBox.setSelected(false));
    }

    private AddressEditInput emptyAddressInput() {
        return new AddressEditInput(null, "", "", "", "", "", "", "", false);
    }

    private String formatAddress(AddressItem address) {
        return joinNonBlank(
                address.indirizzo(),
                address.numeroCivico(),
                address.cap(),
                address.citta(),
                address.provincia(),
                address.regione(),
                address.paese());
    }

    private String joinNonBlank(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.trim());
            }
        }
        return String.join(" · ", parts);
    }

    private String valueOf(TextField field) {
        return field == null ? "" : field.getText();
    }

    private record AddressEditControls(
            java.util.UUID id,
            TextField countryField,
            TextField regionField,
            TextField provinceField,
            TextField cityField,
            TextField addressField,
            TextField streetNumberField,
            TextField zipField,
            CheckBox primaryCheck,
            VBox container) {}
}

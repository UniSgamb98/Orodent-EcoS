package com.orodent.ecos.crm.feature.clienti.nuovocliente.controller;

import com.orodent.ecos.crm.feature.clienti.navigator.ClientiNav;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.ClienteInput;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.ContattoClienteInput;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.EmailClienteInput;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.IndirizzoClienteInput;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.NuovoClienteRequest;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.SitoWebClienteInput;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.dto.TelefonoClienteInput;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.service.NuovoClienteService;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.view.NuovoClienteView;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.view.NuovoClienteView.ContactEntryControls;

import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class NuovoClienteController {

    private final NuovoClienteView view;
    private final ClientiNav clientiNav;
    private final NuovoClienteService service;
    private final com.orodent.ecos.crm.core.async.AsyncLoader tasks;

    public NuovoClienteController(
            NuovoClienteView view,
            ClientiNav clientiNav,
            NuovoClienteService service,
            com.orodent.ecos.crm.core.async.AsyncLoader tasks) {
        this.view = view;
        this.clientiNav = clientiNav;
        this.service = service;
        this.tasks = tasks;
        view.getNameField().setId("crm-lead-name");
        view.getOperatorField().setEditable(false);
        configureActions();
    }

    private void configureActions() {
        tasks.run(
                () -> java.util.List.of(service.getTipiCliente(), service.getStatiTrattativa()),
                lists -> {
                    view.setTipoClienteOptions(lists.get(0));
                    view.setStatoTrattativaOptions(lists.get(1));
                },
                error ->
                        showError("Caricamento opzioni non riuscito", new RuntimeException(error)));
        view.getCancelButton().setOnAction(event -> clientiNav.showClienti());
        view.getSaveButton().setId("crm-save-lead");
        view.getSaveButton()
                .setOnAction(
                        event -> {
                            try {
                                NuovoClienteRequest request = createRequest();
                                tasks.run(
                                        () -> service.saveCliente(request),
                                        draft -> {
                                            tasks.markClean();
                                            clientiNav.showSchedaCliente(draft.cliente().id());
                                        },
                                        error ->
                                                showError(
                                                        "Salvataggio cliente non riuscito",
                                                        new RuntimeException(error)));
                            } catch (RuntimeException error) {
                                showError("Controlla i dati", error);
                            }
                        });
        view.getAddWebsiteButton().setOnAction(event -> view.addWebsiteField().requestFocus());
        view.getAddAddressButton().setOnAction(event -> view.addAddressField().requestFocus());
        view.getAddContactButton()
                .setOnAction(
                        event -> {
                            ContactEntryControls controls =
                                    view.addContactEntry(
                                            nonBlankValues(view.getPhoneFields()),
                                            nonBlankValues(view.getEmailFields()));
                            configureContactRemoval(controls);
                            controls.contactField().requestFocus();
                        });
        view.getAddEmailButton()
                .setOnAction(
                        event -> {
                            TextField field = view.addEmailField();
                            configureContactOptionsRefresh(field);
                            refreshContactOptions();
                            field.requestFocus();
                        });
        view.getAddPhoneButton()
                .setOnAction(
                        event -> {
                            TextField field = view.addPhoneField();
                            configureContactOptionsRefresh(field);
                            refreshContactOptions();
                            field.requestFocus();
                        });
        view.getEmailFields().forEach(this::configureContactOptionsRefresh);
        view.getPhoneFields().forEach(this::configureContactOptionsRefresh);
        view.getContactEntries().forEach(this::configureContactRemoval);
        refreshContactOptions();
    }

    private void configureContactRemoval(ContactEntryControls controls) {
        controls.removeButton().setOnAction(event -> view.removeContactEntry(controls));
    }

    private void configureContactOptionsRefresh(TextField field) {
        field.textProperty()
                .addListener((observable, oldValue, newValue) -> refreshContactOptions());
    }

    private void refreshContactOptions() {
        view.setContactPhoneOptions(nonBlankValues(view.getPhoneFields()));
        view.setContactEmailOptions(nonBlankValues(view.getEmailFields()));
    }

    private NuovoClienteRequest createRequest() {
        return new NuovoClienteRequest(
                new ClienteInput(
                        text(view.getNameField()),
                        choiceText(view.getTypeChoiceBox()),
                        choiceText(view.getStatusChoiceBox()),
                        view.getInvolvementChoiceBox().getValue(),
                        text(view.getVatField()),
                        text(view.getFiscalCodeField()),
                        parseDate(text(view.getAcquisitionField())),
                        text(view.getOperatorField())),
                indirizzi(),
                telefoni(view.getPhoneFields()),
                email(view.getEmailFields()),
                sitiWeb(),
                contactInputs());
    }

    private List<IndirizzoClienteInput> indirizzi() {
        List<IndirizzoClienteInput> indirizzi = new ArrayList<>();
        indirizzi.add(
                new IndirizzoClienteInput(
                        text(view.getCountryField()),
                        text(view.getRegionField()),
                        text(view.getProvinceField()),
                        text(view.getCityField()),
                        text(view.getAddressField()),
                        text(view.getStreetNumberField()),
                        text(view.getZipField()),
                        true));
        view.getExtraAddressFields().stream()
                .map(NuovoClienteController::text)
                .map(value -> new IndirizzoClienteInput("", "", "", "", value, "", "", false))
                .forEach(indirizzi::add);
        return indirizzi;
    }

    private List<SitoWebClienteInput> sitiWeb() {
        return values(view.getWebsiteFields()).stream().map(SitoWebClienteInput::new).toList();
    }

    private static List<TelefonoClienteInput> telefoni(List<TextField> fields) {
        return values(fields).stream().map(TelefonoClienteInput::new).toList();
    }

    private static List<EmailClienteInput> email(List<TextField> fields) {
        return values(fields).stream().map(EmailClienteInput::new).toList();
    }

    private List<ContattoClienteInput> contactInputs() {
        List<ContattoClienteInput> inputs = new ArrayList<>();
        for (int index = 0; index < view.getContactFields().size(); index++) {
            String telefono = comboText(view.getContactPhoneFields().get(index));
            String email = comboText(view.getContactEmailFields().get(index));
            inputs.add(
                    new ContattoClienteInput(
                            text(view.getContactFields().get(index)),
                            telefono.isBlank()
                                    ? List.of()
                                    : List.of(new TelefonoClienteInput(telefono)),
                            email.isBlank() ? List.of() : List.of(new EmailClienteInput(email))));
        }
        return inputs;
    }

    private static List<String> values(List<TextField> fields) {
        return fields.stream().map(NuovoClienteController::text).toList();
    }

    private static List<String> nonBlankValues(List<TextField> fields) {
        return fields.stream()
                .map(NuovoClienteController::text)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static LocalDate parseDate(String value) {
        if (value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private static String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private static String choiceText(ChoiceBox<String> choiceBox) {
        String value = choiceBox.getValue();
        return value == null ? "" : value.trim();
    }

    private static String comboText(ComboBox<String> comboBox) {
        String editorText = comboBox.getEditor().getText();
        if (editorText != null && !editorText.isBlank()) {
            return editorText.trim();
        }
        String value = comboBox.getValue();
        return value == null ? "" : value.trim();
    }

    private void showError(String title, RuntimeException e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(com.orodent.ecos.crm.core.async.CrmErrors.message(e));
        alert.showAndWait();
    }

    public NuovoClienteView getView() {
        return view;
    }

    public ClientiNav getClientiNav() {
        return clientiNav;
    }

    public NuovoClienteService getService() {
        return service;
    }
}

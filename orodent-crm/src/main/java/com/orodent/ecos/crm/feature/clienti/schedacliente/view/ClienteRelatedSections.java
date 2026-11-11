package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.AddressEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.AddressItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ContactEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ContactItem;

import javafx.scene.control.TextField;

import java.util.List;

final class ClienteRelatedSections extends ResponsiveTilePane {

    private final ClienteContactsSection contactsSection;
    private final ClienteAddressesSection addressesSection;

    ClienteRelatedSections(double gap, double twoColumnBreakpoint) {
        super(gap, twoColumnBreakpoint);
        getStyleClass().add("client-profile-related-sections-grid");
        contactsSection = new ClienteContactsSection();
        addressesSection = new ClienteAddressesSection();
        addStretchingTile(ClienteProfileFormControls.createSection("Contatti", contactsSection));
        addStretchingTile(ClienteProfileFormControls.createSection("Indirizzi", addressesSection));
    }

    void setCompanyValueSources(List<TextField> phoneFields, List<TextField> emailFields) {
        contactsSection.setCompanyValueSources(phoneFields, emailFields);
    }

    void renderContacts(List<ContactItem> values) {
        contactsSection.render(values);
    }

    void renderAddresses(List<AddressItem> values) {
        addressesSection.render(values);
    }

    void renderContactsEditor(List<ContactEditInput> values) {
        contactsSection.renderEditor(values);
    }

    void renderAddressesEditor(List<AddressEditInput> values) {
        addressesSection.renderEditor(values);
    }

    List<ContactEditInput> collectContacts() {
        return contactsSection.collectContacts();
    }

    List<AddressEditInput> collectAddresses() {
        return addressesSection.collectAddresses();
    }

    void refreshLinkedContactOptions() {
        contactsSection.refreshLinkedContactOptions();
    }
}

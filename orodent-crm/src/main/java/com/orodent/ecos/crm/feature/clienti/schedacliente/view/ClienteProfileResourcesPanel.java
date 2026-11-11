package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeClienteItem;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.List;

/** Full-width panel reserved for the customer's production resources. */
final class ClienteProfileResourcesPanel extends VBox {

    private final FlowPane resources = new FlowPane(12, 12);
    private final ClienteForniResourceSection forniSection = new ClienteForniResourceSection();
    private final ClienteFresatoriResourceSection fresatoriSection =
            new ClienteFresatoriResourceSection();
    private final ClienteMaterialiResourceSection materialiSection =
            new ClienteMaterialiResourceSection();

    ClienteProfileResourcesPanel() {
        super(12);
        getStyleClass().add("client-profile-resources-panel");

        Label title = new Label("Risorse cliente");
        title.getStyleClass().add("client-profile-resources-title");

        resources.getStyleClass().add("client-profile-resources-flow");
        resources
                .getChildren()
                .setAll(forniSection.root(), fresatoriSection.root(), materialiSection.root());

        getChildren().addAll(title, resources);
    }

    void setForniCatalog(List<FornoCatalogItem> values) {
        forniSection.setCatalog(values);
    }

    void setFresatoriCatalog(List<FresatoreCatalogItem> values) {
        fresatoriSection.setCatalog(values);
    }

    void setMaterialiCatalog(List<MaterialeCatalogItem> values) {
        materialiSection.setCatalog(values);
    }

    void renderForni(List<FornoClienteItem> forni) {
        forniSection.render(forni);
    }

    void renderStandaloneForniEditor(List<FornoClienteEditInput> forni) {
        forniSection.renderEditor(forni);
    }

    List<FornoClienteEditInput> collectForni() {
        return forniSection.collect();
    }

    void renderFresatori(List<FresatoreClienteItem> fresatori) {
        fresatoriSection.render(fresatori);
    }

    void renderStandaloneFresatoriEditor(List<FresatoreClienteEditInput> fresatori) {
        fresatoriSection.renderEditor(fresatori);
    }

    List<FresatoreClienteEditInput> collectFresatori() {
        return fresatoriSection.collect();
    }

    void renderMateriali(List<MaterialeClienteItem> materiali) {
        materialiSection.render(materiali);
    }

    void renderStandaloneMaterialiEditor(List<MaterialeClienteEditInput> materiali) {
        materialiSection.renderEditor(materiali);
    }

    List<MaterialeClienteEditInput> collectMateriali() {
        return materialiSection.collect();
    }

    Button editForniButton() {
        return forniSection.editButton();
    }

    Button saveForniButton() {
        return forniSection.saveButton();
    }

    Button cancelForniButton() {
        return forniSection.cancelButton();
    }

    Button editFresatoriButton() {
        return fresatoriSection.editButton();
    }

    Button saveFresatoriButton() {
        return fresatoriSection.saveButton();
    }

    Button cancelFresatoriButton() {
        return fresatoriSection.cancelButton();
    }

    Button editMaterialiButton() {
        return materialiSection.editButton();
    }

    Button saveMaterialiButton() {
        return materialiSection.saveButton();
    }

    Button cancelMaterialiButton() {
        return materialiSection.cancelButton();
    }
}

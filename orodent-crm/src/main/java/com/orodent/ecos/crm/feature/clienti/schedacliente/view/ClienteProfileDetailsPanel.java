package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import javafx.scene.Node;
import javafx.scene.layout.VBox;

/** Groups client data, contacts and addresses into one profile card. */
final class ClienteProfileDetailsPanel extends VBox {

    ClienteProfileDetailsPanel(
            ClienteDataSection dataSection, ClienteRelatedSections relatedSections) {
        super(14);
        getStyleClass().add("client-profile-details-panel");
        dataSection.getStyleClass().remove("new-client-section");
        for (Node section : relatedSections.getChildren()) {
            section.getStyleClass().remove("new-client-section");
        }
        getChildren().addAll(relatedSections, dataSection);
    }
}

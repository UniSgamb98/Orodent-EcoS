package com.orodent.ecos.crm.feature.clienti.clienti.view;

import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientePreview;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ClientePreviewDetailPanel extends VBox {

    private final Button companyNameButton;
    private final Label contactLabel;
    private final Label addressLabel;
    private final Label operatorLabel;

    public ClientePreviewDetailPanel(Runnable onClose) {
        super(16);
        getStyleClass().add("clients-detail-panel");
        setPadding(new Insets(18));
        setPrefWidth(280);
        setMinWidth(260);

        companyNameButton = new Button();
        companyNameButton.getStyleClass().add("clients-detail-company-button");
        companyNameButton.setWrapText(true);
        companyNameButton.setMaxWidth(Double.MAX_VALUE);

        Button closeButton = new Button("×");
        closeButton.getStyleClass().add("clients-detail-close-button");
        closeButton.setOnAction(event -> onClose.run());

        HBox header = new HBox(12, companyNameButton, closeButton);
        header.getStyleClass().add("clients-detail-header");
        header.setAlignment(Pos.TOP_LEFT);
        header.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(companyNameButton, Priority.ALWAYS);

        contactLabel = createDetailValue("Referente");
        addressLabel = createDetailValue("Indirizzo");
        operatorLabel = createDetailValue("Operatore assegnato");

        getChildren()
                .addAll(
                        header,
                        createDetailSection("Contatti", contactLabel),
                        createDetailSection("Indirizzo", addressLabel),
                        createDetailSection("Assegnazione", operatorLabel));
    }

    public void showCliente(ClientePreview preview, Runnable onOpenProfile) {
        companyNameButton.setText("↗  " + valueOrDash(preview.name()));
        companyNameButton.setOnAction(event -> onOpenProfile.run());
        contactLabel.setText(valueOrDash(preview.contact()));
        addressLabel.setText(valueOrDash(preview.address()));
        operatorLabel.setText(valueOrDash(preview.operator()));
    }

    private VBox createDetailSection(String title, Label value) {
        VBox section = new VBox(5);
        section.getStyleClass().add("clients-detail-section");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("clients-detail-section-title");
        section.getChildren().addAll(titleLabel, value);
        return section;
    }

    private Label createDetailValue(String accessibleText) {
        Label value = new Label();
        value.setAccessibleText(accessibleText);
        value.setWrapText(true);
        value.getStyleClass().add("clients-detail-value");
        return value;
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}

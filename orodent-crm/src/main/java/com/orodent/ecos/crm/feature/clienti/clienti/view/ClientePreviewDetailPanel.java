package com.orodent.ecos.crm.feature.clienti.clienti.view;

import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientePreview;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ClientePreviewDetailPanel extends BorderPane {
    private final HBox header;
    private final VBox heading = new VBox(10);
    private final VBox fields = new VBox(10);
    private final Button company = new Button();
    private final Button open = new Button("Apri scheda →");

    public ClientePreviewDetailPanel(Runnable close) {
        setMinWidth(0);
        setMinHeight(0);
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("clients-detail-panel");
        Label title = new Label("Anteprima anagrafica");
        title.getStyleClass().add("clients-detail-eyebrow");
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        Button dismiss = new Button("×");
        dismiss.getStyleClass().add("clients-detail-close-button");
        dismiss.setTooltip(new Tooltip("Chiudi anteprima"));
        dismiss.setAccessibleText("Chiudi anteprima");
        dismiss.setOnAction(e -> close.run());
        header = new HBox(6, title, space, dismiss);
        header.setAlignment(Pos.CENTER_LEFT);
        company.setWrapText(true);
        company.setMaxWidth(Double.MAX_VALUE);
        company.getStyleClass().add("clients-detail-company-button");
        open.setId("crm-preview-open");
        open.setMaxWidth(Double.MAX_VALUE);
        open.getStyleClass().add("clients-primary-button");
        var scroll = new ScrollPane(fields);
        scroll.setMinHeight(0);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("clients-detail-fields-scroll");
        setTop(heading);
        setCenter(scroll);
        setBottom(open);
        BorderPane.setMargin(scroll, new Insets(12, 0, 0, 0));
        BorderPane.setMargin(open, new Insets(12, 0, 0, 0));
    }

    public void showCliente(ClientePreview p, Runnable onOpen) {
        company.setText(p.name());
        company.setOnAction(e -> onOpen.run());
        open.setOnAction(e -> onOpen.run());
        heading.getChildren().setAll(header, company);
        fields.getChildren().clear();
        if (p.type() != null && !p.type().isBlank()) {
            Label type = new Label(p.type());
            type.getStyleClass().add("clients-detail-value");
            heading.getChildren().add(type);
        }
        heading.getChildren().add(ClientiPresentation.badge(p.status()));
        addSection("Referente", p.contact(), false);
        addSection("Telefono", p.phone(), true);
        addSection("Email", p.email(), true);
        addSection("Indirizzo", p.address(), false);
        addSection("Paese", p.country(), false);
        addSection("Operatore assegnato", p.operator(), false);
        addSection("Ultimo contatto", p.lastContact(), false);
        var recall =
                addSection(
                        ClientiPresentation.overdue(p.nextContact())
                                ? "Richiamo scaduto"
                                : "Prossimo richiamo",
                        p.nextContact(),
                        false);
        if (recall != null && ClientiPresentation.overdue(p.nextContact()))
            recall.getStyleClass().add("clients-recall-overdue");
    }

    private VBox addSection(String title, String value, boolean copy) {
        if (value == null || value.isBlank() || value.equals("—")) return null;
        Label label = new Label(title);
        label.getStyleClass().add("clients-detail-section-title");
        Label text = new Label(value);
        text.setWrapText(true);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        text.getStyleClass().add("clients-detail-value");
        HBox line = new HBox(8, text);
        line.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        if (copy) {
            Button b = new Button("Copia");
            b.getStyleClass().add("clients-copy-button");
            b.setAccessibleText("Copia " + title);
            b.setOnAction(
                    e -> {
                        var data = new javafx.scene.input.ClipboardContent();
                        data.putString(value);
                        javafx.scene.input.Clipboard.getSystemClipboard().setContent(data);
                    });
            line.getChildren().add(b);
        }
        VBox section = new VBox(5, label, line);
        section.getStyleClass().add("clients-detail-section");
        fields.getChildren().add(section);
        return section;
    }
}

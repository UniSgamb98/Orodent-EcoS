package com.orodent.ecos.ton.features.catalog.view;

import static com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

public final class CatalogForm extends VBox {
    private final CatalogKind kind;
    private final TextField code, name, technology, diameter, height, derived;
    private final TextArea notes = new TextArea();
    private final ComboBox<String> material = new ComboBox<>(), color = new ComboBox<>();
    private final CheckBox active = new CheckBox("Anagrafica attiva");
    private final CatalogLookup parent;
    private final Label structural = CatalogUi.hint(""), legacy = CatalogUi.hint("");
    private final Label generatedDescription = CatalogUi.hint("");
    private Product originalProduct;
    private boolean showing;

    public CatalogForm(CatalogKind kind) {
        super(16);
        this.kind = kind;
        getStyleClass().add("ton-catalogue-tab");
        setMinWidth(0);
        String base = "ton-" + kind.id();
        code = CatalogUi.field(base + "-code", "Codice univoco");
        name =
                CatalogUi.field(
                        base + "-" + (kind == CatalogKind.LINES ? "name" : "description"),
                        kind == CatalogKind.LINES ? "Nome della linea" : "Descrizione");
        technology = CatalogUi.field(base + "-technology", "Tecnologia del laboratorio");
        diameter = CatalogUi.field(base + "-diameter", "Diametro in mm");
        height = CatalogUi.field(base + "-height", "Altezza in mm");
        derived = CatalogUi.field(base + "-inherited", "Derivano dal prodotto");
        derived.setEditable(false);
        material.setId(base + "-material");
        material.setEditable(true);
        material.getItems().setAll("ZR", "ZS");
        material.setMaxWidth(Double.MAX_VALUE);
        material.getEditor().setPromptText("Facoltativa");
        color.setId(base + "-color");
        color.setEditable(true);
        color.setMaxWidth(Double.MAX_VALUE);
        color.getItems()
                .setAll(
                        "Senza colore",
                        "A0",
                        "A1",
                        "A2",
                        "A3",
                        "A3.5",
                        "A4",
                        "B1",
                        "B2",
                        "B3",
                        "C1",
                        "C2",
                        "C3",
                        "D2",
                        "D3");
        color.getEditor().setPromptText("Scegli o digita il colore commerciale");
        parent =
                kind.parent() == null
                        ? null
                        : new CatalogLookup(kind.parent(), base + "-parent", false);
        if (parent != null)
            parent.choices()
                    .valueProperty()
                    .addListener(
                            (o, a, b) -> {
                                derived.setText(
                                        b == null
                                                ? ""
                                                : text(b.lineCode())
                                                        + " · "
                                                        + colorLabelOf(b.color()));
                                productPreview();
                            });
        color.getEditor().textProperty().addListener((o, a, b) -> productPreview());
        generatedDescription.setId("ton-product-generated-description");
        code.setEditable(kind != CatalogKind.PRODUCTS);
        var fields = new ArrayList<javafx.scene.Node>();
        if (kind != CatalogKind.PRODUCTS) {
            fields.add(CatalogUi.field("Codice *", code));
            fields.add(CatalogUi.field(kind == CatalogKind.LINES ? "Nome *" : "Descrizione", name));
        }
        switch (kind) {
            case LINES -> {
                fields.add(CatalogUi.field("Tecnologia", technology));
                fields.add(CatalogUi.field("Materia prima", material));
            }
            case PRODUCTS -> {
                fields.add(CatalogUi.field("Linea *", parent));
                fields.add(CatalogUi.field("Colore commerciale *", color));
                fields.add(CatalogUi.field("Codice prodotto · automatico", code));
                fields.add(CatalogUi.field("Descrizione · automatica", generatedDescription));
            }
            case ARTICLES -> {
                fields.add(CatalogUi.field("Prodotto *", parent));
                fields.add(CatalogUi.field("Linea e colore ereditati", derived));
                fields.add(CatalogUi.field("Diametro · mm *", diameter));
                fields.add(CatalogUi.field("Altezza · mm *", height));
            }
        }
        notes.setId(base + "-notes");
        notes.setWrapText(true);
        notes.setPrefRowCount(3);
        active.setId(base + "-active");
        structural.getStyleClass().add("ton-catalogue-note");
        legacy.getStyleClass().add("ton-catalogue-note");
        getChildren()
                .setAll(
                        CatalogUi.fields(fields.toArray(javafx.scene.Node[]::new)),
                        CatalogUi.field("Note", notes),
                        structural,
                        legacy,
                        active);
    }

    public void show(Detail detail, Choice context) {
        showing = true;
        Entry entry = detail == null ? null : detail.entry();
        originalProduct = entry instanceof Product p ? p : null;
        boolean used = entry != null && entry.used();
        code.setText(entry == null ? "" : entry.code());
        name.setText(entry == null ? "" : entry.name());
        notes.setText(entry == null ? "" : entry.meta().notes());
        active.setSelected(entry == null || entry.active());
        technology.setText(entry instanceof Line l ? l.technology() : "");
        material.setValue(entry instanceof Line l ? l.material() : "");
        material.getEditor().setText(material.getValue());
        technology.setDisable(used && entry instanceof Line l && !l.technology().isBlank());
        material.setDisable(used && entry instanceof Line l && !l.material().isBlank());
        if (parent != null) {
            parent.reset();
            parent.select(
                    switch (entry) {
                        case Product p -> p.line();
                        case Article a -> a.product();
                        case null -> context;
                        default -> null;
                    });
            parent.setDisable(entry instanceof Product p ? used && p.line() != null : used);
        }
        String selected = entry instanceof Product p ? p.color() : null;
        color.setValue(
                selected == null
                        ? ""
                        : "SENZA_COLORE".equals(selected) ? "Senza colore" : selected);
        color.getEditor().setText(color.getValue());
        color.setDisable(used && selected != null);
        diameter.setText(entry instanceof Article a ? CatalogUi.number(a.diameter()) : "");
        height.setText(entry instanceof Article a ? CatalogUi.number(a.height()) : "");
        diameter.setDisable(used);
        height.setDisable(used);
        structural.setText(
                used
                        ? switch (kind) {
                            case LINES ->
                                    "La tecnologia è già utilizzata. Per cambiarne il significato"
                                        + " crea una nuova linea; puoi correggere codice, nome e"
                                        + " note.";
                            case PRODUCTS ->
                                    "Il prodotto è già utilizzato: linea e colore già definiti ne"
                                            + " identificano il gruppo. Puoi completare i valori"
                                            + " mancanti e modificare le note.";
                            case ARTICLES ->
                                    "L'articolo è già configurato. Per cambiare prodotto o formato"
                                            + " crea un nuovo articolo. Codice, descrizione e note"
                                            + " restano modificabili con storico.";
                        }
                        : "");
        CatalogUi.visible(structural, used);
        legacy.setText(
                detail != null && !detail.previousLines().isEmpty()
                        ? "Associazioni precedenti: "
                                + String.join(
                                        ", ",
                                        detail.previousLines().stream().map(Choice::code).toList())
                                + ". Verifica e completa linea e colore; i collegamenti precedenti"
                                + " restano conservati."
                        : "");
        CatalogUi.visible(legacy, !legacy.getText().isEmpty());
        showing = false;
        productPreview();
    }

    private void productPreview() {
        if (kind != CatalogKind.PRODUCTS || showing) return;
        Choice line = parent.value();
        generatedDescription.getStyleClass().remove("ton-error-feedback");
        try {
            String selectedColor = commercialColor(color.getEditor().getText());
            if (line == null || selectedColor == null) {
                code.clear();
                name.clear();
                generatedDescription.setText("Seleziona linea e colore.");
                return;
            }
            if (originalProduct != null
                    && originalProduct.line() != null
                    && originalProduct.line().id() == line.id()
                    && Objects.equals(originalProduct.color(), selectedColor)) {
                code.setText(originalProduct.code());
                name.setText(originalProduct.name());
                generatedDescription.setText(originalProduct.name());
                return;
            }
            code.setText(productCode(line.code(), selectedColor));
            name.setText(productDescription(line.name(), selectedColor));
            generatedDescription.setText(name.getText());
        } catch (IllegalArgumentException invalid) {
            code.clear();
            name.clear();
            generatedDescription.setText(invalid.getMessage());
            generatedDescription.getStyleClass().add("ton-error-feedback");
        }
    }

    public Draft draft() {
        return switch (kind) {
            case LINES ->
                    new LineDraft(
                            code.getText(),
                            name.getText(),
                            technology.getText(),
                            material.getEditor().getText(),
                            notes.getText(),
                            active.isSelected());
            case PRODUCTS ->
                    ProductDraft.configured(
                            parent.id(),
                            color.getEditor().getText(),
                            notes.getText(),
                            active.isSelected());
            case ARTICLES ->
                    new ArticleDraft(
                            code.getText(),
                            name.getText(),
                            parent.id() == null ? 0 : parent.id(),
                            CatalogUi.decimal(diameter.getText(), "Diametro"),
                            CatalogUi.decimal(height.getText(), "Altezza"),
                            notes.getText(),
                            active.isSelected());
        };
    }

    public List<String> fingerprint() {
        if (kind == CatalogKind.PRODUCTS)
            return List.of(
                    notes.getText(),
                    Boolean.toString(active.isSelected()),
                    color.getEditor().getText(),
                    parent.id() == null ? "" : parent.id().toString());
        return List.of(
                code.getText(),
                name.getText(),
                notes.getText(),
                Boolean.toString(active.isSelected()),
                technology.getText(),
                material.getEditor().getText(),
                color.getEditor().getText(),
                parent == null || parent.id() == null ? "" : parent.id().toString(),
                diameter.getText(),
                height.getText());
    }

    public CatalogLookup parent() {
        return parent;
    }
}

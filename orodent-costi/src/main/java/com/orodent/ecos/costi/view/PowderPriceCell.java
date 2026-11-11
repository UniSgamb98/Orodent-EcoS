package com.orodent.ecos.costi.view;

import com.orodent.ecos.costi.model.PowderPrices.Row;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.scene.control.*;

import java.util.function.Consumer;

/** Only the loaded powder edits the shared form; other rows request guarded selection first. */
final class PowderPriceCell extends TableCell<Row, Row> {
    private final TextField field = new TextField();
    private final ReadOnlyLongProperty selected;
    private final ReadOnlyBooleanProperty locked;
    private final TextField reference;
    private boolean updating;

    PowderPriceCell(
            ReadOnlyLongProperty selected,
            ReadOnlyBooleanProperty locked,
            TextField reference,
            Consumer<Row> request) {
        this.selected = selected;
        this.locked = locked;
        this.reference = reference;
        field.getStyleClass().add("powder-inline-price");
        field.setMinWidth(60);
        field.setMaxWidth(Double.MAX_VALUE);
        field.setPromptText("Da compilare");
        field.setTooltip(
                new Tooltip(
                        "Modifica il prezzo di riferimento e premi Salva per registrare la"
                            + " revisione."));
        field.focusedProperty()
                .addListener(
                        (o, b, focused) -> {
                            if (focused && getItem() != null && !active() && !locked.get())
                                request.accept(getItem());
                        });
        field.textProperty()
                .addListener(
                        (o, b, text) -> {
                            if (!updating && active() && !locked.get()) reference.setText(text);
                        });
        field.setOnAction(e -> getTableView().requestFocus());
        selected.addListener((o, b, a) -> render());
        locked.addListener((o, b, a) -> render());
        reference
                .textProperty()
                .addListener(
                        (o, b, a) -> {
                            if (active()) render();
                        });
    }

    private boolean active() {
        return getItem() != null && selected.get() == getItem().powder().id();
    }

    @Override
    protected void updateItem(Row row, boolean empty) {
        super.updateItem(row, empty);
        setText(null);
        setGraphic(empty || row == null ? null : field);
        render();
    }

    private void render() {
        Row row = getItem();
        if (row == null) return;
        updating = true;
        try {
            String text = active() ? reference.getText() : PriceFormats.edit(row.unitPrice());
            if (!field.getText().equals(text)) field.setText(text);
            field.setEditable(active() && !locked.get());
            field.setId("costi-inline-price-" + row.powder().id());
            field.setAccessibleText("Prezzo di riferimento €/kg per " + row.powder().code());
        } finally {
            updating = false;
        }
    }
}

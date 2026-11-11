package com.orodent.ecos.costi.view;

import com.orodent.ecos.costi.model.PowderPrices.Row;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.function.Function;

/** Only the loaded powder edits the shared form; other rows request guarded selection first. */
final class PowderValueCell extends TableCell<Row, Row> {
    private final TextField field = new TextField();
    private final ReadOnlyLongProperty selected;
    private final ReadOnlyBooleanProperty locked;
    private final StringProperty reference;
    private final Function<Row, BigDecimal> savedValue;
    private final String prefix, caption;
    private boolean updating;

    PowderValueCell(
            ReadOnlyLongProperty selected,
            ReadOnlyBooleanProperty locked,
            StringProperty reference,
            Function<Row, BigDecimal> savedValue,
            String prefix,
            String caption,
            Consumer<Row> request) {
        this.selected = selected;
        this.locked = locked;
        this.reference = reference;
        this.savedValue = savedValue;
        this.prefix = prefix;
        this.caption = caption;
        field.getStyleClass().add("powder-inline-price");
        field.setMinWidth(60);
        field.setMaxWidth(Double.MAX_VALUE);
        field.setPromptText(prefix.equals("quantity") ? "Non indicata" : "Da compilare");
        field.setTooltip(
                new Tooltip(
                        "Modifica "
                                + caption.toLowerCase(java.util.Locale.ROOT)
                                + " e premi Salva per registrare la revisione."));
        field.focusedProperty()
                .addListener(
                        (o, b, focused) -> {
                            if (focused && getItem() != null && !active() && !locked.get())
                                request.accept(getItem());
                        });
        field.textProperty()
                .addListener(
                        (o, b, text) -> {
                            if (!updating && active() && !locked.get()) reference.set(text);
                        });
        field.setOnAction(e -> getTableView().requestFocus());
        selected.addListener((o, b, a) -> render());
        locked.addListener((o, b, a) -> render());
        reference.addListener(
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
            String text = active() ? reference.get() : PriceFormats.edit(savedValue.apply(row));
            if (!field.getText().equals(text)) field.setText(text);
            field.setEditable(active() && !locked.get());
            field.setId("costi-inline-" + prefix + "-" + row.powder().id());
            field.setAccessibleText(caption + " per " + row.powder().code());
        } finally {
            updating = false;
        }
    }
}

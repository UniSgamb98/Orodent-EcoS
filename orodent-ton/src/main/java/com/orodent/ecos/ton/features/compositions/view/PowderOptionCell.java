package com.orodent.ecos.ton.features.compositions.view;

import com.orodent.ecos.ton.features.compositions.model.Compositions.PowderRef;

import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;

/** Whole-width powder colours, including the popup and the selected revision. */
final class PowderOptionCell extends ListCell<PowderRef> {
    PowderOptionCell() {
        getStyleClass().add("ton-comp-powder-option");
        getStylesheets()
                .add(
                        getClass()
                                .getResource("/ton/css/composition-powder-options.css")
                                .toExternalForm());
    }

    @Override
    protected void updateItem(PowderRef powder, boolean empty) {
        super.updateItem(powder, empty);
        getStyleClass().removeIf(style -> style.startsWith("powder-"));
        setText(empty || powder == null ? null : powder.toString());
        setTooltip(empty || powder == null ? null : new Tooltip(powder.toString()));
        setStyle(
                empty || powder == null
                        ? ""
                        : "-fx-background-color:" + powder.appearance().background() + ";");
        if (empty || powder == null) return;
        getStyleClass()
                .add(
                        switch (powder.color() == null ? "" : powder.color()) {
                            case "BIANCA" -> "powder-white";
                            case "GIALLA" -> "powder-yellow";
                            case "ROSA" -> "powder-pink";
                            case "GRIGIA" -> "powder-gray";
                            default -> "powder-unknown";
                        });
    }
}

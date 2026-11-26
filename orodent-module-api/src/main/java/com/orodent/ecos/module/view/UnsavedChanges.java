package com.orodent.ecos.module.view;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/** The same discard confirmation used by the technical catalogue and work screens. */
public final class UnsavedChanges {
    private UnsavedChanges() {}

    public static boolean confirmDiscard(Window owner) {
        var alert =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Vuoi scartare le modifiche non salvate prima di continuare?",
                        ButtonType.YES,
                        ButtonType.NO);
        alert.setTitle("Modifiche non salvate");
        alert.setHeaderText("Modifiche non salvate");
        if (owner != null) alert.initOwner(owner);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }
}

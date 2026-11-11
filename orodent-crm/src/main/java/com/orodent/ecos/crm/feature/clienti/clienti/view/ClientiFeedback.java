package com.orodent.ecos.crm.feature.clienti.clienti.view;

import com.orodent.ecos.crm.core.database.model.VistaSalvata;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.Optional;

public class ClientiFeedback {

    public void showFeatureInDevelopment(String feature) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Funzionalità in sviluppo");
        alert.setHeaderText(feature);
        alert.setContentText("Questa funzionalità sarà disponibile prossimamente.");
        alert.showAndWait();
    }

    public Optional<SaveSearchRequest> requestSaveSearch() {
        Dialog<SaveSearchRequest> dialog = new Dialog<>();
        dialog.setTitle("Salva ricerca");
        dialog.setHeaderText("Salva i filtri e l'ordinamento correnti");

        ButtonType saveButton =
                new ButtonType("Salva", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Nome ricerca");
        CheckBox defaultCheckBox = new CheckBox("Imposta come predefinita");
        GridPane content = new GridPane();
        content.setHgap(10);
        content.setVgap(12);
        content.add(new Label("Nome"), 0, 0);
        content.add(nameField, 1, 0);
        content.add(defaultCheckBox, 1, 1);
        dialog.getDialogPane().setContent(content);

        javafx.scene.Node saveNode = dialog.getDialogPane().lookupButton(saveButton);
        saveNode.setDisable(true);
        nameField
                .textProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                saveNode.setDisable(newValue == null || newValue.isBlank()));
        dialog.setOnShown(event -> nameField.requestFocus());
        dialog.setResultConverter(
                button ->
                        button == saveButton
                                ? new SaveSearchRequest(
                                        nameField.getText(), defaultCheckBox.isSelected())
                                : null);
        return dialog.showAndWait();
    }

    public Optional<String> requestRename(VistaSalvata savedView) {
        javafx.scene.control.TextInputDialog dialog =
                new javafx.scene.control.TextInputDialog(savedView.nome());
        dialog.setTitle("Rinomina ricerca");
        dialog.setHeaderText("Scegli un nuovo nome");
        dialog.setContentText("Nome");
        return dialog.showAndWait().map(String::trim).filter(name -> !name.isBlank());
    }

    public boolean confirmDelete(VistaSalvata savedView) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Elimina ricerca");
        alert.setHeaderText("Eliminare \"" + savedView.nome() + "\"?");
        alert.setContentText("La ricerca salvata non potrà essere recuperata.");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    public void showSearchSaved(String name) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ricerca salvata");
        alert.setHeaderText("Ricerca salvata correttamente");
        alert.setContentText(name);
        alert.showAndWait();
    }

    public void showOperationCompleted(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Ricerche salvate");
        alert.setHeaderText("Operazione completata");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Operazione non riuscita");
        alert.setHeaderText("Ricerca salvata");
        alert.setContentText(
                message == null || message.isBlank() ? "Si è verificato un errore." : message);
        alert.showAndWait();
    }

    public record SaveSearchRequest(String name, boolean predefinita) {}
}

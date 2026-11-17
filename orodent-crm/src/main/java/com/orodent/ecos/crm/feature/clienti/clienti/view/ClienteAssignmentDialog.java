package com.orodent.ecos.crm.feature.clienti.clienti.view;

import com.orodent.ecos.crm.feature.auth.login.service.LoginService.LoginUser;
import com.orodent.ecos.crm.feature.clienti.clienti.service.ClienteAssignmentService.Assignment;

import javafx.beans.binding.Bindings;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClienteAssignmentDialog {
    private ClienteAssignmentDialog() {}

    public static Optional<UUID> show(Parent owner, Assignment assignment) {
        Dialog<UUID> dialog = new Dialog<>();
        dialog.setTitle("Assegna operatore");
        dialog.setHeaderText(assignment.clientName());
        dialog.initOwner(owner.getScene().getWindow());
        var pane = dialog.getDialogPane();
        pane.setId("crm-assignment-dialog");
        pane.getStylesheets().addAll(owner.getStylesheets());
        pane.getStylesheets().addAll(owner.getScene().getStylesheets());
        var confirm = new ButtonType("Assegna", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(confirm, ButtonType.CANCEL);
        ((Button) pane.lookupButton(ButtonType.CANCEL)).setText("Annulla");
        Label current =
                new Label(
                        "Operatore attuale: "
                                + (assignment.operatorName() == null
                                        ? "Non assegnato"
                                        : assignment.operatorName()));
        current.setWrapText(true);
        ComboBox<LoginUser> operators = new ComboBox<>();
        operators.setId("crm-assignment-operator");
        operators.getItems().setAll(assignment.activeOperators());
        operators.setPromptText("Seleziona il nuovo operatore");
        operators.setMaxWidth(Double.MAX_VALUE);
        operators.getItems().stream()
                .filter(o -> Objects.equals(o.id(), assignment.operatorId()))
                .findFirst()
                .ifPresent(operators::setValue);
        Label hint =
                new Label(
                        assignment.activeOperators().isEmpty()
                                ? "Nessun operatore attivo. Creane o riattivane uno nelle"
                                        + " Impostazioni."
                                : "L'assegnazione cambia per questa anagrafica. Gli autori delle"
                                        + " chiamate e delle note restano invariati.");
        hint.setWrapText(true);
        Label label = new Label("Nuovo operatore");
        label.setLabelFor(operators);
        VBox content = new VBox(12, current, new VBox(5, label, operators), hint);
        content.setPrefWidth(420);
        pane.setContent(content);
        var button = pane.lookupButton(confirm);
        button.setId("crm-assignment-confirm");
        button.disableProperty()
                .bind(
                        Bindings.createBooleanBinding(
                                () ->
                                        operators.getValue() == null
                                                || Objects.equals(
                                                        operators.getValue().id(),
                                                        assignment.operatorId()),
                                operators.valueProperty()));
        dialog.setResultConverter(b -> b == confirm ? operators.getValue().id() : null);
        dialog.setOnShown(e -> operators.requestFocus());
        return dialog.showAndWait();
    }

    public static void showError(Parent owner, Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner.getScene().getWindow());
        alert.setTitle("Assegna operatore");
        alert.setHeaderText("Assegnazione non completata");
        alert.setContentText(com.orodent.ecos.crm.core.async.CrmErrors.message(error));
        alert.showAndWait();
    }
}

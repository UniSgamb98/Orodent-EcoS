package com.orodent.ecos.crm.feature.auth.login.view;

import com.orodent.ecos.crm.feature.auth.login.service.LoginService.LoginUser;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

public class LoginView extends BorderPane {

    private final ChoiceBox<LoginUser> userChoiceBox;
    private final Button loginButton;
    private final Button createOperator = new Button("Nuovo operatore");

    public Button createOperatorButton() {
        return createOperator;
    }

    private final Label errorLabel;

    public LoginView() {
        userChoiceBox = new ChoiceBox<>();
        userChoiceBox.getStyleClass().add("login-field");
        userChoiceBox.setMaxWidth(Double.MAX_VALUE);

        loginButton = new Button("Accedi");
        loginButton.getStyleClass().add("login-primary-button");
        loginButton.setDefaultButton(true);

        errorLabel = new Label();
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        getStyleClass().add("login-root");
        setCenter(createContent());
    }

    private VBox createContent() {
        VBox wrapper = new VBox();
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(32));
        wrapper.getStyleClass().add("login-wrapper");

        VBox card = new VBox(18);
        card.setMaxWidth(420);
        card.getStyleClass().add("login-card");

        VBox heading = new VBox(6);
        heading.getStyleClass().add("login-heading");
        Label title = new Label("Bentornato");
        title.getStyleClass().add("login-title");
        Label subtitle =
                new Label(
                        "Seleziona il tuo utente per accedere a clienti, contatti e attività"
                                + " commerciali.");
        subtitle.getStyleClass().add("login-subtitle");
        subtitle.setWrapText(true);
        heading.getChildren().addAll(title, subtitle);

        VBox form = new VBox(12);
        form.getStyleClass().add("login-form");
        form.getChildren()
                .addAll(createFieldGroup("Utente", userChoiceBox), errorLabel, createActions());

        card.getChildren().addAll(heading, form);
        wrapper.getChildren().add(card);
        return wrapper;
    }

    private VBox createFieldGroup(String labelText, Control field) {
        VBox group = new VBox(6);
        Label label = new Label(labelText);
        label.getStyleClass().add("login-field-label");
        field.setMaxWidth(Double.MAX_VALUE);
        group.getChildren().addAll(label, field);
        return group;
    }

    private HBox createActions() {
        HBox actions = new HBox();
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(loginButton, Priority.NEVER);
        actions.getChildren().addAll(createOperator, loginButton);
        return actions;
    }

    public void setUsers(List<LoginUser> users) {
        userChoiceBox.getItems().setAll(users);
        if (!users.isEmpty()) {
            userChoiceBox.getSelectionModel().selectFirst();
        }
    }

    public LoginUser getSelectedUser() {
        return userChoiceBox.getSelectionModel().getSelectedItem();
    }

    public void showError(String message) {
        errorLabel.setText(
                message == null || message.isBlank() ? "Accesso non riuscito." : message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    public void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    public Button getLoginButton() {
        return loginButton;
    }
}

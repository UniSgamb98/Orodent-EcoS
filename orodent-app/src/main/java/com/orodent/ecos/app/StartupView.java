package com.orodent.ecos.app;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;

public final class StartupView extends VBox {

    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final Label statusLabel = new Label();
    private final Button retryButton = new Button("Riprova");
    private final Button closeButton = new Button("Chiudi applicazione");

    public StartupView() {
        getStyleClass().add("startup-view");
        setAlignment(Pos.CENTER);
        setSpacing(18);

        Label eyebrow = new Label("ORODENT");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Orodent-EcoS");
        title.getStyleClass().add("startup-title");
        Label subtitle = new Label("Il tuo ecosistema Orodent");
        subtitle.getStyleClass().add("muted-label");

        progressIndicator.setMaxSize(46, 46);
        statusLabel.setMaxWidth(480);
        statusLabel.setWrapText(true);
        statusLabel.setAlignment(Pos.CENTER);
        retryButton.getStyleClass().add("primary-button");
        closeButton.getStyleClass().add("secondary-button");

        getChildren().addAll(
                eyebrow,
                title,
                subtitle,
                progressIndicator,
                statusLabel,
                retryButton,
                closeButton
        );
        showLoading();
    }

    public Button retryButton() {
        return retryButton;
    }

    public Button closeButton() {
        return closeButton;
    }

    public void showLoading() {
        progressIndicator.setVisible(true);
        progressIndicator.setManaged(true);
        statusLabel.setText("Preparazione del database…");
        setButtonVisible(retryButton, false);
        setButtonVisible(closeButton, false);
    }

    public void showError(String message) {
        progressIndicator.setVisible(false);
        progressIndicator.setManaged(false);
        statusLabel.setText(message == null || message.isBlank()
                ? "Impossibile avviare il database."
                : message);
        setButtonVisible(retryButton, true);
        setButtonVisible(closeButton, true);
    }

    private void setButtonVisible(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }
}

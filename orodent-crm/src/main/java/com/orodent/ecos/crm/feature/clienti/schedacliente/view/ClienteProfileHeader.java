package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

final class ClienteProfileHeader extends VBox {

    private final Label titleLabel;
    private final Label subtitleLabel;
    private final Label acquisitionLabel;
    private final Label lastInteractionLabel;
    private final Label nextInteractionLabel;
    private final Slider involvementSlider;
    private final Button favoriteButton;
    private final Button editProfileButton;
    private final Button saveProfileEditButton;
    private final Button cancelProfileEditButton;
    private boolean updatingInvolvementSlider;

    ClienteProfileHeader() {
        super(12);
        getStyleClass().add("client-profile-hero");

        titleLabel = new Label("Cliente");
        titleLabel.getStyleClass().add("clients-title");
        titleLabel.setWrapText(true);
        titleLabel.setMinWidth(0);
        subtitleLabel = new Label("Profilo cliente e storico comunicazioni");
        subtitleLabel.getStyleClass().add("clients-subtitle");
        acquisitionLabel = createBadgeLabel();
        lastInteractionLabel = createBadgeLabel();
        nextInteractionLabel = createBadgeLabel();
        involvementSlider = createInvolvementSlider();
        favoriteButton = new Button("☆");
        favoriteButton.getStyleClass().add("client-profile-favorite-button");
        editProfileButton = new Button("Modifica anagrafica");
        editProfileButton.getStyleClass().add("clients-filter-button");
        saveProfileEditButton = new Button("Salva modifiche");
        saveProfileEditButton.getStyleClass().add("clients-primary-button");
        cancelProfileEditButton = new Button("Annulla");
        cancelProfileEditButton.getStyleClass().add("clients-filter-button");

        getChildren().addAll(createTitleRow(), createSummaryRow());
    }

    void setTitle(String title) {
        titleLabel.setText(title);
    }

    void setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle);
    }

    void setAcquisitionText(String text) {
        acquisitionLabel.setText(text);
    }

    void setLastInteractionText(String text) {
        lastInteractionLabel.setText(text);
    }

    void setNextInteractionText(String text) {
        nextInteractionLabel.setText(text);
    }

    void setNextDate(LocalDate date) {
        nextInteractionLabel.getStyleClass().remove("client-profile-overdue");
        if (date != null && date.isBefore(LocalDate.now())) {
            nextInteractionLabel.getStyleClass().add("client-profile-overdue");
            nextInteractionLabel.setText(
                    "Richiamo scaduto · "
                            + date.format(
                                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        }
    }

    void setEditMode(boolean editMode) {
        editProfileButton.setVisible(!editMode);
        editProfileButton.setManaged(!editMode);
        saveProfileEditButton.setVisible(editMode);
        saveProfileEditButton.setManaged(editMode);
        cancelProfileEditButton.setVisible(editMode);
        cancelProfileEditButton.setManaged(editMode);
        involvementSlider.setDisable(editMode);
        favoriteButton.setDisable(editMode);
    }

    void setFavorite(boolean favorite) {
        favoriteButton.setText(favorite ? "★" : "☆");
        favoriteButton.getStyleClass().remove("client-profile-favorite-active");
        if (favorite) {
            favoriteButton.getStyleClass().add("client-profile-favorite-active");
        }
    }

    Slider getInvolvementSlider() {
        return involvementSlider;
    }

    boolean isUpdatingInvolvementSlider() {
        return updatingInvolvementSlider;
    }

    int involvementSliderValue() {
        return (int) Math.round(involvementSlider.getValue());
    }

    void setInvolvementSliderValue(Integer value) {
        updatingInvolvementSlider = true;
        int safeValue = value == null || value < 1 || value > 5 ? 1 : value;
        involvementSlider.setValue(safeValue);
        updatingInvolvementSlider = false;
    }

    Button getFavoriteButton() {
        return favoriteButton;
    }

    Button getEditProfileButton() {
        return editProfileButton;
    }

    Button getSaveProfileEditButton() {
        return saveProfileEditButton;
    }

    Button getCancelProfileEditButton() {
        return cancelProfileEditButton;
    }

    private HBox createTitleRow() {
        HBox titleRow = new HBox(12);
        titleRow.getStyleClass().add("clients-title-bar");
        VBox titleBox = new VBox(4);
        titleBox.setMinWidth(0);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8);
        actions.getStyleClass().add("client-profile-header-actions");
        actions.getChildren()
                .addAll(
                        favoriteButton,
                        editProfileButton,
                        saveProfileEditButton,
                        cancelProfileEditButton);
        titleRow.getChildren().addAll(titleBox, spacer, actions);
        return titleRow;
    }

    private FlowPane createSummaryRow() {
        FlowPane summary = new FlowPane(10, 10);
        summary.getStyleClass().add("client-profile-summary-row");
        summary.getChildren()
                .addAll(
                        acquisitionLabel,
                        lastInteractionLabel,
                        nextInteractionLabel,
                        createInvolvementSliderBox());
        return summary;
    }

    private HBox createInvolvementSliderBox() {
        HBox box = new HBox(8);
        box.getStyleClass().add("client-profile-involvement-box");
        box.getChildren().addAll(new Label("Coinvolgimento"), involvementSlider);
        return box;
    }

    private Slider createInvolvementSlider() {
        Slider slider = new Slider(1, 5, 1);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setSnapToTicks(true);
        slider.getStyleClass().add("client-profile-involvement-slider");
        return slider;
    }

    private Label createBadgeLabel() {
        Label label = new Label();
        label.getStyleClass().add("client-profile-badge");
        return label;
    }
}

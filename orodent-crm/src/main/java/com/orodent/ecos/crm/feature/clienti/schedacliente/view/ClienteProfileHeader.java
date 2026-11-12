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
    private final Button saveProfileEditButton;
    private final Button cancelProfileEditButton;
    private boolean updatingInvolvementSlider;
    private boolean involvementUnset;
    private VBox identityBox;

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
        involvementSlider
                .valueProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!updatingInvolvementSlider) involvementUnset = false;
                        });
        favoriteButton = new Button("☆");
        favoriteButton.getStyleClass().add("client-profile-favorite-button");
        saveProfileEditButton = new Button("▣");
        saveProfileEditButton.setGraphic(saveIcon());
        saveProfileEditButton.setText("");
        saveProfileEditButton.setAccessibleText("Salva modifiche");
        saveProfileEditButton.setTooltip(
                new javafx.scene.control.Tooltip("Salva tutte le modifiche della scheda"));
        saveProfileEditButton.getStyleClass().add("clients-primary-button");
        cancelProfileEditButton = new Button("↶");
        cancelProfileEditButton.setAccessibleText("Scarta modifiche");
        cancelProfileEditButton.setTooltip(
                new javafx.scene.control.Tooltip("Scarta le modifiche della scheda"));
        cancelProfileEditButton.getStyleClass().add("clients-filter-button");

        createResponsiveHeader();
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
        involvementUnset = value == null;
        updatingInvolvementSlider = false;
    }

    Button getFavoriteButton() {
        return favoriteButton;
    }

    Button getSaveProfileEditButton() {
        return saveProfileEditButton;
    }

    Button getCancelProfileEditButton() {
        return cancelProfileEditButton;
    }

    private void createResponsiveHeader() {
        VBox identity = new VBox(4, titleLabel, subtitleLabel);
        identityBox = identity;
        identity.setMinWidth(0);
        identity.setPrefWidth(260);
        FlowPane summary = createSummaryRow();
        summary.setMinWidth(0);
        HBox actions = new HBox(8, favoriteButton, saveProfileEditButton, cancelProfileEditButton);
        actions.getStyleClass().add("client-profile-header-actions");
        HBox line = new HBox(18);
        line.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Boolean[] previous = {null};
        Runnable arrange =
                () -> {
                    boolean compact = getWidth() < 1250;
                    if (java.util.Objects.equals(previous[0], compact)) return;
                    previous[0] = compact;
                    line.getChildren().clear();
                    getChildren().clear();
                    if (compact) {
                        HBox.setHgrow(identity, Priority.ALWAYS);
                        line.getChildren().setAll(identity, actions);
                        getChildren().setAll(line, summary);
                    } else {
                        HBox.setHgrow(identity, Priority.NEVER);
                        HBox.setHgrow(summary, Priority.ALWAYS);
                        line.getChildren().setAll(identity, summary, actions);
                        getChildren().setAll(line);
                    }
                };
        widthProperty().addListener((o, a, b) -> arrange.run());
        arrange.run();
    }

    private javafx.scene.Node saveIcon() {
        var icon = new javafx.scene.shape.SVGPath();
        icon.setContent("M3 2 H16 L20 6 V20 H2 V2 Z M6 2 V8 H15 V2 M6 20 V12 H16 V20");
        icon.setFill(javafx.scene.paint.Color.TRANSPARENT);
        icon.setStroke(javafx.scene.paint.Color.WHITE);
        icon.setStrokeWidth(1.7);
        return icon;
    }

    void setNameEditor(javafx.scene.control.TextField field) {
        field.setId("crm-profile-name");
        field.getStyleClass().add("crm-inline-title");
        identityBox.getChildren().set(0, field);
        involvementSlider.setDisable(false);
        favoriteButton.setDisable(false);
        setDirty(false);
    }

    Integer nullableInvolvement() {
        return involvementUnset ? null : involvementSliderValue();
    }

    void setDirty(boolean dirty) {
        saveProfileEditButton.setVisible(dirty);
        saveProfileEditButton.setManaged(dirty);
        cancelProfileEditButton.setVisible(dirty);
        cancelProfileEditButton.setManaged(dirty);
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

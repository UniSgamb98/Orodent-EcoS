package com.orodent.ecos.crm.core.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

public class AppSidebar extends VBox {

    private final Button dashboardButton;
    private final Button clientsButton;
    private final Button contactsButton;
    private final Button opportunitiesButton;
    private final Button activitiesButton;
    private final Button calendarButton;
    private final Button reportsButton;
    private final Button settingsButton;

    public AppSidebar() {
        dashboardButton = createButton("Dashboard");
        clientsButton = createButton("Clienti");
        contactsButton = createButton("Contatti");
        opportunitiesButton = createButton("Opportunità");
        activitiesButton = createButton("Attività");
        calendarButton = createButton("Calendario");
        reportsButton = createButton("Report");
        settingsButton = createButton("Impostazioni");

        setSpacing(6);
        setPadding(new Insets(16, 12, 16, 12));
        setPrefWidth(135);
        getStyleClass().add("sidebar");

        getChildren()
                .addAll(
                        dashboardButton,
                        clientsButton,
                        contactsButton,
                        opportunitiesButton,
                        activitiesButton,
                        calendarButton,
                        reportsButton,
                        settingsButton);
    }

    private Button createButton(String text) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("sidebar-button");
        return button;
    }

    public Button getDashboardButton() {
        return dashboardButton;
    }

    public Button getClientsButton() {
        return clientsButton;
    }

    public Button getContactsButton() {
        return contactsButton;
    }

    public Button getOpportunitiesButton() {
        return opportunitiesButton;
    }

    public Button getActivitiesButton() {
        return activitiesButton;
    }

    public Button getCalendarButton() {
        return calendarButton;
    }

    public Button getReportsButton() {
        return reportsButton;
    }

    public Button getSettingsButton() {
        return settingsButton;
    }
}

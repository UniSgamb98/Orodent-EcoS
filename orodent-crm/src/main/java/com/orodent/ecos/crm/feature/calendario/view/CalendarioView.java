package com.orodent.ecos.crm.feature.calendario.view;

import com.orodent.ecos.crm.core.ui.AppSidebar;
import com.orodent.ecos.crm.feature.calendario.dto.CalendarioCall;
import com.orodent.ecos.crm.feature.calendario.dto.CalendarioDisplayMode;
import com.orodent.ecos.crm.feature.calendario.dto.CalendarioMonth;
import com.orodent.ecos.crm.feature.calendario.dto.CalendarioWeek;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.OperatoreFilter;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class CalendarioView extends BorderPane {

    private static final DateTimeFormatter AGENDA_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.ITALIAN);

    private final AppSidebar sidebar;
    private final Button previousYearButton;
    private final Button previousMonthButton;
    private final Button nextMonthButton;
    private final Button nextYearButton;
    private final Button weekViewButton;
    private final Button monthViewButton;
    private final Button newActivityButton;
    private final ChoiceBox<OperatoreFilter> operatorFilterChoiceBox;
    private final VBox activityList;
    private final Label agendaTitle;
    private final Label agendaSubtitle;
    private final Label currentPeriodLabel;
    private final StackPane calendarDisplay;
    private final CalendarioMonthView monthView;
    private final CalendarioWeekView weekView;
    private Map<LocalDate, List<CalendarioCall>> callsByDate = Map.of();
    private Consumer<LocalDate> selectDateHandler = date -> {};
    private Consumer<CalendarioCall> openCallHandler = call -> {};

    public CalendarioView() {
        sidebar = new AppSidebar();
        previousYearButton = createNavigationButton("/\\", "Anno precedente");
        previousMonthButton = createSecondaryButton("<");
        nextMonthButton = createSecondaryButton(">");
        nextYearButton = createNavigationButton("\\/", "Anno successivo");
        weekViewButton = createToggleButton("Settimana");
        monthViewButton = createToggleButton("Mese");
        newActivityButton = createPrimaryButton("+ Nuova attività");
        operatorFilterChoiceBox = new ChoiceBox<>();
        operatorFilterChoiceBox.getStyleClass().add("calendar-operator-filter");
        activityList = new VBox(10);
        activityList.getStyleClass().add("calendar-activity-list");
        agendaTitle = new Label("Agenda CRM");
        agendaTitle.getStyleClass().add("calendar-agenda-title");
        agendaSubtitle = new Label();
        agendaSubtitle.getStyleClass().add("calendar-agenda-subtitle");
        currentPeriodLabel = new Label();
        currentPeriodLabel.getStyleClass().add("calendar-period-label");
        calendarDisplay = new StackPane();
        monthView = new CalendarioMonthView();
        weekView = new CalendarioWeekView();
        monthView.setSelectDateHandler(this::selectDate);
        weekView.setSelectDateHandler(this::selectDate);
        monthView.setOpenCallHandler(this::openCall);
        weekView.setOpenCallHandler(this::openCall);

        setLeft(sidebar);
        setCenter(createContent());
    }

    private VBox createContent() {
        VBox content = new VBox(18);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("calendar-content");

        HBox calendarBody = createCalendarBody();
        VBox.setVgrow(calendarBody, Priority.ALWAYS);
        content.getChildren().addAll(createTitleBar(), createToolbar(), calendarBody);
        return content;
    }

    private HBox createTitleBar() {
        HBox titleBar = new HBox(12);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("calendar-title-bar");

        VBox titleBox = new VBox(4);
        Label title = new Label("Calendario");
        title.getStyleClass().add("calendar-title");
        Label subtitle =
                new Label("Pianifica chiamate, follow-up, appuntamenti e scadenze commerciali.");
        subtitle.getStyleClass().add("calendar-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleBar.getChildren().addAll(titleBox, spacer, newActivityButton);
        return titleBar;
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("calendar-toolbar");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        monthViewButton.getStyleClass().add("calendar-toggle-selected");
        toolbar.getChildren()
                .addAll(
                        previousYearButton,
                        previousMonthButton,
                        currentPeriodLabel,
                        nextMonthButton,
                        nextYearButton,
                        spacer,
                        new Label("Operatore"),
                        operatorFilterChoiceBox,
                        weekViewButton,
                        monthViewButton);
        return toolbar;
    }

    private HBox createCalendarBody() {
        HBox body = new HBox(16);
        body.setMaxHeight(Double.MAX_VALUE);
        body.getStyleClass().add("calendar-body");
        body.getChildren().addAll(createCalendarPanel(), createAgendaPanel());
        HBox.setHgrow(body.getChildren().get(0), Priority.ALWAYS);
        return body;
    }

    private VBox createCalendarPanel() {
        VBox panel = new VBox(10);
        panel.setMaxHeight(Double.MAX_VALUE);
        panel.getChildren().add(calendarDisplay);
        VBox.setVgrow(calendarDisplay, Priority.ALWAYS);
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    public void showMonth(YearMonth displayedMonth, CalendarioMonth month, LocalDate selectedDate) {
        currentPeriodLabel.setText(month.periodLabel());
        callsByDate = month.callsByDate();
        monthView.showMonth(displayedMonth, month, selectedDate);
        calendarDisplay.getChildren().setAll(monthView);
        setDisplayMode(CalendarioDisplayMode.MONTH);
        LocalDate initialDate =
                YearMonth.from(selectedDate).equals(displayedMonth)
                        ? selectedDate
                        : displayedMonth.atDay(1);
        showAgenda(initialDate);
    }

    public void showWeek(CalendarioWeek week, LocalDate selectedDate) {
        currentPeriodLabel.setText(week.periodLabel());
        callsByDate = week.callsByDate();
        weekView.showWeek(week, selectedDate);
        calendarDisplay.getChildren().setAll(weekView);
        setDisplayMode(CalendarioDisplayMode.WEEK);
        LocalDate agendaDate =
                selectedDate.isBefore(week.startDate()) || selectedDate.isAfter(week.endDate())
                        ? week.startDate()
                        : selectedDate;
        showAgenda(agendaDate);
    }

    private VBox createAgendaPanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("calendar-agenda-panel");
        panel.setMinWidth(280);
        panel.setPrefWidth(280);

        panel.getChildren().addAll(agendaTitle, agendaSubtitle, activityList);
        return panel;
    }

    private VBox createAgendaItem(CalendarioCall call) {
        VBox item = new VBox(5);
        item.setAlignment(Pos.CENTER);
        item.setMaxWidth(Double.MAX_VALUE);
        item.getStyleClass().add("calendar-agenda-item");
        Label operatorLabel = new Label(call.operatore());
        operatorLabel.setWrapText(true);
        operatorLabel.setMaxWidth(Double.MAX_VALUE);
        operatorLabel.setAlignment(Pos.CENTER);
        operatorLabel.getStyleClass().add("calendar-agenda-operator");
        Label textLabel = new Label(call.cliente());
        textLabel.setWrapText(true);
        textLabel.setMaxWidth(Double.MAX_VALUE);
        textLabel.setAlignment(Pos.CENTER);
        textLabel.getStyleClass().add("calendar-agenda-text");
        item.getChildren().addAll(operatorLabel, textLabel);
        item.setOnMouseClicked(event -> openCallHandler.accept(call));
        return item;
    }

    private void showAgenda(LocalDate date) {
        List<CalendarioCall> calls = callsByDate.getOrDefault(date, List.of());
        agendaSubtitle.setText(AGENDA_DATE_FORMATTER.format(date));
        if (calls.isEmpty()) {
            Label empty = new Label("Nessuna chiamata pianificata.");
            empty.getStyleClass().add("calendar-agenda-empty");
            activityList.getChildren().setAll(empty);
            return;
        }
        activityList.getChildren().setAll(calls.stream().map(this::createAgendaItem).toList());
    }

    private void selectDate(LocalDate date) {
        showAgenda(date);
        selectDateHandler.accept(date);
    }

    private void openCall(CalendarioCall call) {
        openCallHandler.accept(call);
    }

    private void setDisplayMode(CalendarioDisplayMode mode) {
        monthViewButton.getStyleClass().remove("calendar-toggle-selected");
        weekViewButton.getStyleClass().remove("calendar-toggle-selected");
        Button selectedButton =
                mode == CalendarioDisplayMode.WEEK ? weekViewButton : monthViewButton;
        if (!selectedButton.getStyleClass().contains("calendar-toggle-selected")) {
            selectedButton.getStyleClass().add("calendar-toggle-selected");
        }
    }

    public void setOperatorFilters(List<OperatoreFilter> filters) {
        operatorFilterChoiceBox.getItems().setAll(OperatoreFilter.empty());
        operatorFilterChoiceBox.getItems().addAll(filters);
        operatorFilterChoiceBox.getSelectionModel().selectFirst();
    }

    public void setOpenCallHandler(Consumer<CalendarioCall> handler) {
        openCallHandler = handler == null ? call -> {} : handler;
    }

    public void setSelectDateHandler(Consumer<LocalDate> handler) {
        selectDateHandler = handler == null ? date -> {} : handler;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("calendar-primary-button");
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("calendar-secondary-button");
        return button;
    }

    private Button createNavigationButton(String text, String description) {
        Button button = createSecondaryButton(text);
        button.setAccessibleText(description);
        button.setTooltip(new Tooltip(description));
        return button;
    }

    private Button createToggleButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("calendar-toggle-button");
        return button;
    }

    public AppSidebar getSidebar() {
        return sidebar;
    }

    public ChoiceBox<OperatoreFilter> getOperatorFilterChoiceBox() {
        return operatorFilterChoiceBox;
    }

    public Button getPreviousMonthButton() {
        return previousMonthButton;
    }

    public Button getPreviousYearButton() {
        return previousYearButton;
    }

    public Button getNextMonthButton() {
        return nextMonthButton;
    }

    public Button getNextYearButton() {
        return nextYearButton;
    }

    public Button getWeekViewButton() {
        return weekViewButton;
    }

    public Button getMonthViewButton() {
        return monthViewButton;
    }

    public Button getNewActivityButton() {
        return newActivityButton;
    }

    public VBox getActivityList() {
        return activityList;
    }
}

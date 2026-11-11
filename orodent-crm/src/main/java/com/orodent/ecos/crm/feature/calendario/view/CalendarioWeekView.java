package com.orodent.ecos.crm.feature.calendario.view;

import com.orodent.ecos.crm.feature.calendario.dto.CalendarioCall;
import com.orodent.ecos.crm.feature.calendario.dto.CalendarioWeek;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class CalendarioWeekView extends HBox {

    private static final double DAY_COLUMN_WIDTH = 105;
    private static final Locale ITALIAN = Locale.ITALIAN;
    private static final DateTimeFormatter DAY_MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("d MMM").withLocale(ITALIAN);

    private final Map<LocalDate, VBox> dayColumns = new HashMap<>();
    private Consumer<LocalDate> selectDateHandler = date -> {};
    private Consumer<CalendarioCall> openCallHandler = call -> {};

    public CalendarioWeekView() {
        setSpacing(8);
        getStyleClass().addAll("calendar-panel", "calendar-week");
    }

    public void showWeek(CalendarioWeek week, LocalDate selectedDate) {
        getChildren().clear();
        dayColumns.clear();
        LocalDate date = week.startDate();
        while (!date.isAfter(week.endDate())) {
            VBox dayColumn =
                    createDayColumn(
                            date,
                            date.equals(week.today()),
                            week.callsByDate().getOrDefault(date, List.of()));
            dayColumns.put(date, dayColumn);
            getChildren().add(dayColumn);
            date = date.plusDays(1);
        }
        selectDate(selectedDate);
    }

    private VBox createDayColumn(LocalDate date, boolean today, List<CalendarioCall> calls) {
        VBox column = new VBox(8);
        column.getStyleClass().add("calendar-week-day");
        if (today) {
            column.getStyleClass().add("calendar-day-today");
        }
        HBox.setHgrow(column, Priority.ALWAYS);
        column.setMinWidth(DAY_COLUMN_WIDTH);
        column.setPrefWidth(DAY_COLUMN_WIDTH);
        column.setMaxWidth(Double.MAX_VALUE);
        column.setOnMouseClicked(
                event -> {
                    selectDate(date);
                    selectDateHandler.accept(date);
                });

        Label dayName = new Label(date.getDayOfWeek().getDisplayName(TextStyle.FULL, ITALIAN));
        dayName.getStyleClass().add("calendar-week-day-name");
        Label dayDate = new Label(DAY_MONTH_FORMATTER.format(date));
        dayDate.getStyleClass().add("calendar-week-day-date");
        VBox header = new VBox(2, dayName, dayDate);
        header.setAlignment(Pos.CENTER);
        header.getStyleClass().add("calendar-week-day-header");

        VBox callsBox = new VBox(8);
        if (calls.isEmpty()) {
            Label empty = new Label("Nessuna chiamata");
            empty.getStyleClass().add("calendar-week-empty");
            callsBox.getChildren().add(empty);
        } else {
            calls.stream().map(this::createCallCard).forEach(callsBox.getChildren()::add);
        }
        ScrollPane scrollPane = new ScrollPane(callsBox);
        scrollPane.setMinWidth(0);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("calendar-week-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        column.getChildren().addAll(header, scrollPane);
        return column;
    }

    private void selectDate(LocalDate date) {
        dayColumns
                .values()
                .forEach(column -> column.getStyleClass().remove("calendar-day-selected"));
        VBox selectedColumn = dayColumns.get(date);
        if (selectedColumn != null) {
            selectedColumn.getStyleClass().add("calendar-day-selected");
        }
    }

    private Button createCallCard(CalendarioCall call) {
        Button card = new Button(call.cliente());
        card.getStyleClass().add("calendar-week-call");
        card.setMinWidth(0);
        card.setPrefWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setWrapText(true);
        card.setOnAction(event -> openCallHandler.accept(call));
        return card;
    }

    public void setSelectDateHandler(Consumer<LocalDate> handler) {
        selectDateHandler = handler == null ? date -> {} : handler;
    }

    public void setOpenCallHandler(Consumer<CalendarioCall> handler) {
        openCallHandler = handler == null ? call -> {} : handler;
    }
}

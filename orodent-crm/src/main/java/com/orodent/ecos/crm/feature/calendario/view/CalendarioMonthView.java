package com.orodent.ecos.crm.feature.calendario.view;

import com.orodent.ecos.crm.feature.calendario.dto.CalendarioCall;
import com.orodent.ecos.crm.feature.calendario.dto.CalendarioMonth;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class CalendarioMonthView extends VBox {

    private static final double HEADER_ROW_HEIGHT = 24;
    private static final String[] WEEK_DAYS = {"Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"};
    private static final int MAX_VISIBLE_CALLS = 2;

    private final GridPane monthGrid = new GridPane();
    private final Map<LocalDate, VBox> dayCells = new HashMap<>();
    private Consumer<LocalDate> selectDateHandler = date -> {};
    private Consumer<CalendarioCall> openCallHandler = call -> {};

    public CalendarioMonthView() {
        setSpacing(10);
        getStyleClass().add("calendar-panel");
        monthGrid.getStyleClass().add("calendar-month-grid");
        monthGrid.setHgap(8);
        monthGrid.setVgap(8);
        monthGrid.setMaxHeight(Double.MAX_VALUE);
        for (int day = 0; day < WEEK_DAYS.length; day++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setMinWidth(0);
            column.setPercentWidth(100.0 / WEEK_DAYS.length);
            column.setHgrow(Priority.ALWAYS);
            column.setFillWidth(true);
            monthGrid.getColumnConstraints().add(column);
        }
        VBox.setVgrow(monthGrid, Priority.ALWAYS);
        getChildren().add(monthGrid);
    }

    public void showMonth(YearMonth displayedMonth, CalendarioMonth month, LocalDate selectedDate) {
        monthGrid.getChildren().clear();
        configureRows(month);
        dayCells.clear();
        for (int column = 0; column < WEEK_DAYS.length; column++) {
            Label dayHeader = new Label(WEEK_DAYS[column]);
            dayHeader.setMinWidth(0);
            dayHeader.setMaxWidth(Double.MAX_VALUE);
            dayHeader.getStyleClass().add("calendar-day-header");
            monthGrid.add(dayHeader, column, 0);
        }

        int row = 1;
        int column = month.firstColumn();
        for (int day = 1; day <= month.dayCount(); day++) {
            LocalDate date = displayedMonth.atDay(day);
            boolean today = month.todayDay() != null && day == month.todayDay();
            VBox dayCell =
                    createDayCell(date, today, month.callsByDate().getOrDefault(date, List.of()));
            dayCells.put(date, dayCell);
            monthGrid.add(dayCell, column, row);
            column++;
            if (column == WEEK_DAYS.length) {
                column = 0;
                row++;
            }
        }
        selectDate(selectedDate);
    }

    private void configureRows(CalendarioMonth month) {
        monthGrid.getRowConstraints().clear();
        RowConstraints headerRow =
                new RowConstraints(HEADER_ROW_HEIGHT, HEADER_ROW_HEIGHT, HEADER_ROW_HEIGHT);
        headerRow.setFillHeight(true);
        monthGrid.getRowConstraints().add(headerRow);

        int weekRows =
                (month.firstColumn() + month.dayCount() + WEEK_DAYS.length - 1) / WEEK_DAYS.length;
        for (int week = 0; week < weekRows; week++) {
            RowConstraints weekRow = new RowConstraints();
            weekRow.setMinHeight(0);
            weekRow.setPrefHeight(0);
            weekRow.setVgrow(Priority.ALWAYS);
            weekRow.setFillHeight(true);
            monthGrid.getRowConstraints().add(weekRow);
        }
    }

    private VBox createDayCell(LocalDate date, boolean today, List<CalendarioCall> calls) {
        VBox cell = new VBox(6);
        cell.setMinWidth(0);
        cell.setPrefWidth(0);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMinHeight(0);
        cell.setPrefHeight(0);
        cell.setMaxHeight(Double.MAX_VALUE);
        cell.getStyleClass().add("calendar-day-cell");
        if (today) {
            cell.getStyleClass().add("calendar-day-today");
        }

        Label dayNumber = new Label(String.valueOf(date.getDayOfMonth()));
        dayNumber.getStyleClass().add("calendar-day-number");
        cell.getChildren().add(dayNumber);
        calls.stream()
                .limit(MAX_VISIBLE_CALLS)
                .map(this::createCallChip)
                .forEach(cell.getChildren()::add);
        if (calls.size() > MAX_VISIBLE_CALLS) {
            Label remaining = new Label("+" + (calls.size() - MAX_VISIBLE_CALLS) + " altre");
            remaining.getStyleClass().add("calendar-more-calls");
            cell.getChildren().add(remaining);
        }
        cell.setOnMouseClicked(
                event -> {
                    selectDate(date);
                    selectDateHandler.accept(date);
                });
        return cell;
    }

    private void selectDate(LocalDate date) {
        dayCells.values().forEach(cell -> cell.getStyleClass().remove("calendar-day-selected"));
        VBox selectedCell = dayCells.get(date);
        if (selectedCell != null) {
            selectedCell.getStyleClass().add("calendar-day-selected");
        }
    }

    private Button createCallChip(CalendarioCall call) {
        Button chip = new Button(call.cliente());
        chip.getStyleClass().add("calendar-activity-chip");
        chip.setMinWidth(0);
        chip.setPrefWidth(0);
        chip.setMaxWidth(Double.MAX_VALUE);
        chip.setOnAction(event -> openCallHandler.accept(call));
        return chip;
    }

    public void setSelectDateHandler(Consumer<LocalDate> handler) {
        selectDateHandler = handler == null ? date -> {} : handler;
    }

    public void setOpenCallHandler(Consumer<CalendarioCall> handler) {
        openCallHandler = handler == null ? call -> {} : handler;
    }
}

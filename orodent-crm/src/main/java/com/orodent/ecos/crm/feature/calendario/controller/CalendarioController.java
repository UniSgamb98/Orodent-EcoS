package com.orodent.ecos.crm.feature.calendario.controller;

import com.orodent.ecos.crm.feature.calendario.dto.CalendarioDisplayMode;
import com.orodent.ecos.crm.feature.calendario.service.CalendarioService;
import com.orodent.ecos.crm.feature.calendario.view.CalendarioView;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.OperatoreFilter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.function.Consumer;

public class CalendarioController {

    private final com.orodent.ecos.crm.core.async.AsyncLoader tasks;
    private final CalendarioView view;
    private final CalendarioService service;
    private final Consumer<UUID> openClienteHandler;
    private LocalDate referenceDate;
    private CalendarioDisplayMode displayMode = CalendarioDisplayMode.MONTH;
    private OperatoreFilter selectedOperatore = OperatoreFilter.empty();

    public CalendarioController(
            CalendarioView view,
            CalendarioService service,
            Consumer<UUID> openClienteHandler,
            com.orodent.ecos.crm.core.async.AsyncLoader tasks) {
        this.view = view;
        this.tasks = tasks;
        this.service = service;
        this.openClienteHandler = openClienteHandler;
        referenceDate = service.currentDate();
        view.getNewActivityButton().setVisible(false);
        view.getNewActivityButton().setManaged(false);

        tasks.run(service::getOperatorFilters, view::setOperatorFilters, this::error);
        view.getOperatorFilterChoiceBox()
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, oldValue, newValue) -> {
                            selectedOperatore =
                                    newValue == null ? OperatoreFilter.empty() : newValue;
                            refresh();
                        });
        view.setOpenCallHandler(call -> this.openClienteHandler.accept(call.clienteId()));
        view.setSelectDateHandler(date -> referenceDate = date);
        view.getPreviousYearButton().setOnAction(event -> moveYears(-1));
        view.getPreviousMonthButton().setOnAction(event -> movePeriod(-1));
        view.getNextMonthButton().setOnAction(event -> movePeriod(1));
        view.getNextYearButton().setOnAction(event -> moveYears(1));
        view.getMonthViewButton().setOnAction(event -> changeMode(CalendarioDisplayMode.MONTH));
        view.getWeekViewButton().setOnAction(event -> changeMode(CalendarioDisplayMode.WEEK));
        refresh();
    }

    private void changeMode(CalendarioDisplayMode mode) {
        displayMode = mode;
        refresh();
    }

    private void movePeriod(int direction) {
        referenceDate =
                displayMode == CalendarioDisplayMode.WEEK
                        ? referenceDate.plusWeeks(direction)
                        : referenceDate.plusMonths(direction);
        refresh();
    }

    private void moveYears(int direction) {
        referenceDate = referenceDate.plusYears(direction);
        refresh();
    }

    private void refresh() {
        LocalDate date = referenceDate;
        var mode = displayMode;
        UUID operatorId = selectedOperatore.id();
        tasks.run(
                () ->
                        mode == CalendarioDisplayMode.WEEK
                                ? service.getWeek(date, operatorId)
                                : service.getMonth(YearMonth.from(date), operatorId),
                result -> {
                    if (mode == CalendarioDisplayMode.WEEK)
                        view.showWeek(
                                (com.orodent.ecos.crm.feature.calendario.dto.CalendarioWeek) result,
                                date);
                    else
                        view.showMonth(
                                YearMonth.from(date),
                                (com.orodent.ecos.crm.feature.calendario.dto.CalendarioMonth)
                                        result,
                                date);
                },
                this::error);
    }

    private void error(Throwable error) {
        javafx.scene.control.Alert alert =
                new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.ERROR,
                        com.orodent.ecos.crm.core.async.CrmErrors.message(error));
        alert.showAndWait();
    }

    public CalendarioView getView() {
        return view;
    }

    public CalendarioService getService() {
        return service;
    }
}

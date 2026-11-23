package com.orodent.ecos.registro;

import com.orodent.ecos.module.*;
import com.orodent.ecos.registro.RegistroService.*;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.input.*;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

final class RegistroController implements ModuleSession {
    private final ModuleContext context;
    private final RegistroService service;
    private final RegistroView view = new RegistroView();
    private final Timeline poll;
    private int page, sequence, detailSequence;
    private boolean closed, busy, refreshingTable;
    private String detailId;

    RegistroController(ModuleContext context) {
        this.context = context;
        service = new RegistroService(context.connections());
        view.home.setOnAction(e -> context.showHome().run());
        view.refresh.setOnAction(
                e -> {
                    loadActors();
                    load();
                });
        view.apply.setOnAction(
                e -> {
                    page = 0;
                    load();
                });
        view.customer.setOnAction(
                e -> {
                    page = 0;
                    load();
                });
        view.previous.setOnAction(
                e -> {
                    if (page > 0) {
                        page--;
                        load();
                    }
                });
        view.next.setOnAction(
                e -> {
                    page++;
                    load();
                });
        view.table
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, a, b) -> {
                            if (!refreshingTable) details(b);
                        });
        view.table.setRowFactory(
                table -> {
                    var row = new javafx.scene.control.TableRow<Event>();
                    row.setOnMouseClicked(
                            e -> {
                                if (!row.isEmpty()
                                        && e.getButton() == MouseButton.PRIMARY
                                        && e.getClickCount() == 2) {
                                    var event = row.getItem();
                                    if (event.customers() == 1) openCustomer(event.customerId());
                                }
                            });
                    return row;
                });
        view.table.setOnKeyPressed(
                e -> {
                    if (e.getCode() == KeyCode.ENTER) openSelected();
                });
        poll =
                new Timeline(
                        new KeyFrame(
                                Duration.seconds(30),
                                e -> {
                                    if (view.automatic.isSelected() && !busy) load();
                                }));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
        loadActors();
        load();
    }

    private void loadActors() {
        async(
                service::actors,
                actors -> {
                    String selected =
                            view.actor.getValue() == null ? "" : view.actor.getValue().id();
                    view.actor.getItems().setAll(actors);
                    view.actor.setValue(
                            actors.stream()
                                    .filter(a -> a.id().equals(selected))
                                    .findFirst()
                                    .orElse(actors.getFirst()));
                },
                this::error);
    }

    private void load() {
        Filter filter;
        try {
            filter = view.filter(page);
        } catch (IllegalArgumentException invalid) {
            error(invalid);
            return;
        }
        int request = ++sequence;
        busy = true;
        view.feedback.setText("Caricamento…");
        async(
                () -> service.load(filter),
                result -> {
                    if (request != sequence) return;
                    busy = false;
                    if (page > 0 && result.events().isEmpty()) {
                        page--;
                        load();
                        return;
                    }
                    refreshingTable = true;
                    try {
                        view.show(result, page);
                    } finally {
                        refreshingTable = false;
                    }
                    details(view.table.getSelectionModel().getSelectedItem());
                    view.feedback.setText(
                            "Ultimo aggiornamento: " + java.time.LocalTime.now().withNano(0));
                },
                failure -> {
                    if (request == sequence) {
                        busy = false;
                        error(failure);
                    }
                });
    }

    private void details(Event event) {
        if (event != null && event.id().equals(detailId)) return;
        detailId = event == null ? null : event.id();
        ++detailSequence;
        view.showDetails(null, List.of(), 0, p -> {}, this::openCustomer);
        if (event != null) loadDetails(event, 0);
    }

    private void loadDetails(Event event, int page) {
        int request = ++detailSequence;
        async(
                () -> service.details(event.id(), page),
                values -> {
                    if (request == detailSequence)
                        view.showDetails(
                                event,
                                values,
                                page,
                                p -> loadDetails(event, p),
                                this::openCustomer);
                },
                failure -> {
                    if (request == detailSequence) {
                        detailId = null;
                        error(failure);
                    }
                });
    }

    private void openSelected() {
        Event event = view.table.getSelectionModel().getSelectedItem();
        if (event != null && event.customers() == 1) openCustomer(event.customerId());
    }

    private void openCustomer(String id) {
        async(
                () -> service.customerExists(id),
                exists -> {
                    if (exists) context.openRecord().accept("crm", id);
                    else
                        view.feedback.setText(
                                "Il cliente non è più presente. I dati storici restano consultabili"
                                        + " nel registro.");
                },
                this::error);
    }

    private <T> void async(Supplier<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        CompletableFuture.supplyAsync(work, context.workers())
                .whenComplete(
                        (value, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (!closed) {
                                                if (error == null) success.accept(value);
                                                else
                                                    failure.accept(
                                                            error.getCause() == null
                                                                    ? error
                                                                    : error.getCause());
                                            }
                                        }));
    }

    private void error(Throwable error) {
        view.feedback.setText(
                "Registro non aggiornato: "
                        + Objects.toString(
                                error.getMessage(), "verifica la connessione e riprova."));
    }

    public Parent root() {
        return view;
    }

    public void close() {
        closed = true;
        sequence++;
        detailSequence++;
        poll.stop();
    }
}

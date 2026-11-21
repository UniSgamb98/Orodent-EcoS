package com.orodent.ecos.ton.features.availability.controller;

import static com.orodent.ecos.ton.features.availability.model.Availability.*;

import com.orodent.ecos.ton.features.availability.service.AvailabilityService;
import com.orodent.ecos.ton.features.availability.view.*;
import com.orodent.ecos.ton.navigation.FeatureController;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class AvailabilityController implements FeatureController {
    private final AvailabilityView view;
    private final AvailabilityService service;
    private final Consumer<Article> open;
    private final ReadOnlyBooleanWrapper saving = new ReadOnlyBooleanWrapper();
    private CompletableFuture<?> reading;
    private long request;
    private boolean disposed, updating;

    public AvailabilityController(
            AvailabilityView view, AvailabilityService service, Consumer<Article> open) {
        this.view = view;
        this.service = service;
        this.open = open;
        view.onOpen(
                row -> {
                    if (saving.get()) return;
                    if (!row.article().active()) {
                        view.error(
                                "Questo articolo o il suo prodotto è archiviato o privo di linea."
                                    + " Verifica l’anagrafica prima di preparare la produzione.");
                        return;
                    }
                    open.accept(row.article());
                });
        view.refresh.setOnAction(e -> reload());
        view.period.setOnAction(
                e -> {
                    if (!updating) reload();
                });
        view.stockImport.setOnAction(e -> choose(Kind.DISPONIBILITA));
        view.ordersImport.setOnAction(e -> choose(Kind.ORDINI));
    }

    public void start(AvailabilityView.Position position) {
        updating = true;
        view.period.setValue(position.period());
        updating = false;
        load(position);
    }

    public void reload() {
        load(view.position());
    }

    private void load(AvailabilityView.Position position) {
        if (disposed || saving.get()) return;
        long token = ++request;
        if (reading != null) reading.cancel(false);
        view.loading(true);
        reading =
                service.load(view.period.getValue())
                        .whenComplete(
                                (data, error) ->
                                        Platform.runLater(
                                                () -> {
                                                    if (disposed || token != request) return;
                                                    view.loading(false);
                                                    if (error != null) view.error(message(error));
                                                    else {
                                                        view.show(data, position);
                                                        Platform.runLater(
                                                                () -> {
                                                                    if (!disposed
                                                                            && token == request)
                                                                        view.restore(position);
                                                                });
                                                    }
                                                }));
    }

    private void choose(Kind kind) {
        if (saving.get() || disposed) return;
        var chooser = new FileChooser();
        chooser.setTitle("Importa " + kind.label().toLowerCase(Locale.ITALIAN));
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("File CSV", "*.csv", "*.CSV", "*.txt"));
        var file = chooser.showOpenDialog(view.getScene().getWindow());
        if (file != null) importFile(kind, file.toPath());
    }

    public void importFile(Kind kind, Path path) {
        if (saving.get() || disposed) return;
        new AvailabilityImportDialog(
                        view.getScene() == null ? null : view.getScene().getWindow(),
                        kind,
                        path,
                        service,
                        value -> {
                            saving.set(value);
                            view.importing(value);
                        },
                        this::reload)
                .showAndWait();
    }

    public ReadOnlyBooleanProperty savingProperty() {
        return saving.getReadOnlyProperty();
    }

    public boolean canLeave() {
        return !saving.get();
    }

    public void dispose() {
        disposed = true;
        request++;
        if (reading != null) reading.cancel(false);
        view.refresh.setOnAction(null);
        view.period.setOnAction(null);
        view.stockImport.setOnAction(null);
        view.ordersImport.setOnAction(null);
        view.dispose();
    }

    private static String message(Throwable e) {
        while (e instanceof CompletionException && e.getCause() != null) e = e.getCause();
        return Objects.toString(e.getMessage(), "Impossibile caricare la disponibilità.");
    }
}

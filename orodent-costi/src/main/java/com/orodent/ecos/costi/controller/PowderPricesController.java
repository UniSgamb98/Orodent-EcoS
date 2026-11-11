package com.orodent.ecos.costi.controller;

import com.orodent.ecos.costi.model.PowderPrices;
import com.orodent.ecos.costi.model.PowderPrices.*;
import com.orodent.ecos.costi.service.PowderPriceService;
import com.orodent.ecos.costi.view.PowderPricesView;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Owns asynchronous work and dirty/close guards. Shared services and workers remain owned by EcoS.
 */
public final class PowderPricesController {
    private final PowderPricesView view;
    private final PowderPriceService service;
    private final Runnable showHome;
    private final ChangeListener<Window> windowChanged = (o, b, a) -> attachWindow(a);
    private final ChangeListener<Scene> sceneChanged = (o, b, a) -> attachScene(b, a);
    private final ChangeListener<Row> selectionChanged = (o, b, a) -> select(a);
    private final EventHandler<WindowEvent> closing =
            event -> {
                if (!canLeave()) event.consume();
            };
    private Detail original;
    private PowderPricesView.FormState baseline;
    private Window window;
    private CompletableFuture<?> listRead, detailRead;
    private long listEpoch, detailEpoch, selectionEpoch, selectedId;
    private int offset;
    private long inlineFocus;
    private Set<String> appliedColors;
    private boolean appliedInactive;
    private boolean disposed, busy, saving, updatingList;

    public PowderPricesController(
            PowderPricesView view, PowderPriceService service, Runnable showHome) {
        this.view = view;
        this.service = service;
        this.showHome = showHome;
        view.onChanged(this::controls);
        appliedColors = view.selectedColors();
        appliedInactive = view.inactive.isSelected();
        view.onColorsChanged(this::changeFilter);
        view.inactive.setOnAction(e -> changeFilter());
        view.onPriceRequested(this::requestPrice);
        view.previous.setOnAction(e -> reloadList(Math.max(0, offset - PowderPrices.PAGE_SIZE)));
        view.next.setOnAction(e -> reloadList(offset + PowderPrices.PAGE_SIZE));
        view.table.getSelectionModel().selectedItemProperty().addListener(selectionChanged);
        view.refresh.setOnAction(
                e -> {
                    if (busy || !canLeave()) return;
                    reloadList(offset);
                    if (selectedId != 0) load(selectedId);
                });
        view.cancel.setOnAction(
                e -> {
                    if (!busy && original != null) {
                        view.show(original);
                        baseline = view.state();
                        controls();
                    }
                });
        view.save.setOnAction(e -> save());
        view.home.setOnAction(
                e -> {
                    if (canLeave()) showHome.run();
                });
        view.sceneProperty().addListener(sceneChanged);
        attachScene(null, view.getScene());
        controls();
    }

    public void start() {
        reloadList(0);
    }

    private boolean dirty() {
        return baseline != null && !baseline.equals(view.state());
    }

    private void controls() {
        view.controls(busy, dirty(), original != null);
        view.home.setDisable(saving);
        view.refresh.setDisable(busy);
    }

    private void select(Row row) {
        if (disposed
                || updatingList
                || row == null
                || busy
                || row.powder().id() == selectedId && original != null) return;
        long intent = ++selectionEpoch;
        // Leave the TableView selection notification before opening a modal dialog.
        // Restoring selection from inside that notification can reenter it twice.
        Platform.runLater(
                () -> {
                    if (disposed
                            || busy
                            || intent != selectionEpoch
                            || view.table.getSelectionModel().getSelectedItem() != row) return;
                    if (canLeave()) load(row.powder().id());
                    else {
                        restoreSelection();
                        if (inlineFocus != 0) view.focusInlinePrice(selectedId);
                        inlineFocus = 0;
                    }
                });
    }

    private void reloadList(int start) {
        if (disposed) return;
        long epoch = ++listEpoch;
        if (listRead != null) listRead.cancel(false);
        try {
            listRead =
                    service.page(view.selectedColors(), view.inactive.isSelected(), start)
                            .whenComplete(
                                    (page, error) ->
                                            Platform.runLater(
                                                    () -> {
                                                        if (disposed || epoch != listEpoch) return;
                                                        if (error != null) {
                                                            view.feedback(message(error), true);
                                                            return;
                                                        }
                                                        offset = start;
                                                        updatingList = true;
                                                        try {
                                                            view.page(page);
                                                            restoreSelection();
                                                        } finally {
                                                            updatingList = false;
                                                        }
                                                        if (selectedId == 0
                                                                && !busy
                                                                && !page.rows().isEmpty())
                                                            view.table
                                                                    .getSelectionModel()
                                                                    .selectFirst();
                                                    }));
        } catch (IllegalArgumentException invalid) {
            view.feedback(invalid.getMessage(), true);
        }
    }

    private void requestPrice(Row row) {
        if (disposed || busy) return;
        inlineFocus = row.powder().id();
        if (original != null && selectedId == inlineFocus) {
            inlineFocus = 0;
            view.focusInlinePrice(selectedId);
            return;
        }
        view.table.getSelectionModel().select(row);
        select(row);
    }

    private void changeFilter() {
        if (disposed || busy || !canLeave()) {
            view.selectedColors(appliedColors);
            view.inactive.setSelected(appliedInactive);
            return;
        }
        appliedColors = view.selectedColors();
        appliedInactive = view.inactive.isSelected();
        selectionEpoch++;
        detailEpoch++;
        if (detailRead != null) detailRead.cancel(false);
        inlineFocus = 0;
        selectedId = 0;
        original = null;
        baseline = null;
        view.clear();
        controls();
        reloadList(0);
    }

    private void restoreSelection() {
        selectionEpoch++;
        boolean before = updatingList;
        updatingList = true;
        try {
            view.table.getSelectionModel().clearSelection();
            view.table.getItems().stream()
                    .filter(row -> row.powder().id() == selectedId)
                    .findFirst()
                    .ifPresent(view.table.getSelectionModel()::select);
        } finally {
            updatingList = before;
        }
    }

    private void load(long id) {
        if (disposed) return;
        selectedId = id;
        long epoch = ++detailEpoch;
        if (detailRead != null) detailRead.cancel(false);
        busy = true;
        original = null;
        baseline = null;
        view.clear();
        view.feedback("Caricamento…", false);
        controls();
        detailRead =
                service.load(id)
                        .whenComplete(
                                (detail, error) ->
                                        Platform.runLater(
                                                () -> {
                                                    if (disposed || epoch != detailEpoch) return;
                                                    busy = false;
                                                    if (error != null) {
                                                        view.feedback(message(error), true);
                                                        controls();
                                                        return;
                                                    }
                                                    original = detail;
                                                    view.show(detail);
                                                    baseline = view.state();
                                                    controls();
                                                    if (inlineFocus == id) {
                                                        inlineFocus = 0;
                                                        view.focusInlinePrice(id);
                                                    }
                                                }));
    }

    private void save() {
        if (disposed || busy || original == null || !dirty()) return;
        Draft draft;
        try {
            draft = view.draft();
        } catch (RuntimeException invalid) {
            view.feedback(message(invalid), true);
            return;
        }
        Detail expected = original;
        saving = true;
        busy = true;
        controls();
        view.feedback("Salvataggio…", false);
        service.save(expected.powder().id(), expected.version(), draft)
                .whenComplete(
                        (saved, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed) return;
                                            busy = false;
                                            saving = false;
                                            if (error != null) {
                                                view.feedback(
                                                        message(error)
                                                                + " Nessun tentativo automatico di"
                                                                + " salvataggio: verifica e"
                                                                + " ricarica prima di riprovare.",
                                                        true);
                                                controls();
                                                return;
                                            }
                                            original = saved;
                                            view.show(saved);
                                            baseline = view.state();
                                            controls();
                                            view.feedback(
                                                    "Prezzi salvati · revisione "
                                                            + saved.version()
                                                            + ".",
                                                    false);
                                            reloadList(offset);
                                        }));
    }

    public boolean canLeave() {
        if (saving) return false;
        if (!dirty()) return true;
        Alert alert =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Ci sono modifiche non salvate. Vuoi scartarle e proseguire?",
                        ButtonType.CANCEL,
                        ButtonType.OK);
        alert.setTitle("Modifiche ai prezzi");
        alert.setHeaderText(null);
        if (window != null) alert.initOwner(window);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        return error.getMessage() == null
                ? "Operazione non riuscita. Verifica la connessione al database."
                : error.getMessage();
    }

    private void attachScene(Scene before, Scene after) {
        if (before != null) before.windowProperty().removeListener(windowChanged);
        if (after != null) after.windowProperty().addListener(windowChanged);
        attachWindow(after == null ? null : after.getWindow());
    }

    private void attachWindow(Window next) {
        if (window != null) window.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closing);
        window = next;
        if (window != null) window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closing);
    }

    public void dispose() {
        disposed = true;
        listEpoch++;
        detailEpoch++;
        if (listRead != null) listRead.cancel(false);
        if (detailRead != null) detailRead.cancel(false);
        view.table.getSelectionModel().selectedItemProperty().removeListener(selectionChanged);
        view.onChanged(() -> {});
        attachScene(view.getScene(), null);
        view.sceneProperty().removeListener(sceneChanged);
        view.onColorsChanged(() -> {});
        view.onPriceRequested(row -> {});
        view.inactive.setOnAction(null);
        for (Button button :
                java.util.List.of(
                        view.home, view.save, view.cancel, view.refresh, view.previous, view.next))
            button.setOnAction(null);
    }
}

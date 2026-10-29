package com.orodent.ecos.ton.features.documents.controller;

import static com.orodent.ecos.ton.features.documents.model.DocumentTemplates.*;

import com.orodent.ecos.ton.features.documents.service.DocumentTemplateService;
import com.orodent.ecos.ton.features.documents.view.DocumentTemplatesView;
import com.orodent.ecos.ton.navigation.FeatureController;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.*;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns navigation, stale read guards and the editor lifecycle; commands are never retried. */
public final class DocumentTemplatesController implements FeatureController {
    private final DocumentTemplatesView view;
    private final DocumentTemplateService service;
    private final Executor workers;
    private final SimpleBooleanProperty saving = new SimpleBooleanProperty();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
    private final ChangeListener<String> searchListener = (o, b, a) -> debounce.playFromStart();
    private final EventHandler<WindowEvent> closeHandler =
            e -> {
                if (!canLeave()) e.consume();
            };
    private final ChangeListener<Window> windowListener = (o, b, a) -> attachWindow(a);
    private final ChangeListener<Scene> sceneListener = (o, b, a) -> attachScene(b, a);
    private Window window;
    private CompletableFuture<?> listRead, detailRead, versionsRead;
    private Detail original;
    private String baseline, baselineCode = "";
    private boolean editing, updatingVersions, loading;
    private volatile boolean disposed;
    private long generation;
    private int offset;

    public DocumentTemplatesController(
            DocumentTemplatesView view, DocumentTemplateService service, Executor workers) {
        this.view = view;
        this.service = service;
        this.workers = workers;
        view.search.textProperty().addListener(searchListener);
        debounce.setOnFinished(e -> reloadList(0));
        view.search.setOnAction(e -> reloadList(0));
        view.refresh.setOnAction(e -> reloadList(offset));
        view.previous.setOnAction(
                e -> reloadList(Math.max(0, offset - DocumentTemplateService.PAGE_SIZE)));
        view.next.setOnAction(e -> reloadList(offset + DocumentTemplateService.PAGE_SIZE));
        view.open.setOnAction(e -> openSelected());
        view.table.setOnMouseClicked(
                e -> {
                    if (e.getClickCount() == 2) openSelected();
                });
        view.table.setOnKeyPressed(
                e -> {
                    if (e.getCode() == KeyCode.ENTER) {
                        openSelected();
                        e.consume();
                    }
                });
        view.create.setOnAction(
                e -> {
                    if (canLeave()) openEditor(null);
                });
        view.back.setOnAction(
                e -> {
                    if (canLeave()) showList();
                });
        view.save.setOnAction(e -> save(false));
        view.approve.setOnAction(e -> save(true));
        view.reload.setOnAction(
                e -> {
                    if (original != null && canLeave()) load(original.template().id());
                });
        view.newVersion.setOnAction(
                e -> {
                    if (original != null && canLeave())
                        write(
                                () -> service.newVersion(original.template()),
                                "Nuova bozza creata dalla versione selezionata.");
                });
        view.delete.setOnAction(
                e -> {
                    if (original != null
                            && !saving.get()
                            && confirm(
                                    "Elimina bozza",
                                    "Eliminare questa bozza e le eventuali modifiche locali? Le"
                                        + " versioni approvate rimangono conservate."))
                        command(() -> service.delete(original.template()), ignored -> showList());
                });
        view.versions.setOnAction(
                e -> {
                    Template next = view.versions.getValue();
                    if (updatingVersions
                            || next == null
                            || original == null
                            || next.id() == original.template().id()) return;
                    if (canLeave()) load(next.id());
                    else selectVersion(original.template());
                });
        view.importFile.setOnAction(e -> importModel());
        view.exportFile.setOnAction(e -> exportModel());
        view.sceneProperty().addListener(sceneListener);
        attachScene(null, view.getScene());
    }

    public void start() {
        reloadList(0);
    }

    @Override
    public ReadOnlyBooleanProperty savingProperty() {
        return saving;
    }

    private void attachScene(Scene before, Scene after) {
        if (before != null) before.windowProperty().removeListener(windowListener);
        if (after != null) after.windowProperty().addListener(windowListener);
        attachWindow(after == null ? null : after.getWindow());
    }

    private void attachWindow(Window next) {
        if (window != null)
            window.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeHandler);
        window = next;
        if (window != null) window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closeHandler);
    }

    @Override
    public boolean canLeave() {
        if (saving.get()) return false;
        if (!editing) return true;
        boolean dirty = !view.code.getText().equals(baselineCode);
        if (view.editor() != null && view.editor().ready() && baseline != null) {
            try {
                dirty |= !baseline.equals(view.editor().serialize());
            } catch (RuntimeException invalid) {
                dirty = true;
            }
        }
        return !dirty
                || confirm(
                        "Modifiche al modello",
                        "Ci sono modifiche non salvate. Vuoi scartarle e proseguire?");
    }

    private boolean confirm(String title, String message) {
        var alert =
                new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (window != null) alert.initOwner(window);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void openSelected() {
        Template selected = view.table.getSelectionModel().getSelectedItem();
        if (selected != null && canLeave()) load(selected.id());
    }

    private void reloadList(int nextOffset) {
        cancelReads();
        offset = nextOffset;
        long request = ++generation;
        view.listingBusy(true);
        var read = service.page(view.search.getText(), offset);
        listRead = read;
        read.whenComplete(
                (page, error) ->
                        Platform.runLater(
                                () -> {
                                    if (disposed || request != generation || listRead != read)
                                        return;
                                    listRead = null;
                                    view.listingBusy(false);
                                    if (error != null) view.feedback(message(error), true);
                                    else view.page(page);
                                }));
    }

    private void showList() {
        cancelReads();
        generation++;
        editing = false;
        original = null;
        baseline = null;
        view.showList();
        reloadList(offset);
    }

    private void load(long id) {
        cancelReads();
        long request = ++generation;
        loading = true;
        controls();
        var read = service.load(id);
        detailRead = read;
        read.whenComplete(
                (detail, error) ->
                        Platform.runLater(
                                () -> {
                                    if (disposed || request != generation || detailRead != read)
                                        return;
                                    detailRead = null;
                                    loading = false;
                                    if (error != null) {
                                        controls();
                                        view.feedback(message(error), true);
                                        if (original != null) selectVersion(original.template());
                                    } else openEditor(detail);
                                }));
    }

    private void openEditor(Detail detail) {
        cancelReads();
        generation++;
        editing = true;
        loading = false;
        original = detail;
        baseline = null;
        view.code.setText(detail == null ? "" : detail.template().code());
        baselineCode = view.code.getText();
        var editor = view.showEditor();
        if (editor.ready()) render(detail);
        else
            editor.start(
                    () -> {
                        if (!disposed) render(original);
                    },
                    error -> {
                        controls();
                        view.feedback(error, true);
                    });
        controls();
    }

    private void render(Detail detail) {
        try {
            if (detail == null) view.editor().newModel();
            else view.editor().restore(detail.content());
            baseline = view.editor().serialize();
            baselineCode = view.code.getText();
            controls();
            loadVersions();
            view.feedback(
                    "Anteprima con dati di esempio · Nessun documento di produzione emesso.",
                    false);
        } catch (RuntimeException invalid) {
            // Do not let a failed restore expose the preceding document under another identity.
            baseline = null;
            view.closeEditor();
            view.feedback(message(invalid), true);
            controls();
        }
    }

    private void controls() {
        view.controls(
                original == null ? null : original.template(),
                saving.get() || loading,
                view.editor() != null && view.editor().ready());
    }

    private void selectVersion(Template selected) {
        updatingVersions = true;
        try {
            view.versions.getSelectionModel().select(selected);
        } finally {
            updatingVersions = false;
        }
    }

    private void loadVersions() {
        updatingVersions = true;
        view.versions.getItems().clear();
        updatingVersions = false;
        if (original == null) return;
        long request = generation;
        String code = original.template().code();
        var read = service.versions(code);
        versionsRead = read;
        read.whenComplete(
                (versions, error) ->
                        Platform.runLater(
                                () -> {
                                    if (disposed || request != generation || versionsRead != read)
                                        return;
                                    versionsRead = null;
                                    if (error != null) {
                                        view.feedback(message(error), true);
                                        return;
                                    }
                                    updatingVersions = true;
                                    try {
                                        view.versions.getItems().setAll(versions);
                                        view.versions
                                                .getSelectionModel()
                                                .select(original.template());
                                    } finally {
                                        updatingVersions = false;
                                    }
                                }));
    }

    private void save(boolean approve) {
        if (saving.get() || loading || view.editor() == null || !view.editor().ready()) return;
        try {
            var draft = new Draft(view.code.getText(), view.editor().serialize());
            if (approve) {
                draft.requireApprovable();
                if (!confirm(
                        "Approva modello",
                        "La versione approvata diventa di sola lettura. L’anteprima usa ancora dati"
                            + " di esempio: l’emissione dalle produzioni sarà collegata"
                            + " successivamente."))
                    return;
            }
            write(
                    () ->
                            original == null
                                    ? service.create(draft, approve)
                                    : service.save(original.template(), draft, approve),
                    approve
                            ? "Modello approvato. Per modificarlo crea una nuova versione."
                            : "Bozza salvata nel database condiviso.");
        } catch (RuntimeException invalid) {
            view.feedback(message(invalid), true);
        }
    }

    private void write(Supplier<CompletableFuture<Detail>> operation, String success) {
        command(
                operation,
                detail -> {
                    openEditor(detail);
                    view.feedback(success, false);
                });
    }

    private <T> void command(Supplier<CompletableFuture<T>> operation, Consumer<T> success) {
        if (disposed || saving.get()) return;
        saving.set(true);
        controls();
        CompletableFuture<T> future;
        try {
            future = operation.get();
        } catch (RuntimeException failure) {
            saving.set(false);
            controls();
            view.feedback(message(failure), true);
            return;
        }
        future.whenComplete(
                (result, error) -> {
                    if (disposed) {
                        if (error != null)
                            System.getLogger(getClass().getName())
                                    .log(
                                            System.Logger.Level.WARNING,
                                            "Operazione modello terminata con errore dopo la"
                                                + " chiusura",
                                            error);
                        return;
                    }
                    Platform.runLater(
                            () -> {
                                if (disposed) return;
                                saving.set(false);
                                controls();
                                if (error != null) view.feedback(message(error), true);
                                else success.accept(result);
                            });
                });
    }

    private FileChooser chooser(String title) {
        var chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("Modello documento (*.json)", "*.json"));
        return chooser;
    }

    private void importModel() {
        if (saving.get() || !canLeave()) return;
        var file = chooser("Importa un modello nell’attuale bozza").showOpenDialog(window);
        if (file == null) return;
        command(
                () ->
                        CompletableFuture.supplyAsync(
                                () -> {
                                    try {
                                        if (Files.size(file.toPath()) > MAX_CONTENT)
                                            throw new IllegalArgumentException(
                                                    "Il file supera 8 MB.");
                                        try (var input = Files.newInputStream(file.toPath())) {
                                            byte[] bytes = input.readNBytes(MAX_CONTENT + 1);
                                            if (bytes.length > MAX_CONTENT)
                                                throw new IllegalArgumentException(
                                                        "Il file supera 8 MB.");
                                            return new String(bytes, StandardCharsets.UTF_8);
                                        }
                                    } catch (IOException failure) {
                                        throw new IllegalArgumentException(
                                                "Impossibile leggere il file selezionato.",
                                                failure);
                                    }
                                },
                                workers),
                content -> {
                    try {
                        view.editor().restore(content);
                        view.feedback(
                                "Modello importato nell’editor. Salva la bozza per conservarlo nel"
                                    + " DB.",
                                false);
                    } catch (RuntimeException invalid) {
                        view.feedback(message(invalid), true);
                    }
                    controls();
                });
    }

    private void exportModel() {
        if (saving.get() || view.editor() == null || !view.editor().ready()) return;
        String content;
        try {
            content = view.editor().serialize();
        } catch (RuntimeException invalid) {
            view.feedback(message(invalid), true);
            return;
        }
        var chooser = chooser("Esporta modello");
        chooser.setInitialFileName(
                (view.code.getText().isBlank()
                                ? "modello"
                                : view.code.getText().replaceAll("[^a-zA-Z0-9._-]", "-"))
                        + ".json");
        var file = chooser.showSaveDialog(window);
        if (file == null) return;
        command(
                () ->
                        CompletableFuture.supplyAsync(
                                () -> {
                                    try {
                                        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                                        if (bytes.length > MAX_CONTENT)
                                            throw new IllegalArgumentException(
                                                    "Il file supera 8 MB. Riduci le immagini.");
                                        Files.write(file.toPath(), bytes);
                                        return file;
                                    } catch (IOException failure) {
                                        throw new IllegalArgumentException(
                                                "Impossibile esportare il modello nel file scelto.",
                                                failure);
                                    }
                                },
                                workers),
                ignored ->
                        view.feedback(
                                "Modello esportato. Le modifiche al DB si salvano con Salva bozza.",
                                false));
    }

    private void cancelReads() {
        debounce.stop();
        for (var read : new CompletableFuture<?>[] {listRead, detailRead, versionsRead})
            if (read != null) read.cancel(false);
        listRead = null;
        detailRead = null;
        versionsRead = null;
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        return error.getMessage() == null
                ? "Operazione non riuscita. Verifica la connessione e ricarica prima di riprovare."
                : error.getMessage();
    }

    @Override
    public void dispose() {
        disposed = true;
        generation++;
        cancelReads();
        attachScene(view.getScene(), null);
        view.sceneProperty().removeListener(sceneListener);
        view.search.textProperty().removeListener(searchListener);
        view.search.setOnAction(null);
        debounce.setOnFinished(null);
        view.table.setOnMouseClicked(null);
        view.table.setOnKeyPressed(null);
        view.versions.setOnAction(null);
        for (var button :
                List.of(
                        view.create,
                        view.open,
                        view.refresh,
                        view.previous,
                        view.next,
                        view.back,
                        view.save,
                        view.approve,
                        view.newVersion,
                        view.delete,
                        view.importFile,
                        view.exportFile,
                        view.reload)) button.setOnAction(null);
        view.dispose();
    }
}

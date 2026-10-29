package com.orodent.ecos.ton.features.documents.view;

import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Worker;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.util.Duration;

import netscape.javascript.JSObject;

import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Local, disposable WebView. Only Java calls the narrow JS API; no Java object is exposed to HTML.
 */
public final class DocumentEditorPane extends BorderPane {
    private final WebView web = new WebView();
    private final ReadOnlyBooleanWrapper ready = new ReadOnlyBooleanWrapper();
    private final Label status = new Label("Caricamento editor locale…");
    private final String resource;
    private final URI resourceUri;
    private JSObject api;
    private boolean closed;
    private final PauseTransition startupCheck = new PauseTransition(Duration.millis(50));
    private int attempts;
    private Runnable onReady = () -> {};
    private Consumer<String> onError = ignored -> {};

    public DocumentEditorPane() {
        setId("ton-document-editor");
        setMinSize(0, 0);
        web.setId("ton-document-webview");
        web.setMinSize(0, 0);
        web.setPrefSize(900, 580);
        resource =
                Objects.requireNonNull(getClass().getResource("/ton/document-editor/index.html"))
                        .toExternalForm();
        resourceUri = URI.create(resource);
        var engine = web.getEngine();
        web.setContextMenuEnabled(false);
        engine.setCreatePopupHandler(ignored -> null);
        engine.setConfirmHandler(
                message -> {
                    var alert =
                            new Alert(
                                    Alert.AlertType.CONFIRMATION,
                                    message,
                                    ButtonType.CANCEL,
                                    ButtonType.OK);
                    if (getScene() != null) alert.initOwner(getScene().getWindow());
                    return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
                });
        engine.setOnAlert(event -> onError.accept(event.getData()));
        engine.locationProperty()
                .addListener(
                        (o, before, after) -> {
                            if (!closed && !isLocalResource(after)) {
                                engine.getLoadWorker().cancel();
                                ready.set(false);
                                api = null;
                                setCenter(status);
                                status.setText("Navigazione esterna bloccata. Riapri il modello.");
                                onError.accept(status.getText());
                            }
                        });
        engine.getLoadWorker()
                .stateProperty()
                .addListener(
                        (o, before, after) -> {
                            if (closed) return;
                            if (after == Worker.State.SUCCEEDED) {
                                // WebKit can report the load complete before its final script
                                // callback.
                                attempts = 0;
                                startupCheck.setOnFinished(event -> initialize());
                                startupCheck.playFromStart();
                            } else if (after == Worker.State.FAILED)
                                failed(
                                        "Impossibile caricare l’editor locale. Chiudi e riapri la"
                                                + " scheda.");
                        });
        setCenter(web);
    }

    private boolean isLocalResource(String location) {
        try {
            // WebKit normalizes file:/ to file:///; URI equality treats them identically.
            return URI.create(location).equals(resourceUri);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private void initialize() {
        if (closed) return;
        try {
            var engine = web.getEngine();
            String state =
                    String.valueOf(
                            engine.executeScript("document.documentElement.dataset.editorReady"));
            if (!"true".equals(state)) {
                if ("error".equals(state) || ++attempts >= 200)
                    throw new IllegalStateException(
                            "Inizializzazione editor non riuscita. Chiudi e riapri la scheda.");
                startupCheck.playFromStart();
                return;
            }
            api = (JSObject) engine.executeScript("window.OrodentDocumentEditor");
            api.call("setEmbedded");
            ready.set(true);
            onReady.run();
        } catch (RuntimeException failure) {
            failed(failure.getMessage());
        }
    }

    public void start(Runnable initialized, Consumer<String> failure) {
        onReady = initialized;
        onError = failure;
        web.getEngine().load(resource);
    }

    private void failed(String message) {
        ready.set(false);
        api = null;
        status.setText(message);
        setCenter(status);
        onError.accept(message);
    }

    public ReadOnlyBooleanProperty readyProperty() {
        return ready.getReadOnlyProperty();
    }

    public boolean ready() {
        return ready.get() && !closed;
    }

    private Object call(String method, Object... args) {
        if (!ready()) throw new IllegalStateException("Attendi il caricamento dell’editor.");
        return api.call(method, args);
    }

    public String serialize() {
        return (String) call("serialize");
    }

    public void restore(String payload) {
        call("restore", payload);
    }

    public void newModel() {
        call("newModel");
    }

    public void editable(boolean value) {
        if (ready()) call("setEditable", value);
    }

    public void dispose() {
        if (closed) return;
        if (ready()) {
            try {
                api.call("destroy");
            } catch (RuntimeException ignored) {
            }
        }
        closed = true;
        startupCheck.stop();
        startupCheck.setOnFinished(null);
        ready.set(false);
        api = null;
        onReady = () -> {};
        onError = ignored -> {};
        web.getEngine().setOnAlert(null);
        web.getEngine().setConfirmHandler(null);
        web.getEngine().getLoadWorker().cancel();
        web.getEngine().load(null);
        setCenter(null);
    }
}

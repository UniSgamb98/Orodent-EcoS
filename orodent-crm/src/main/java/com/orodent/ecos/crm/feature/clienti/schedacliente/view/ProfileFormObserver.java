package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.scene.*;
import javafx.scene.control.*;

import java.util.*;

/** Watches logical form controls, including collapsed content, without inspecting control skins. */
final class ProfileFormObserver implements AutoCloseable {
    private final Parent root;
    private final Runnable changed;
    private final List<Runnable> detach = new ArrayList<>();
    private final Set<Node> nodes = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean queued, closed;

    ProfileFormObserver(Parent root, Runnable changed) {
        this.root = root;
        this.changed = changed;
        rebind();
    }

    void rebind() {
        if (closed) return;
        detach.forEach(Runnable::run);
        detach.clear();
        nodes.clear();
        watch(root);
    }

    private void value(Observable p) {
        InvalidationListener listener = o -> changed.run();
        p.addListener(listener);
        detach.add(() -> p.removeListener(listener));
    }

    private void structure(Observable p) {
        InvalidationListener listener =
                o -> {
                    if (queued || closed) return;
                    queued = true;
                    Platform.runLater(
                            () -> {
                                queued = false;
                                if (!closed) {
                                    rebind();
                                    changed.run();
                                }
                            });
                };
        p.addListener(listener);
        detach.add(() -> p.removeListener(listener));
    }

    private void watch(Node n) {
        if (!nodes.add(n)) return;
        if (n instanceof TextInputControl t) value(t.textProperty());
        else if (n instanceof DatePicker d) {
            value(d.valueProperty());
            value(d.getEditor().textProperty());
        } else if (n instanceof ComboBox<?> b) {
            value(b.valueProperty());
            if (b.isEditable()) value(b.getEditor().textProperty());
        } else if (n instanceof ChoiceBox<?> b) value(b.valueProperty());
        else if (n instanceof CheckBox b) value(b.selectedProperty());
        else if (n instanceof Slider b) value(b.valueProperty());
        else if (n instanceof ScrollPane s) {
            structure(s.contentProperty());
            if (s.getContent() != null) watch(s.getContent());
        } else if (n instanceof TitledPane t) {
            structure(t.contentProperty());
            if (t.getContent() != null) watch(t.getContent());
        } else if (!(n instanceof Control) && n instanceof Parent p) {
            structure(p.getChildrenUnmodifiable());
            p.getChildrenUnmodifiable().forEach(this::watch);
        }
    }

    void prepareInitialDates() {
        for (Node node : nodes)
            if (node instanceof DatePicker date)
                date.getEditor().setText(date.getConverter().toString(date.getValue()));
    }

    Map<DatePicker, String> pendingDates() {
        Map<DatePicker, String> invalid = new IdentityHashMap<>();
        for (Node n : nodes)
            if (n instanceof DatePicker d) {
                String raw = d.getEditor().getText();
                try {
                    if (!Objects.equals(parse(d, raw), d.getValue())) invalid.put(d, raw);
                } catch (RuntimeException e) {
                    invalid.put(d, raw);
                }
            }
        return invalid;
    }

    void commitDates() {
        for (Node n : List.copyOf(nodes))
            if (n instanceof DatePicker d) {
                try {
                    d.setValue(parse(d, d.getEditor().getText()));
                } catch (RuntimeException e) {
                    d.requestFocus();
                    throw new IllegalArgumentException(
                            "Data non valida: " + d.getEditor().getText(), e);
                }
            }
    }

    private java.time.LocalDate parse(DatePicker d, String value) {
        return value == null || value.isBlank() ? null : d.getConverter().fromString(value);
    }

    public void close() {
        closed = true;
        detach.forEach(Runnable::run);
        detach.clear();
        nodes.clear();
    }
}

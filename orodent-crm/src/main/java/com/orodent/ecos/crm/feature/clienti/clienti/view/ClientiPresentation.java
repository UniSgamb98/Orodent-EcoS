package com.orodent.ecos.crm.feature.clienti.clienti.view;

import com.orodent.ecos.crm.core.database.model.CallOutcome;

import javafx.scene.control.Label;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class ClientiPresentation {
    private ClientiPresentation() {}

    static String status(String raw) {
        if (raw == null || raw.isBlank()) return "Non indicato";
        var outcome = CallOutcome.fromCode(raw.strip().toUpperCase(Locale.ROOT).replace(' ', '_'));
        return outcome == null ? raw.strip().replace('_', ' ') : outcome.label();
    }

    static Label badge(String raw) {
        Label label = new Label(status(raw));
        label.getStyleClass().add("clients-status-badge");
        String key = status(raw).toLowerCase(Locale.ROOT);
        if (key.equals("cliente")) label.getStyleClass().add("clients-status-customer");
        else if (List.of("listino", "campione", "presentazione").contains(key))
            label.getStyleClass().add("clients-status-progress");
        return label;
    }

    static boolean overdue(String text) {
        try {
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    .isBefore(LocalDate.now());
        } catch (RuntimeException e) {
            return false;
        }
    }

    static String value(String text) {
        return text == null || text.isBlank() ? "—" : text;
    }
}

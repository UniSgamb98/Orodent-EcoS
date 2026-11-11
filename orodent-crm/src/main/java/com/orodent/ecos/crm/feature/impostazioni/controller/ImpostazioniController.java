package com.orodent.ecos.crm.feature.impostazioni.controller;

import com.orodent.ecos.crm.core.async.AsyncLoader;
import com.orodent.ecos.crm.feature.impostazioni.service.ImpostazioniService;
import com.orodent.ecos.crm.feature.impostazioni.view.ImpostazioniView;

import javafx.scene.control.Alert;

import java.util.*;

public final class ImpostazioniController {
    public ImpostazioniController(
            ImpostazioniView view, ImpostazioniService service, AsyncLoader tasks) {
        configure(
                view,
                service,
                tasks,
                "MATERIALI_DI_CONSUMO",
                List.of("MATERIALE", "MARCHIO", "MODELLO"));
        configure(view, service, tasks, "CANALI_DI_ACQUISTO", List.of("MODALITA", "NOTA"));
        configure(view, service, tasks, "FRESATORI", List.of("MARCA", "MODELLO"));
        configure(view, service, tasks, "FORNI", List.of("TECNOLOGIA", "MARCA", "MODELLO"));
    }

    private void configure(
            ImpostazioniView view,
            ImpostazioniService service,
            AsyncLoader tasks,
            String table,
            List<String> columns) {
        var editor = view.getEditor(table);
        editor.onAdd(editor::addEmpty);
        editor.onEditChanged(
                editing -> {
                    if (editing) return;
                    var rows = editor.getVoci();
                    tasks.run(
                            () -> {
                                service.saveVoci(table, columns, rows);
                                return service.getVoci(table, columns);
                            },
                            data -> {
                                editor.setVoci(data);
                                tasks.markClean();
                            },
                            error -> {
                                editor.resumeEditing();
                                new Alert(
                                                Alert.AlertType.ERROR,
                                                com.orodent.ecos.crm.core.async.CrmErrors.message(
                                                        error))
                                        .showAndWait();
                            });
                });
        tasks.run(
                () -> service.getVoci(table, columns),
                editor::setVoci,
                error ->
                        new Alert(
                                        Alert.AlertType.ERROR,
                                        com.orodent.ecos.crm.core.async.CrmErrors.message(error))
                                .showAndWait());
    }
}

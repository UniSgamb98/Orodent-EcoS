package com.orodent.ecos.crm.feature.impostazioni.controller;

import com.orodent.ecos.crm.core.async.AsyncLoader;
import com.orodent.ecos.crm.core.async.CrmErrors;
import com.orodent.ecos.crm.core.database.service.OperatorManagementService;
import com.orodent.ecos.crm.core.database.service.OperatorManagementService.Operator;
import com.orodent.ecos.crm.feature.impostazioni.service.ImpostazioniService;
import com.orodent.ecos.crm.feature.impostazioni.view.ImpostazioniView;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

import java.util.*;
import java.util.function.Consumer;

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

    public void operators(
            ImpostazioniView view,
            OperatorManagementService service,
            AsyncLoader tasks,
            Consumer<List<Operator>> updated) {
        var editor = view.operators();
        Consumer<List<Operator>> show =
                rows -> {
                    editor.show(rows);
                    updated.accept(rows);
                    tasks.markClean();
                };
        Consumer<Runnable> save =
                operation ->
                        tasks.run(
                                () -> {
                                    operation.run();
                                    return service.load();
                                },
                                rows -> {
                                    show.accept(rows);
                                    editor.feedback("Operatori aggiornati.", false);
                                },
                                error -> editor.feedback(CrmErrors.message(error), true));
        editor.onRename(
                request ->
                        save.accept(
                                () ->
                                        service.rename(
                                                request.id(),
                                                request.name(),
                                                request.expectedName())));
        editor.onActive((id, active) -> save.accept(() -> service.active(id, active)));
        editor.onAdd(
                () -> {
                    var dialog = new TextInputDialog();
                    dialog.setHeaderText("Nome del nuovo operatore");
                    if (view.getScene() != null) dialog.initOwner(view.getScene().getWindow());
                    dialog.showAndWait().ifPresent(name -> save.accept(() -> service.create(name)));
                });
        editor.onRemove(
                operator -> {
                    var dialog =
                            new Alert(
                                    Alert.AlertType.CONFIRMATION,
                                    operator.linked()
                                            ? "L'operatore è collegato a dati CRM: verrà"
                                                    + " disattivato mantenendo assegnazioni e"
                                                    + " storico. Proseguire?"
                                            : "Eliminare l'operatore " + operator.name() + "?",
                                    ButtonType.CANCEL,
                                    ButtonType.OK);
                    if (view.getScene() != null) dialog.initOwner(view.getScene().getWindow());
                    dialog.setHeaderText("Rimozione operatore");
                    if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
                        save.accept(() -> service.remove(operator.id()));
                });
        tasks.run(service::load, show, error -> editor.feedback(CrmErrors.message(error), true));
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
                                new Alert(Alert.AlertType.ERROR, CrmErrors.message(error))
                                        .showAndWait();
                            });
                });
        tasks.run(
                () -> service.getVoci(table, columns),
                editor::setVoci,
                error -> new Alert(Alert.AlertType.ERROR, CrmErrors.message(error)).showAndWait());
    }
}

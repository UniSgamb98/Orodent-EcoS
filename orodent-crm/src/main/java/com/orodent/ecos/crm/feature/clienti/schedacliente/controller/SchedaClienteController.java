package com.orodent.ecos.crm.feature.clienti.schedacliente.controller;

import com.orodent.ecos.crm.core.async.AsyncLoader;
import com.orodent.ecos.crm.feature.clienti.navigator.ClientiNav;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.*;
import com.orodent.ecos.crm.feature.clienti.schedacliente.service.SchedaClienteService;
import com.orodent.ecos.crm.feature.clienti.schedacliente.view.SchedaClienteView;

import javafx.scene.control.*;

import java.util.*;

/** One editable workspace; JDBC remains serialized on EcoS workers and saves commit together. */
public final class SchedaClienteController {
    private final SchedaClienteView view;
    private final SchedaClienteService service;
    private final AsyncLoader tasks;
    private final UUID id;

    public SchedaClienteController(
            SchedaClienteView view,
            ClientiNav nav,
            SchedaClienteService service,
            UUID id,
            AsyncLoader tasks) {
        this.view = view;
        this.service = service;
        this.tasks = tasks;
        this.id = id;
        tasks.onClose(view::disposeInline);
        load();
        view.getFavoriteButton().setOnAction(e -> view.toggleDraftFavorite());
        view.getSaveProfileEditButton().setId("crm-save-profile");
        view.getSaveProfileEditButton()
                .setOnAction(
                        e -> {
                            try {
                                WorkspaceDraft draft = view.captureForSave();
                                boolean forni = view.forniChanged(),
                                        fresatori = view.fresatoriChanged(),
                                        materiali = view.materialiChanged();
                                tasks.run(
                                        () ->
                                                service.saveWorkspace(
                                                        draft, forni, fresatori, materiali),
                                        p -> {
                                            view.renderInlineProfile(p);
                                            tasks.markClean();
                                        },
                                        this::error);
                            } catch (RuntimeException ex) {
                                error(ex);
                            }
                        });
        view.getCancelProfileEditButton()
                .setOnAction(
                        e -> {
                            Alert confirm =
                                    new Alert(
                                            Alert.AlertType.CONFIRMATION,
                                            "Vuoi scartare le modifiche non salvate?",
                                            ButtonType.CANCEL,
                                            ButtonType.OK);
                            confirm.setHeaderText("Modifiche alla scheda");
                            ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK))
                                    .setText("Scarta modifiche");
                            ((Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL))
                                    .setText("Continua a modificare");
                            if (view.getScene() != null)
                                confirm.initOwner(view.getScene().getWindow());
                            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
                                load();
                        });
        view.getNewNoteButton().setId("crm-new-note");
        view.getNewCallButton().setId("crm-new-call");
        view.getNewNoteButton().setOnAction(e -> view.addInlineActivity(InteractionType.NOTA));
        view.getNewCallButton().setOnAction(e -> view.addInlineActivity(InteractionType.CHIAMATA));
        view.getAllFilterButton().setOnAction(e -> view.filterInline(TimelineFilter.ALL));
        view.getNotesFilterButton().setOnAction(e -> view.filterInline(TimelineFilter.NOTES));
        view.getCallsFilterButton().setOnAction(e -> view.filterInline(TimelineFilter.CALLS));
    }

    private void load() {
        tasks.run(
                () ->
                        new Initial(
                                service.loadProfile(id),
                                service.getTipiCliente(),
                                service.getStatiTrattativa(),
                                service.getForniCatalog(),
                                service.getFresatoriCatalog(),
                                service.getMaterialiCatalog()),
                data -> {
                    view.setTipoClienteOptions(data.types());
                    view.setStatoTrattativaOptions(data.statuses());
                    view.setForniCatalog(data.forni());
                    view.setFresatoriCatalog(data.fresatori());
                    view.setMaterialiCatalog(data.materiali());
                    view.renderInlineProfile(data.profile());
                    tasks.markClean();
                },
                this::error);
    }

    private record Initial(
            ClienteProfile profile,
            List<String> types,
            List<String> statuses,
            List<FornoCatalogItem> forni,
            List<FresatoreCatalogItem> fresatori,
            List<MaterialeCatalogItem> materiali) {}

    private void error(Throwable error) {
        Alert alert =
                new Alert(
                        Alert.AlertType.ERROR,
                        com.orodent.ecos.crm.core.async.CrmErrors.message(error));
        alert.setHeaderText("Salvataggio CRM non riuscito");
        if (view.getScene() != null) alert.initOwner(view.getScene().getWindow());
        alert.showAndWait();
    }
}

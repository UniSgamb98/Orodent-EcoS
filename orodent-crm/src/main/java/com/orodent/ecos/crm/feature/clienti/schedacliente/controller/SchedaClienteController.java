package com.orodent.ecos.crm.feature.clienti.schedacliente.controller;

import com.orodent.ecos.crm.core.async.AsyncLoader;
import com.orodent.ecos.crm.core.database.model.CallOutcome;
import com.orodent.ecos.crm.feature.clienti.navigator.ClientiNav;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.*;
import com.orodent.ecos.crm.feature.clienti.schedacliente.service.SchedaClienteService;
import com.orodent.ecos.crm.feature.clienti.schedacliente.view.SchedaClienteView;

import javafx.scene.control.Alert;

import java.util.*;
import java.util.function.*;

/** Captures input on FX, executes complete operations on borrowed workers, renders only on FX. */
public final class SchedaClienteController {
    private final SchedaClienteView view;
    private final SchedaClienteService service;
    private final AsyncLoader tasks;
    private boolean call;

    public SchedaClienteController(
            SchedaClienteView view,
            ClientiNav nav,
            SchedaClienteService service,
            UUID id,
            AsyncLoader tasks) {
        this.view = view;
        this.service = service;
        this.tasks = tasks;
        tasks.run(() -> service.loadProfile(id), view::renderProfile, this::error);
        view.getFavoriteButton().setOnAction(e -> profile(service::toggleFavorite, false));
        view.getInvolvementSlider().valueProperty().addListener((o, b, a) -> involvement());
        view.getInvolvementSlider()
                .valueChangingProperty()
                .addListener(
                        (o, b, a) -> {
                            if (!a) involvement();
                        });
        view.getEditProfileButton().setId("crm-edit-profile");
        view.getEditProfileButton()
                .setOnAction(
                        e ->
                                tasks.run(
                                        () ->
                                                new EditData(
                                                        service.getTipiCliente(),
                                                        service.getStatiTrattativa(),
                                                        service.startEdit()),
                                        data -> {
                                            view.hideNoteEditor();
                                            view.setTipoClienteOptions(data.types());
                                            view.setStatoTrattativaOptions(data.statuses());
                                            view.renderEditableProfile(data.draft());
                                            lockEditors(true);
                                            tasks.markClean();
                                        },
                                        this::error));
        view.getSaveProfileEditButton().setId("crm-save-profile");
        view.getSaveProfileEditButton()
                .setOnAction(
                        e -> {
                            try {
                                var draft = view.collectEditDraft();
                                profile(() -> service.saveEdit(draft), true);
                            } catch (RuntimeException ex) {
                                error(ex);
                            }
                        });
        view.getCancelProfileEditButton().setOnAction(e -> profile(service::cancelEdit, true));
        view.getEditForniButton()
                .setOnAction(
                        e ->
                                tasks.run(
                                        () ->
                                                new ForniData(
                                                        service.getForniCatalog(),
                                                        service.startForniEdit()),
                                        data -> {
                                            view.setForniCatalog(data.catalogue());
                                            view.renderStandaloneForniEditor(data.rows());
                                            lockEditors(true);
                                            tasks.markClean();
                                        },
                                        this::error));
        view.getSaveForniButton()
                .setOnAction(
                        e -> {
                            var rows = view.collectForni();
                            save(() -> service.saveForniEdit(rows), view::renderForni);
                        });
        view.getCancelForniButton()
                .setOnAction(e -> save(service::cancelForniEdit, view::renderForni));
        view.getEditFresatoriButton()
                .setOnAction(
                        e ->
                                tasks.run(
                                        () ->
                                                new FresatoriData(
                                                        service.getFresatoriCatalog(),
                                                        service.startFresatoriEdit()),
                                        data -> {
                                            view.setFresatoriCatalog(data.catalogue());
                                            view.renderStandaloneFresatoriEditor(data.rows());
                                            lockEditors(true);
                                            tasks.markClean();
                                        },
                                        this::error));
        view.getSaveFresatoriButton()
                .setOnAction(
                        e -> {
                            var rows = view.collectFresatori();
                            save(() -> service.saveFresatoriEdit(rows), view::renderFresatori);
                        });
        view.getCancelFresatoriButton()
                .setOnAction(e -> save(service::cancelFresatoriEdit, view::renderFresatori));
        view.getEditMaterialiButton()
                .setOnAction(
                        e ->
                                tasks.run(
                                        () ->
                                                new MaterialiData(
                                                        service.getMaterialiCatalog(),
                                                        service.startMaterialiEdit()),
                                        data -> {
                                            view.setMaterialiCatalog(data.catalogue());
                                            view.renderStandaloneMaterialiEditor(data.rows());
                                            lockEditors(true);
                                            tasks.markClean();
                                        },
                                        this::error));
        view.getSaveMaterialiButton()
                .setOnAction(
                        e -> {
                            var rows = view.collectMateriali();
                            save(() -> service.saveMaterialiEdit(rows), view::renderMateriali);
                        });
        view.getCancelMaterialiButton()
                .setOnAction(e -> save(service::cancelMaterialiEdit, view::renderMateriali));
        view.getNewNoteButton().setId("crm-new-note");
        view.getNewCallButton().setId("crm-new-call");
        view.getSaveNoteButton().setId("crm-save-interaction");
        view.getNewNoteButton()
                .setOnAction(
                        e -> {
                            call = false;
                            view.getNoteTextArea().clear();
                            view.showNoteEditor();
                            lockEditors(true);
                            tasks.markClean();
                        });
        view.getNewCallButton()
                .setOnAction(
                        e -> {
                            call = true;
                            view.getNoteTextArea().clear();
                            view.showCallEditor();
                            lockEditors(true);
                            tasks.markClean();
                        });
        view.getCancelNoteButton()
                .setOnAction(
                        e -> {
                            view.hideNoteEditor();
                            lockEditors(false);
                            tasks.markClean();
                        });
        view.getSaveNoteButton()
                .setOnAction(
                        e -> {
                            String text = view.getNoteTextArea().getText();
                            var date = view.getNextCallDatePicker().getValue();
                            CallOutcome outcome = view.getCallOutcomeChoiceBox().getValue();
                            boolean isCall = call;
                            if (isCall && outcome == null) {
                                error(
                                        new IllegalArgumentException(
                                                "Seleziona un esito per la chiamata."));
                                return;
                            }
                            tasks.run(
                                    () ->
                                            isCall
                                                    ? service.addChiamata(text, date, outcome)
                                                    : service.addNota(text),
                                    data -> {
                                        view.renderProfile(data);
                                        view.hideNoteEditor();
                                        lockEditors(false);
                                        tasks.markClean();
                                    },
                                    this::error);
                        });
        view.getAllFilterButton().setOnAction(e -> filter(TimelineFilter.ALL));
        view.getNotesFilterButton().setOnAction(e -> filter(TimelineFilter.NOTES));
        view.getCallsFilterButton().setOnAction(e -> filter(TimelineFilter.CALLS));
    }

    private void filter(TimelineFilter filter) {
        tasks.run(
                () -> service.setTimelineFilter(filter),
                p -> {
                    view.setActiveTimelineFilter(filter);
                    view.renderProfile(p);
                },
                this::error);
    }

    private void involvement() {
        if (view.isUpdatingInvolvementSlider()
                || view.getInvolvementSlider().isValueChanging()
                || tasks.busy()) return;
        int value = view.involvementSliderValue();
        profile(() -> service.updateCoinvolgimento(value), false);
    }

    private void profile(Supplier<ClienteProfile> operation, boolean clean) {
        tasks.run(
                operation,
                p -> {
                    view.renderProfile(p);
                    lockEditors(false);
                    if (clean) tasks.markClean();
                },
                this::error);
    }

    private <T> void save(Supplier<T> operation, Consumer<T> render) {
        tasks.run(
                operation,
                data -> {
                    render.accept(data);
                    lockEditors(false);
                    tasks.markClean();
                },
                this::error);
    }

    private void lockEditors(boolean locked) {
        view.getEditProfileButton().setDisable(locked);
        view.getEditForniButton().setDisable(locked);
        view.getEditFresatoriButton().setDisable(locked);
        view.getEditMaterialiButton().setDisable(locked);
        view.getNewNoteButton().setDisable(locked);
        view.getNewCallButton().setDisable(locked);
        view.getAllFilterButton().setDisable(locked);
        view.getNotesFilterButton().setDisable(locked);
        view.getCallsFilterButton().setDisable(locked);
        view.getInvolvementSlider().setDisable(locked);
        view.getFavoriteButton().setDisable(locked);
    }

    private void error(Throwable error) {
        Alert alert =
                new Alert(
                        Alert.AlertType.ERROR,
                        com.orodent.ecos.crm.core.async.CrmErrors.message(error));
        alert.setHeaderText("Operazione CRM non riuscita");
        if (view.getScene() != null) alert.initOwner(view.getScene().getWindow());
        alert.showAndWait();
    }

    private record EditData(List<String> types, List<String> statuses, EditProfileDraft draft) {}

    private record ForniData(List<FornoCatalogItem> catalogue, List<FornoClienteEditInput> rows) {}

    private record FresatoriData(
            List<FresatoreCatalogItem> catalogue, List<FresatoreClienteEditInput> rows) {}

    private record MaterialiData(
            List<MaterialeCatalogItem> catalogue, List<MaterialeClienteEditInput> rows) {}
}

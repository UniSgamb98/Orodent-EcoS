package com.orodent.ecos.crm.feature.clienti.clienti.controller;

import com.orodent.ecos.crm.core.database.model.VistaSalvata;
import com.orodent.ecos.crm.core.database.service.CurrentOperatoreService;
import com.orodent.ecos.crm.core.database.service.VistaSalvataService;
import com.orodent.ecos.crm.core.session.FeatureKey;
import com.orodent.ecos.crm.core.session.FeatureSessionStateStore;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientePreview;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientePreviewRow;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientiPage;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientiSearchRequest;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientiSearchState;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientiSessionState;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientiViewState;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.OperatoreFilter;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.SortColumn;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.TextFilter;
import com.orodent.ecos.crm.feature.clienti.clienti.service.ClientiService;
import com.orodent.ecos.crm.feature.clienti.clienti.service.ClientiViewStateCodec;
import com.orodent.ecos.crm.feature.clienti.clienti.view.ClientiFeedback;
import com.orodent.ecos.crm.feature.clienti.clienti.view.ClientiView;
import com.orodent.ecos.crm.feature.clienti.navigator.ClientiNav;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ClientiController {

    private static final int INITIAL_LOAD_SIZE = 30;
    private static final int LOAD_MORE_SIZE = 50;
    private static final Duration SEARCH_DEBOUNCE = Duration.millis(300);

    private final com.orodent.ecos.crm.core.async.AsyncLoader tasks;
    private final ClientiView view;
    private final ClientiNav clientiNav;
    private final ClientiService service;
    private final ClientiFeedback feedback;
    private final FeatureSessionStateStore sessionStateStore;
    private final CurrentOperatoreService currentOperatoreService;
    private final VistaSalvataService vistaSalvataService;
    private final ClientiViewStateCodec viewStateCodec;
    private final PauseTransition searchDebounce = new PauseTransition(SEARCH_DEBOUNCE);
    private ClientiSearchState searchState = ClientiSearchState.initial(INITIAL_LOAD_SIZE);
    private long loadVersion;
    private boolean clearingFilters;
    private boolean loadingPage;
    private boolean hasNextPage;
    private int loadedRows;
    private boolean restoringFilters;
    private int pendingFilterLoads;
    private boolean sessionStateAvailable;
    private boolean savedSearchesLoaded;
    private boolean initialRestoreCompleted;
    private ClientiViewState defaultSavedState;
    private VistaSalvata activeSavedView;
    private ClientiViewState savedBaseline;

    public ClientiController(
            ClientiView view,
            com.orodent.ecos.crm.core.async.AsyncLoader tasks,
            ClientiNav clientiNav,
            ClientiService service,
            FeatureSessionStateStore sessionStateStore,
            CurrentOperatoreService currentOperatoreService,
            VistaSalvataService vistaSalvataService,
            ClientiViewStateCodec viewStateCodec) {
        this.view = view;
        this.tasks = tasks;
        this.clientiNav = clientiNav;
        this.service = service;
        this.sessionStateStore = sessionStateStore;
        this.currentOperatoreService = currentOperatoreService;
        this.vistaSalvataService = vistaSalvataService;
        this.viewStateCodec = viewStateCodec;
        this.feedback = new ClientiFeedback();
        configureActions();
    }

    private void configureActions() {
        view.onNewClient(clientiNav::showNuovoCliente);
        view.onSortRequested(this::sortClienti);
        view.onSearchChanged(this::searchClienti);
        view.onOperatoreFilterChanged(this::filterByOperatore);
        view.onTipologiaFilterChanged(this::filterByTipoCliente);
        view.onStatoFilterChanged(this::filterByStatoTrattativa);
        view.onClearFilters(this::clearFilters);
        view.onSaveSearch(this::saveCurrentSearch);
        view.onApplySavedSearch(this::applySavedSearch);
        view.onUpdateSavedSearch(this::updateSavedSearch);
        view.onRenameSavedSearch(this::renameSavedSearch);
        view.onSetDefaultSavedSearch(this::setDefaultSavedSearch);
        view.onDeleteSavedSearch(this::deleteSavedSearch);
        view.onScrollNearBottom(this::loadNextPage);
    }

    public void loadPreviewClientsAsync() {
        Optional<ClientiSessionState> sessionState =
                sessionStateStore.find(
                        currentOperatoreService.currentOperatoreId(),
                        FeatureKey.CLIENTI,
                        ClientiSessionState.class);
        sessionStateAvailable = sessionState.isPresent();
        ClientiViewState savedState =
                sessionState
                        .map(ClientiSessionState::viewState)
                        .orElseGet(ClientiViewState::initial);
        searchState = savedState.toSearchState(INITIAL_LOAD_SIZE);
        savedBaseline = sessionState.map(ClientiSessionState::savedBaseline).orElse(null);
        restoringFilters = true;
        pendingFilterLoads = 3;
        loadFiltersAsync();
        loadSavedSearchesAsync();
    }

    private void loadFiltersAsync() {
        tasks.run(
                service::getOperatorFilters,
                operators -> completeFilterLoad(() -> view.setOperatorFilters(operators)),
                error -> completeFilterLoad(() -> view.setOperatorFilters(List.of())));
        tasks.run(
                service::getTipoClienteFilters,
                types -> completeFilterLoad(() -> view.setTypeFilters(types)),
                error -> completeFilterLoad(() -> view.setTypeFilters(List.of())));
        tasks.run(
                service::getStatoTrattativaFilters,
                statuses -> completeFilterLoad(() -> view.setStatusFilters(statuses)),
                error -> completeFilterLoad(() -> view.setStatusFilters(List.of())));
    }

    private void completeFilterLoad(Runnable updateFilterOptions) {
        updateFilterOptions.run();
        pendingFilterLoads--;
        completeInitialRestoreWhenReady();
    }

    private void searchClienti(String searchText) {
        if (clearingFilters || restoringFilters) {
            return;
        }
        searchState = searchState.withSearchText(searchText);
        rememberSearchState();
        updateUnsavedChangesIndicator();
        searchDebounce.stop();
        searchDebounce.setOnFinished(event -> reloadClients());
        searchDebounce.playFromStart();
    }

    private void filterByOperatore(OperatoreFilter operatoreFilter) {
        if (clearingFilters || restoringFilters) {
            return;
        }
        searchState = searchState.withOperatore(operatoreFilter);
        rememberSearchState();
        updateUnsavedChangesIndicator();
        reloadClients();
    }

    private void filterByTipoCliente(TextFilter filter) {
        if (clearingFilters || restoringFilters) {
            return;
        }
        searchState = searchState.withTipologia(filter);
        rememberSearchState();
        updateUnsavedChangesIndicator();
        reloadClients();
    }

    private void filterByStatoTrattativa(TextFilter filter) {
        if (clearingFilters || restoringFilters) {
            return;
        }
        searchState = searchState.withStato(filter);
        rememberSearchState();
        updateUnsavedChangesIndicator();
        reloadClients();
    }

    private void clearFilters() {
        clearingFilters = true;
        searchDebounce.stop();
        view.clearFilters();
        searchState = ClientiSearchState.initial(INITIAL_LOAD_SIZE);
        activeSavedView = null;
        savedBaseline = null;
        view.selectSavedSearch(null);
        clearingFilters = false;
        rememberSearchState();
        updateUnsavedChangesIndicator();
        reloadClients();
    }

    private void loadSavedSearchesAsync() {
        tasks.run(
                () -> vistaSalvataService.findAll(FeatureKey.CLIENTI),
                this::handleSavedSearchesLoaded,
                error -> handleSavedSearchesLoaded(List.of()));
    }

    private void handleSavedSearchesLoaded(List<VistaSalvata> savedSearches) {
        view.setSavedSearches(savedSearches);
        restoreActiveSavedView(savedSearches);
        if (!initialRestoreCompleted && !sessionStateAvailable) {
            savedSearches.stream()
                    .filter(VistaSalvata::predefinita)
                    .findFirst()
                    .ifPresent(
                            savedView -> {
                                activeSavedView = savedView;
                                defaultSavedState = decodeOrDefault(savedView);
                                savedBaseline = defaultSavedState;
                            });
        }
        savedSearchesLoaded = true;
        completeInitialRestoreWhenReady();
    }

    private ClientiViewState decodeOrDefault(VistaSalvata savedView) {
        try {
            return viewStateCodec.decode(savedView.payload());
        } catch (RuntimeException e) {
            return ClientiViewState.initial();
        }
    }

    private void completeInitialRestoreWhenReady() {
        if (initialRestoreCompleted
                || pendingFilterLoads != 0
                || (!sessionStateAvailable && !savedSearchesLoaded)) {
            return;
        }
        ClientiViewState stateToRestore =
                defaultSavedState == null ? ClientiViewState.from(searchState) : defaultSavedState;
        ClientiViewState restoredState = view.applySearchState(stateToRestore);
        searchState = restoredState.toSearchState(INITIAL_LOAD_SIZE);
        restoringFilters = false;
        initialRestoreCompleted = true;
        view.selectSavedSearch(activeSavedView);
        updateUnsavedChangesIndicator();
        rememberSearchState();
        reloadClients();
    }

    private void saveCurrentSearch() {
        feedback.requestSaveSearch()
                .ifPresent(
                        request -> {
                            ClientiViewState stateToSave = ClientiViewState.from(searchState);
                            view.setSaveSearchDisabled(true);
                            tasks.run(
                                    () ->
                                            vistaSalvataService.create(
                                                    FeatureKey.CLIENTI,
                                                    request.name(),
                                                    viewStateCodec.encode(stateToSave),
                                                    request.predefinita()),
                                    savedView -> {
                                        view.setSaveSearchDisabled(false);
                                        activeSavedView = savedView;
                                        savedBaseline = stateToSave;
                                        view.selectSavedSearch(savedView);
                                        updateUnsavedChangesIndicator();
                                        rememberSearchState();
                                        loadSavedSearchesAsync();
                                        feedback.showSearchSaved(savedView.nome());
                                    },
                                    error -> {
                                        view.setSaveSearchDisabled(false);
                                        feedback.showError(safeMessage(error));
                                    });
                        });
    }

    private void applySavedSearch(VistaSalvata savedView) {
        if (savedView == null || restoringFilters) {
            return;
        }
        try {
            ClientiViewState decodedState = viewStateCodec.decode(savedView.payload());
            restoringFilters = true;
            ClientiViewState appliedState = view.applySearchState(decodedState);
            searchState = appliedState.toSearchState(INITIAL_LOAD_SIZE);
            restoringFilters = false;
            activeSavedView = savedView;
            savedBaseline = appliedState;
            view.selectSavedSearch(savedView);
            updateUnsavedChangesIndicator();
            rememberSearchState();
            reloadClients();
        } catch (RuntimeException e) {
            restoringFilters = false;
            feedback.showError(safeMessage(e));
        }
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? "Operazione non riuscita."
                : error.getMessage();
    }

    private void updateSavedSearch(VistaSalvata savedView) {
        if (savedView == null) {
            return;
        }
        ClientiViewState currentState = ClientiViewState.from(searchState);
        tasks.run(
                () ->
                        vistaSalvataService.update(
                                savedView.id(),
                                savedView.nome(),
                                viewStateCodec.encode(currentState),
                                savedView.predefinita()),
                updatedView -> {
                    activeSavedView = updatedView;
                    savedBaseline = currentState;
                    view.selectSavedSearch(updatedView);
                    updateUnsavedChangesIndicator();
                    rememberSearchState();
                    loadSavedSearchesAsync();
                    feedback.showOperationCompleted("La ricerca è stata aggiornata.");
                },
                error -> feedback.showError(safeMessage(error)));
    }

    private void renameSavedSearch(VistaSalvata savedView) {
        if (savedView == null) {
            return;
        }
        feedback.requestRename(savedView)
                .ifPresent(
                        newName ->
                                tasks.run(
                                        () ->
                                                vistaSalvataService.update(
                                                        savedView.id(),
                                                        newName,
                                                        savedView.payload(),
                                                        savedView.predefinita()),
                                        renamedView -> {
                                            if (isActive(renamedView.id())) {
                                                activeSavedView = renamedView;
                                            }
                                            view.selectSavedSearch(renamedView);
                                            rememberSearchState();
                                            loadSavedSearchesAsync();
                                            feedback.showOperationCompleted(
                                                    "La ricerca è stata rinominata.");
                                        },
                                        error -> feedback.showError(safeMessage(error))));
    }

    private void setDefaultSavedSearch(VistaSalvata savedView) {
        if (savedView == null) {
            return;
        }
        tasks.run(
                () -> {
                    vistaSalvataService.setPredefinita(savedView.id());
                    return savedView.id();
                },
                savedViewId -> {
                    loadSavedSearchesAsync();
                    feedback.showOperationCompleted("La ricerca è ora quella predefinita.");
                },
                error -> feedback.showError(safeMessage(error)));
    }

    private void deleteSavedSearch(VistaSalvata savedView) {
        if (savedView == null || !feedback.confirmDelete(savedView)) {
            return;
        }
        tasks.run(
                () -> {
                    vistaSalvataService.delete(savedView.id());
                    return savedView.id();
                },
                deletedId -> {
                    if (isActive(deletedId)) {
                        activeSavedView = null;
                        savedBaseline = null;
                        updateUnsavedChangesIndicator();
                        rememberSearchState();
                    }
                    view.selectSavedSearch(null);
                    loadSavedSearchesAsync();
                    feedback.showOperationCompleted("La ricerca è stata eliminata.");
                },
                error -> feedback.showError(safeMessage(error)));
    }

    private void restoreActiveSavedView(List<VistaSalvata> savedSearches) {
        UUID activeId = activeSavedView == null ? sessionActiveSavedViewId() : activeSavedView.id();
        if (activeId == null) {
            return;
        }
        activeSavedView =
                savedSearches.stream()
                        .filter(savedView -> savedView.id().equals(activeId))
                        .findFirst()
                        .orElse(null);
        if (activeSavedView == null) {
            savedBaseline = null;
        } else if (!initialRestoreCompleted) {
            view.selectSavedSearch(activeSavedView);
        }
        updateUnsavedChangesIndicator();
    }

    private UUID sessionActiveSavedViewId() {
        return sessionStateStore
                .find(
                        currentOperatoreService.currentOperatoreId(),
                        FeatureKey.CLIENTI,
                        ClientiSessionState.class)
                .map(ClientiSessionState::activeSavedViewId)
                .orElse(null);
    }

    private boolean isActive(UUID savedViewId) {
        return activeSavedView != null && activeSavedView.id().equals(savedViewId);
    }

    private void updateUnsavedChangesIndicator() {
        boolean unsavedChanges =
                activeSavedView != null
                        && savedBaseline != null
                        && !savedBaseline.equals(ClientiViewState.from(searchState));
        view.setUnsavedChangesVisible(unsavedChanges);
    }

    private void sortClienti(SortColumn sortColumn) {
        searchState = searchState.togglingSort(sortColumn);
        rememberSearchState();
        updateUnsavedChangesIndicator();
        reloadClients();
    }

    private void rememberSearchState() {
        sessionStateStore.save(
                currentOperatoreService.currentOperatoreId(),
                FeatureKey.CLIENTI,
                new ClientiSessionState(
                        ClientiViewState.from(searchState),
                        activeSavedView == null ? null : activeSavedView.id(),
                        savedBaseline));
    }

    private void reloadClients() {
        hasNextPage = false;
        loadedRows = 0;
        searchState = searchState.withPageSize(INITIAL_LOAD_SIZE);
        loadPage(0, INITIAL_LOAD_SIZE, false);
    }

    private void loadNextPage() {
        if (hasNextPage && !loadingPage) {
            loadPage(loadedRows, LOAD_MORE_SIZE, true);
        }
    }

    private void loadPage(int offset, int pageSize, boolean append) {
        if (append && (!hasNextPage || loadingPage)) {
            return;
        }
        searchState = searchState.withPageSize(pageSize).withOffset(offset);
        ClientiSearchRequest request = searchState.toRequest();
        long version = ++loadVersion;
        loadingPage = true;
        if (append) {
            view.showLoadingMore();
        } else {
            view.showLoading();
        }

        tasks.run(
                () -> service.getClientiPreview(request),
                clientiPage -> {
                    if (version == loadVersion) {
                        renderClienti(clientiPage, append);
                        loadingPage = false;
                    }
                },
                error -> {
                    if (version == loadVersion) {
                        loadingPage = false;
                        view.showError("Caricamento clienti non riuscito.");
                    }
                });
    }

    private void renderClienti(ClientiPage page, boolean append) {
        searchState = searchState.withOffset(page.offset());
        hasNextPage = page.hasNextPage();
        view.setResultsCount(page.totalRows());

        if (page.rows().isEmpty() && !append) {
            view.showEmpty();
            return;
        }

        if (!append) {
            view.clearClientRows();
            loadedRows = 0;
        }

        for (ClientePreviewRow cliente : page.rows()) {
            ClientePreview preview = cliente.preview();
            var row =
                    view.addClientRow(
                            preview.name(),
                            preview.type(),
                            preview.contact(),
                            preview.operator(),
                            preview.status(),
                            preview.lastContact(),
                            this::showRowActionsUnavailable);
            row.setOnMouseClicked(
                    event -> {
                        if (event.getClickCount() == 2) {
                            clientiNav.showSchedaCliente(cliente.clienteId());
                            return;
                        }
                        view.openClientDetails(
                                preview,
                                row,
                                () -> clientiNav.showSchedaCliente(cliente.clienteId()));
                    });
        }
        loadedRows += page.rows().size();

        if (hasNextPage) {
            view.showLoadMoreAvailable();
        } else {
            view.showAllResultsLoaded();
        }
    }

    private void showRowActionsUnavailable() {
        feedback.showFeatureInDevelopment("Azioni cliente");
    }

    public void dispose() {
        searchDebounce.stop();
    }

    public ClientiView getView() {
        return view;
    }

    public ClientiNav getClientiNav() {
        return clientiNav;
    }

    public ClientiService getService() {
        return service;
    }
}

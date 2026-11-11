package com.orodent.ecos.crm.feature.clienti.clienti.dto;

import java.util.UUID;

public record ClientiSessionState(
        ClientiViewState viewState, UUID activeSavedViewId, ClientiViewState savedBaseline) {
    public ClientiSessionState {
        viewState = viewState == null ? ClientiViewState.initial() : viewState;
    }
}

package com.orodent.ecos.crm.core.database.service;

import com.orodent.ecos.module.OperatorSession;

import java.util.UUID;

/** Borrows the application session, never stores a global/static operator. */
public final class CurrentOperatoreService {
    private final OperatorSession session;

    public CurrentOperatoreService(OperatorSession session) {
        this.session = session;
    }

    public UUID currentOperatoreId() {
        return session.current()
                .orElseThrow(() -> new IllegalStateException("Seleziona l'operatore CRM."))
                .id();
    }

    public String currentUsername() {
        return session.current().orElseThrow().username();
    }
}

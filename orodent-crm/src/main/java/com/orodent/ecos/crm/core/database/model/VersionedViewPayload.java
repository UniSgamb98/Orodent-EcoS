package com.orodent.ecos.crm.core.database.model;

public record VersionedViewPayload(int version, String configuration) {

    public VersionedViewPayload {
        if (version < 1) {
            throw new IllegalArgumentException(
                    "La versione della configurazione deve essere almeno 1.");
        }
        if (configuration == null || configuration.isBlank()) {
            throw new IllegalArgumentException(
                    "La configurazione della vista non può essere vuota.");
        }
    }
}

package com.orodent.ecos.crm.core.database.model;

import com.orodent.ecos.crm.core.session.FeatureKey;

import java.time.LocalDateTime;
import java.util.UUID;

public record VistaSalvata(
        UUID id,
        UUID operatoreId,
        FeatureKey feature,
        String nome,
        VersionedViewPayload payload,
        boolean predefinita,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}

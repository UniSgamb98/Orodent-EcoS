package com.orodent.ecos.crm.core.database.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record Operatore(
        UUID id,
        String nome,
        String cognome,
        String username,
        boolean attivo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}

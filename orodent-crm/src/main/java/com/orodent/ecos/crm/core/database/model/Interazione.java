package com.orodent.ecos.crm.core.database.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record Interazione(
        UUID id,
        UUID clienteId,
        UUID operatoreId,
        UUID notaId,
        LocalDate dataContatto,
        LocalDate prossimoContatto,
        CallOutcome esito,
        BigDecimal coinvolgimento,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}

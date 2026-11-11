package com.orodent.ecos.crm.feature.calendario.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CalendarioCall(
        UUID interazioneId,
        UUID clienteId,
        String cliente,
        UUID operatoreId,
        String operatore,
        LocalDate data) {}

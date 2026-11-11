package com.orodent.ecos.crm.core.database.query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CalendarioQuery {

    List<CalendarioCallRecord> findProssimeChiamate(
            LocalDate from, LocalDate to, UUID clienteOperatoreId);

    record CalendarioCallRecord(
            UUID interazioneId,
            UUID clienteId,
            String ragioneSociale,
            UUID operatoreId,
            String operatore,
            LocalDate prossimoContatto) {}
}

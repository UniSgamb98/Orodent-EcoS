package com.orodent.ecos.crm.core.database.query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ClientiPreviewQuery {
    ClientePreviewPage findPage(
            int offset,
            int pageSize,
            String searchText,
            UUID operatoreId,
            String tipoCliente,
            String statoTrattativa,
            String orderByColumn,
            boolean ascending);

    record ClientePreviewPage(
            List<ClientePreviewRecord> records, int offset, int pageSize, long totalRows) {
        public ClientePreviewPage {
            records = List.copyOf(records);
        }
    }

    record ClientePreviewRecord(
            UUID clienteId,
            String ragioneSociale,
            String tipoCliente,
            String referente,
            String indirizzo,
            String operatore,
            String statoTrattativa,
            LocalDate ultimoContatto) {}
}

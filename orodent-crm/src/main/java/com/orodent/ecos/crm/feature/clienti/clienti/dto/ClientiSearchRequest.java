package com.orodent.ecos.crm.feature.clienti.clienti.dto;

import java.util.UUID;

public record ClientiSearchRequest(
        int offset,
        int pageSize,
        String searchText,
        UUID operatoreId,
        String tipoCliente,
        String statoTrattativa,
        String paese,
        SortColumn sortColumn,
        boolean ascending) {
    public ClientiSearchRequest {
        offset = Math.max(0, offset);
        pageSize = Math.max(1, pageSize);
        searchText = searchText == null ? "" : searchText.trim();
        tipoCliente = tipoCliente == null ? "" : tipoCliente.trim();
        statoTrattativa = statoTrattativa == null ? "" : statoTrattativa.trim();
        paese = paese == null ? "" : paese.trim();
        sortColumn = sortColumn == null ? SortColumn.NAME : sortColumn;
    }
}

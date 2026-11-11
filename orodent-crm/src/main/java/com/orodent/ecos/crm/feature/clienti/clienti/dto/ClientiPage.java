package com.orodent.ecos.crm.feature.clienti.clienti.dto;

import java.util.List;

public record ClientiPage(List<ClientePreviewRow> rows, int offset, int pageSize, long totalRows) {
    public ClientiPage {
        rows = List.copyOf(rows);
    }

    public boolean hasNextPage() {
        return offset + rows.size() < totalRows;
    }
}

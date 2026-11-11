package com.orodent.ecos.crm.feature.clienti.clienti.dto;

public record ClientiSearchState(
        int offset,
        int pageSize,
        String searchText,
        OperatoreFilter operatore,
        TextFilter tipologia,
        TextFilter stato,
        SortColumn sortColumn,
        boolean ascending) {
    public ClientiSearchState {
        offset = Math.max(0, offset);
        pageSize = Math.max(1, pageSize);
        searchText = searchText == null ? "" : searchText.trim();
        operatore = operatore == null ? OperatoreFilter.empty() : operatore;
        tipologia = tipologia == null ? TextFilter.empty("Tutti") : tipologia;
        stato = stato == null ? TextFilter.empty("Tutti") : stato;
        sortColumn = sortColumn == null ? SortColumn.NAME : sortColumn;
    }

    public static ClientiSearchState initial(int pageSize) {
        return new ClientiSearchState(
                0,
                pageSize,
                "",
                OperatoreFilter.empty(),
                TextFilter.empty("Tutti"),
                TextFilter.empty("Tutti"),
                SortColumn.NAME,
                true);
    }

    public ClientiSearchState withOffset(int offset) {
        return new ClientiSearchState(
                offset, pageSize, searchText, operatore, tipologia, stato, sortColumn, ascending);
    }

    public ClientiSearchState withPageSize(int pageSize) {
        return new ClientiSearchState(
                0, pageSize, searchText, operatore, tipologia, stato, sortColumn, ascending);
    }

    public ClientiSearchState withSearchText(String searchText) {
        return new ClientiSearchState(
                0, pageSize, searchText, operatore, tipologia, stato, sortColumn, ascending);
    }

    public ClientiSearchState withOperatore(OperatoreFilter operatore) {
        return new ClientiSearchState(
                0, pageSize, searchText, operatore, tipologia, stato, sortColumn, ascending);
    }

    public ClientiSearchState withTipologia(TextFilter tipologia) {
        return new ClientiSearchState(
                0, pageSize, searchText, operatore, tipologia, stato, sortColumn, ascending);
    }

    public ClientiSearchState withStato(TextFilter stato) {
        return new ClientiSearchState(
                0, pageSize, searchText, operatore, tipologia, stato, sortColumn, ascending);
    }

    public ClientiSearchState togglingSort(SortColumn column) {
        return new ClientiSearchState(
                0,
                pageSize,
                searchText,
                operatore,
                tipologia,
                stato,
                column,
                column == sortColumn ? !ascending : true);
    }

    public ClientiSearchRequest toRequest() {
        return new ClientiSearchRequest(
                offset,
                pageSize,
                searchText,
                operatore.id(),
                value(tipologia),
                value(stato),
                sortColumn,
                ascending);
    }

    private String value(TextFilter filter) {
        return filter.isEmptyOption() ? "" : filter.value();
    }
}

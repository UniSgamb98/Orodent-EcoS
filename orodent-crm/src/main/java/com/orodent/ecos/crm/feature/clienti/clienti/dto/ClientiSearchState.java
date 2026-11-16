package com.orodent.ecos.crm.feature.clienti.clienti.dto;

public record ClientiSearchState(
        int offset,
        int pageSize,
        String searchText,
        OperatoreFilter operatore,
        TextFilter tipologia,
        TextFilter stato,
        TextFilter paese,
        SortColumn sortColumn,
        boolean ascending,
        boolean myFavoritesOnly,
        boolean overdueOnly) {
    public ClientiSearchState {
        offset = Math.max(0, offset);
        pageSize = Math.max(1, pageSize);
        searchText = searchText == null ? "" : searchText.trim();
        operatore = operatore == null ? OperatoreFilter.empty() : operatore;
        tipologia = tipologia == null ? TextFilter.empty("Tutti") : tipologia;
        stato = stato == null ? TextFilter.empty("Tutti") : stato;
        paese =
                paese == null
                        ? TextFilter.empty("Tutti i paesi")
                        : new TextFilter(
                                paese.value(),
                                com.orodent.ecos.crm.core.country.Countries.filterLabel(
                                        paese.value()));
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
                TextFilter.empty("Tutti i paesi"),
                SortColumn.NAME,
                true,
                false,
                false);
    }

    public ClientiSearchState withOffset(int offset) {
        return new ClientiSearchState(
                offset,
                pageSize,
                searchText,
                operatore,
                tipologia,
                stato,
                paese,
                sortColumn,
                ascending,
                myFavoritesOnly,
                overdueOnly);
    }

    public ClientiSearchState withPageSize(int pageSize) {
        return new ClientiSearchState(
                0,
                pageSize,
                searchText,
                operatore,
                tipologia,
                stato,
                paese,
                sortColumn,
                ascending,
                myFavoritesOnly,
                overdueOnly);
    }

    public ClientiSearchState withSearchText(String searchText) {
        return new ClientiSearchState(
                0,
                pageSize,
                searchText,
                operatore,
                tipologia,
                stato,
                paese,
                sortColumn,
                ascending,
                myFavoritesOnly,
                overdueOnly);
    }

    public ClientiSearchState withOperatore(OperatoreFilter operatore) {
        return new ClientiSearchState(
                0,
                pageSize,
                searchText,
                operatore,
                tipologia,
                stato,
                paese,
                sortColumn,
                ascending,
                myFavoritesOnly,
                overdueOnly);
    }

    public ClientiSearchState withTipologia(TextFilter tipologia) {
        return new ClientiSearchState(
                0,
                pageSize,
                searchText,
                operatore,
                tipologia,
                stato,
                paese,
                sortColumn,
                ascending,
                myFavoritesOnly,
                overdueOnly);
    }

    public ClientiSearchState withStato(TextFilter stato) {
        return new ClientiSearchState(
                0,
                pageSize,
                searchText,
                operatore,
                tipologia,
                stato,
                paese,
                sortColumn,
                ascending,
                myFavoritesOnly,
                overdueOnly);
    }

    public ClientiSearchState withPaese(TextFilter paese) {
        return new ClientiSearchState(
                0,
                pageSize,
                searchText,
                operatore,
                tipologia,
                stato,
                paese,
                sortColumn,
                ascending,
                myFavoritesOnly,
                overdueOnly);
    }

    public ClientiSearchState withQuickFilters(boolean myFavoritesOnly, boolean overdueOnly) {
        return new ClientiSearchState(
                0,
                pageSize,
                searchText,
                operatore,
                tipologia,
                stato,
                paese,
                sortColumn,
                ascending,
                myFavoritesOnly,
                overdueOnly);
    }

    public ClientiSearchState togglingSort(SortColumn column) {
        return new ClientiSearchState(
                0,
                pageSize,
                searchText,
                operatore,
                tipologia,
                stato,
                paese,
                column,
                column == sortColumn ? !ascending : true,
                myFavoritesOnly,
                overdueOnly);
    }

    public ClientiSearchRequest toRequest() {
        return new ClientiSearchRequest(
                offset,
                pageSize,
                searchText,
                operatore.id(),
                value(tipologia),
                value(stato),
                value(paese),
                sortColumn,
                ascending,
                myFavoritesOnly,
                overdueOnly);
    }

    private String value(TextFilter filter) {
        return filter.isEmptyOption() ? "" : filter.value();
    }
}

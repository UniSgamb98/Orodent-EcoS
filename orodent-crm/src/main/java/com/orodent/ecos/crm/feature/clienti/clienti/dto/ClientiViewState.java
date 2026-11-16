package com.orodent.ecos.crm.feature.clienti.clienti.dto;

public record ClientiViewState(
        String searchText,
        OperatoreFilter operatore,
        TextFilter tipologia,
        TextFilter stato,
        TextFilter paese,
        SortColumn sortColumn,
        boolean ascending) {
    public ClientiViewState {
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

    public static ClientiViewState initial() {
        return new ClientiViewState(
                "",
                OperatoreFilter.empty(),
                TextFilter.empty("Tutti"),
                TextFilter.empty("Tutti"),
                TextFilter.empty("Tutti i paesi"),
                SortColumn.NAME,
                true);
    }

    public static ClientiViewState from(ClientiSearchState state) {
        return new ClientiViewState(
                state.searchText(),
                state.operatore(),
                state.tipologia(),
                state.stato(),
                state.paese(),
                state.sortColumn(),
                state.ascending());
    }

    public ClientiSearchState toSearchState(int pageSize) {
        return new ClientiSearchState(
                0, pageSize, searchText, operatore, tipologia, stato, paese, sortColumn, ascending);
    }
}

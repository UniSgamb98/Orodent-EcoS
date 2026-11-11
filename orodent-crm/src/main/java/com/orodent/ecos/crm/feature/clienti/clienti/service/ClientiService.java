package com.orodent.ecos.crm.feature.clienti.clienti.service;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.query.ClientiFilterQuery;
import com.orodent.ecos.crm.core.database.query.ClientiPreviewQuery;
import com.orodent.ecos.crm.core.database.query.derby.DerbyClientiFilterQuery;
import com.orodent.ecos.crm.core.database.query.derby.DerbyClientiPreviewQuery;
import com.orodent.ecos.crm.core.database.query.result.OperatoreClienteFilterResult;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientePreview;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientePreviewRow;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientiPage;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientiSearchRequest;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.OperatoreFilter;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.TextFilter;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class ClientiService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ClientiPreviewQuery clientiPreviewQuery;
    private final ClientiFilterQuery clientiFilterQuery;

    public ClientiService(Database database) {
        this(new DerbyClientiPreviewQuery(database), new DerbyClientiFilterQuery(database));
    }

    public ClientiService(
            ClientiPreviewQuery clientiPreviewQuery, ClientiFilterQuery clientiFilterQuery) {
        this.clientiPreviewQuery = clientiPreviewQuery;
        this.clientiFilterQuery = clientiFilterQuery;
    }

    public ClientiPage getClientiPreview(ClientiSearchRequest request) {
        ClientiPreviewQuery.ClientePreviewPage page =
                clientiPreviewQuery.findPage(
                        request.offset(),
                        request.pageSize(),
                        request.searchText(),
                        request.operatoreId(),
                        request.tipoCliente(),
                        request.statoTrattativa(),
                        request.sortColumn().sqlColumn(),
                        request.ascending());
        return new ClientiPage(
                page.records().stream().map(this::toPreviewRow).toList(),
                page.offset(),
                page.pageSize(),
                page.totalRows());
    }

    public List<OperatoreFilter> getOperatorFilters() {
        return clientiFilterQuery.findOperatoriConClienti().stream()
                .map(this::toOperatoreFilter)
                .toList();
    }

    public List<TextFilter> getTipoClienteFilters() {
        return clientiFilterQuery.findTipiCliente().stream()
                .map(value -> new TextFilter(value, value))
                .toList();
    }

    public List<TextFilter> getStatoTrattativaFilters() {
        return clientiFilterQuery.findStatiTrattativa().stream()
                .map(value -> new TextFilter(value, value))
                .toList();
    }

    private OperatoreFilter toOperatoreFilter(OperatoreClienteFilterResult result) {
        return new OperatoreFilter(
                result.id(), operatorLabel(result.nome(), result.cognome(), result.username()));
    }

    private String operatorLabel(String nome, String cognome, String username) {
        String fullName = String.join(" ", valueOrEmpty(nome), valueOrEmpty(cognome)).trim();
        String cleanUsername = valueOrEmpty(username);
        if (fullName.isBlank()) {
            return cleanUsername;
        }
        return cleanUsername.isBlank() ? fullName : fullName + " (" + cleanUsername + ")";
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private ClientePreviewRow toPreviewRow(ClientiPreviewQuery.ClientePreviewRecord record) {
        return new ClientePreviewRow(
                record.clienteId(),
                new ClientePreview(
                        record.ragioneSociale(),
                        record.tipoCliente(),
                        record.referente(),
                        record.indirizzo(),
                        record.operatore(),
                        record.statoTrattativa(),
                        record.ultimoContatto() == null
                                ? "—"
                                : DATE_FORMATTER.format(record.ultimoContatto())));
    }
}

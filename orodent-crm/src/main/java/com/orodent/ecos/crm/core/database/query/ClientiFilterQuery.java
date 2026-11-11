package com.orodent.ecos.crm.core.database.query;

import com.orodent.ecos.crm.core.database.query.result.OperatoreClienteFilterResult;

import java.util.List;

public interface ClientiFilterQuery {
    List<OperatoreClienteFilterResult> findOperatoriConClienti();

    List<String> findTipiCliente();

    List<String> findStatiTrattativa();
}

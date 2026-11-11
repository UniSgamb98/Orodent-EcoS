package com.orodent.ecos.crm.core.database.query;

import java.util.List;
import java.util.UUID;

public interface TipoClienteQuery {
    List<TipoClienteRecord> findAll();

    record TipoClienteRecord(UUID id, String nome, int ordine) {}
}

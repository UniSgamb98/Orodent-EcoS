package com.orodent.ecos.crm.core.database.query;

import java.util.List;
import java.util.UUID;

public interface StatoTrattativaQuery {
    List<StatoTrattativaRecord> findAll();

    record StatoTrattativaRecord(UUID id, String nome, int ordine) {}
}

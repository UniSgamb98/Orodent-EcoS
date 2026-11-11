package com.orodent.ecos.crm.core.database.repository;

import java.util.List;
import java.util.UUID;

public interface ClientePreferitoRepository {
    void add(UUID operatoreId, UUID clienteId);

    void remove(UUID operatoreId, UUID clienteId);

    boolean exists(UUID operatoreId, UUID clienteId);

    List<UUID> findClienteIdsByOperatoreId(UUID operatoreId);
}

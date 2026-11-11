package com.orodent.ecos.crm.core.database.repository;

import com.orodent.ecos.crm.core.database.model.SitoWebCliente;

import java.util.List;
import java.util.UUID;

public interface SitoWebClienteRepository {
    void insertAll(List<SitoWebCliente> sitiWeb);

    void insert(SitoWebCliente sitoWeb);

    void update(SitoWebCliente sitoWeb);

    void deleteById(UUID id);

    List<SitoWebCliente> findByClienteId(UUID clienteId);
}

package com.orodent.ecos.crm.core.database.repository;

import com.orodent.ecos.crm.core.database.model.NotaCliente;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotaClienteRepository {
    void insert(NotaCliente nota);

    void update(NotaCliente nota);

    Optional<NotaCliente> findById(UUID id);

    List<NotaCliente> findByClienteId(UUID clienteId);

    void deleteById(UUID id);
}

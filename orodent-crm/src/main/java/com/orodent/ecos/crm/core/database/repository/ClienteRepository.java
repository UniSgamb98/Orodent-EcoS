package com.orodent.ecos.crm.core.database.repository;

import com.orodent.ecos.crm.core.database.model.Cliente;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository {
    void insert(Cliente cliente);

    void update(Cliente cliente);

    void updateStatoTrattativa(UUID clienteId, String statoTrattativa, LocalDateTime updatedAt);

    Optional<Cliente> findById(UUID id);

    List<Cliente> findAll();
}

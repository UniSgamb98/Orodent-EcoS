package com.orodent.ecos.crm.core.database.repository;

import com.orodent.ecos.crm.core.database.model.ContattoCliente;

import java.util.List;
import java.util.UUID;

public interface ContattoClienteRepository {
    void insertAll(List<ContattoCliente> contatti);

    void insert(ContattoCliente contatto);

    void update(ContattoCliente contatto);

    void deleteById(UUID id);

    List<ContattoCliente> findByClienteId(UUID clienteId);
}

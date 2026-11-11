package com.orodent.ecos.crm.core.database.repository;

import com.orodent.ecos.crm.core.database.model.IndirizzoCliente;

import java.util.List;
import java.util.UUID;

public interface IndirizzoClienteRepository {
    void insertAll(List<IndirizzoCliente> indirizzi);

    void insert(IndirizzoCliente indirizzo);

    void update(IndirizzoCliente indirizzo);

    void deleteById(UUID id);

    List<IndirizzoCliente> findByClienteId(UUID clienteId);
}

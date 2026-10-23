package com.orodent.ecos.database.repository;

import com.orodent.ecos.database.model.Cliente;

import java.util.List;
import java.util.Optional;

public interface ClienteRepository {
    int deleteAll();
    int insertAll(List<Cliente> clienti);
    Optional<Cliente> findByCode(String codiceCliente);
    List<Cliente> findAll();
}

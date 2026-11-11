package com.orodent.ecos.crm.core.database.repository;

import com.orodent.ecos.crm.core.database.model.Interazione;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterazioneRepository {
    void insert(Interazione interazione);

    void update(Interazione interazione);

    Optional<Interazione> findById(UUID id);

    List<Interazione> findByClienteId(UUID clienteId);

    void deleteById(UUID id);
}

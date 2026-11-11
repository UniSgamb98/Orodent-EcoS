package com.orodent.ecos.crm.core.database.service;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.model.VersionedViewPayload;
import com.orodent.ecos.crm.core.database.model.VistaSalvata;
import com.orodent.ecos.crm.core.database.repository.VistaSalvataRepository;
import com.orodent.ecos.crm.core.database.repository.derby.DerbyVistaSalvataRepository;
import com.orodent.ecos.crm.core.session.FeatureKey;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class VistaSalvataService {

    private static final int MAX_NAME_LENGTH = 120;

    private final VistaSalvataRepository repository;
    private final CurrentOperatoreService currentOperatoreService;

    public VistaSalvataService(Database database, CurrentOperatoreService currentOperatoreService) {
        this(new DerbyVistaSalvataRepository(database), currentOperatoreService);
    }

    public VistaSalvataService(
            VistaSalvataRepository repository, CurrentOperatoreService currentOperatoreService) {
        this.repository = repository;
        this.currentOperatoreService = currentOperatoreService;
    }

    public List<VistaSalvata> findAll(FeatureKey feature) {
        return repository.findAll(currentOperatoreId(), requireFeature(feature));
    }

    public Optional<VistaSalvata> findById(UUID id) {
        return repository.findById(requireId(id), currentOperatoreId());
    }

    public Optional<VistaSalvata> findPredefinita(FeatureKey feature) {
        return repository.findPredefinita(currentOperatoreId(), requireFeature(feature));
    }

    public VistaSalvata create(
            FeatureKey feature, String nome, VersionedViewPayload payload, boolean predefinita) {
        LocalDateTime now = LocalDateTime.now();
        VistaSalvata vista =
                new VistaSalvata(
                        UUID.randomUUID(),
                        currentOperatoreId(),
                        requireFeature(feature),
                        normalizeName(nome),
                        requirePayload(payload),
                        predefinita,
                        now,
                        null);
        repository.insert(vista);
        return vista;
    }

    public VistaSalvata update(
            UUID id, String nome, VersionedViewPayload payload, boolean predefinita) {
        VistaSalvata existing = findOwned(id);
        VistaSalvata updated =
                new VistaSalvata(
                        existing.id(),
                        existing.operatoreId(),
                        existing.feature(),
                        normalizeName(nome),
                        requirePayload(payload),
                        predefinita,
                        existing.createdAt(),
                        LocalDateTime.now());
        if (!repository.update(updated)) {
            throw new IllegalArgumentException("Vista salvata non trovata.");
        }
        return updated;
    }

    public void delete(UUID id) {
        if (!repository.delete(requireId(id), currentOperatoreId())) {
            throw new IllegalArgumentException("Vista salvata non trovata.");
        }
    }

    public void setPredefinita(UUID id) {
        VistaSalvata vista = findOwned(id);
        if (!repository.setPredefinita(vista.id(), vista.operatoreId(), vista.feature())) {
            throw new IllegalArgumentException("Vista salvata non trovata.");
        }
    }

    private VistaSalvata findOwned(UUID id) {
        return repository
                .findById(requireId(id), currentOperatoreId())
                .orElseThrow(() -> new IllegalArgumentException("Vista salvata non trovata."));
    }

    private UUID currentOperatoreId() {
        return currentOperatoreService.currentOperatoreId();
    }

    private UUID requireId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Identificativo vista obbligatorio.");
        }
        return id;
    }

    private FeatureKey requireFeature(FeatureKey feature) {
        if (feature == null) {
            throw new IllegalArgumentException("Feature della vista obbligatoria.");
        }
        return feature;
    }

    private VersionedViewPayload requirePayload(VersionedViewPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Configurazione della vista obbligatoria.");
        }
        return payload;
    }

    private String normalizeName(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome della vista obbligatorio.");
        }
        String normalized = nome.trim();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Il nome della vista non può superare 120 caratteri.");
        }
        return normalized;
    }
}

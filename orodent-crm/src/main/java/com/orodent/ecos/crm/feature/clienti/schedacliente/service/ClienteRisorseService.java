package com.orodent.ecos.crm.feature.clienti.schedacliente.service;

import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ClienteProfile;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.repository.ClienteRisorseRepository;

import java.util.List;
import java.util.UUID;

/** Application service that coordinates cliente resource catalogs and edit/save workflows. */
final class ClienteRisorseService {

    private final ClienteRisorseRepository repository;

    ClienteRisorseService(ClienteRisorseRepository repository) {
        this.repository = repository;
    }

    List<FornoCatalogItem> getForniCatalog() {
        return repository == null ? List.of() : repository.findForniCatalog();
    }

    List<FresatoreCatalogItem> getFresatoriCatalog() {
        return repository == null ? List.of() : repository.findFresatoriCatalog();
    }

    List<MaterialeCatalogItem> getMaterialiCatalog() {
        return repository == null ? List.of() : repository.findMaterialiCatalog();
    }

    List<FornoClienteItem> findClienteForni(UUID clienteId) {
        return repository == null ? List.of() : repository.findClienteForni(clienteId);
    }

    List<FresatoreClienteItem> findClienteFresatori(UUID clienteId) {
        return repository == null ? List.of() : repository.findClienteFresatori(clienteId);
    }

    List<MaterialeClienteItem> findClienteMateriali(UUID clienteId) {
        return repository == null ? List.of() : repository.findClienteMateriali(clienteId);
    }

    List<FornoClienteEditInput> startForniEdit(ClienteProfile profile) {
        return profile.forni().stream().map(FornoClienteEditInput::from).toList();
    }

    List<FornoClienteItem> cancelForniEdit(ClienteProfile profile) {
        return profile.forni();
    }

    List<FornoClienteItem> saveForni(UUID clienteId, List<FornoClienteEditInput> forni) {
        if (repository == null) {
            return List.of();
        }
        repository.saveClienteForni(clienteId, forni == null ? List.of() : forni);
        return repository.findClienteForni(clienteId);
    }

    List<FresatoreClienteEditInput> startFresatoriEdit(ClienteProfile profile) {
        return profile.fresatori().stream().map(FresatoreClienteEditInput::from).toList();
    }

    List<FresatoreClienteItem> cancelFresatoriEdit(ClienteProfile profile) {
        return profile.fresatori();
    }

    List<FresatoreClienteItem> saveFresatori(
            UUID clienteId, List<FresatoreClienteEditInput> fresatori) {
        if (repository == null) {
            return List.of();
        }
        repository.saveClienteFresatori(clienteId, fresatori == null ? List.of() : fresatori);
        return repository.findClienteFresatori(clienteId);
    }

    List<MaterialeClienteEditInput> startMaterialiEdit(ClienteProfile profile) {
        return profile.materiali().stream().map(MaterialeClienteEditInput::from).toList();
    }

    List<MaterialeClienteItem> cancelMaterialiEdit(ClienteProfile profile) {
        return profile.materiali();
    }

    List<MaterialeClienteItem> saveMateriali(
            UUID clienteId, List<MaterialeClienteEditInput> materiali) {
        if (repository == null) {
            return List.of();
        }
        repository.saveClienteMateriali(clienteId, materiali == null ? List.of() : materiali);
        return repository.findClienteMateriali(clienteId);
    }
}
